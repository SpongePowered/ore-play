package db.impl.access

import scala.language.higherKinds

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Files._
import java.time.Instant

import db.impl.query.SharedQueries
import discourse.OreDiscourseApi
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{PageTable, ProjectTableMain, VersionTable}
import ore.db.{Model, ModelService}
import ore.models.project._
import ore.models.project.io.ProjectFiles
import ore.util.{FileUtils, OreMDC}
import ore.{OreConfig, OreEnv}
import ore.util.StringUtils._
import util.syntax._
import util.IOUtils

import cats.Parallel
import cats.data.OptionT
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.instances.vector._
import cats.instances.option._
import com.google.common.base.Preconditions._
import com.typesafe.scalalogging.LoggerTakingImplicit

trait ProjectBase[F[_]] {

  def missingFile: F[Seq[Model[Version]]]

  def refreshHomePage(logger: LoggerTakingImplicit[OreMDC])(implicit mdc: OreMDC): F[Unit]

  /**
    * Returns projects that have not beein updated in a while.
    *
    * @return Stale projects
    */
  def stale: F[Seq[Model[Project]]]

  /**
    * Returns the Project with the specified owner name and name.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Project with name
    */
  def withName(owner: String, name: String): OptionT[F, Model[Project]]

  /**
    * Returns the Project with the specified owner name and URL slug, if any.
    *
    * @param owner  Owner name
    * @param slug   URL slug
    * @return       Project if found, None otherwise
    */
  def withSlug(owner: String, slug: String): OptionT[F, Model[Project]]

  /**
    * Returns the Project with the specified plugin ID, if any.
    *
    * @param pluginId Plugin ID
    * @return         Project if found, None otherwise
    */
  def withPluginId(pluginId: String): OptionT[F, Model[Project]]

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable(owner: String, slug: String): F[Boolean]

  /**
    * Returns true if the specified project exists.
    *
    * @return True if exists
    */
  def exists(owner: String, name: String): F[Boolean]

  /**
    * Saves any pending icon that has been uploaded for the specified [[Project]].
    *
    * FIXME: Weird behavior
    *
    * @param project Project to save icon for
    */
  def savePendingIcon(project: Project)(implicit mdc: OreMDC): F[Unit]

  /**
    * Renames the specified [[Project]].
    *
    * @param project  Project to rename
    * @param name     New name to assign Project
    */
  def rename(project: Model[Project], name: String): F[Boolean]

  /**
    * Irreversibly deletes this channel and all version associated with it.
    */
  def deleteChannel(project: Model[Project], channel: Model[Channel]): F[Unit]

  def prepareDeleteVersion(version: Model[Version]): F[Model[Project]]

  /**
    * Irreversibly deletes this version.
    */
  def deleteVersion(version: Model[Version])(implicit mdc: OreMDC): F[Model[Project]]

  /**
    * Irreversibly deletes this project.
    *
    * @param project Project to delete
    */
  def delete(project: Model[Project])(implicit mdc: OreMDC): F[Int]

  def queryProjectPages(project: Model[Project]): F[Seq[(Model[Page], Seq[Model[Page]])]]
}

object ProjectBase {

  /**
    * Default live implementation of [[ProjectBase]]
    */
  class ProjectBaseF[F[_], G[_]](
      implicit service: ModelService[F],
      config: OreConfig,
      forums: OreDiscourseApi[F],
      fileManager: ProjectFiles,
      F: cats.effect.Effect[F],
      par: Parallel[F, G]
  ) extends ProjectBase[F] {

    def missingFile: F[Seq[Model[Version]]] = {
      def allVersions =
        for {
          v <- TableQuery[VersionTable]
          p <- TableQuery[ProjectTableMain] if v.projectId === p.id
        } yield (p.ownerName, p.name, v)

      service.runDBIO(allVersions.result).map { versions =>
        versions
          .filter {
            case (ownerNamer, name, version) =>
              try {
                val versionDir = this.fileManager.getVersionDir(ownerNamer, name, version.name)
                Files.notExists(versionDir.resolve(version.fileName))
              } catch {
                case _: IOException =>
                  //Invalid file name
                  false
              }
          }
          .map(_._3)
      }
    }

    def refreshHomePage(logger: LoggerTakingImplicit[OreMDC])(implicit mdc: OreMDC): F[Unit] =
      service
        .runDbCon(SharedQueries.refreshHomeView.run)
        .runAsync(IOUtils.logCallback("Failed to refresh home page", logger))
        .to[F]

    def stale: F[Seq[Model[Project]]] =
      service.runDBIO(
        ModelView
          .raw(Project)
          .filter(_.lastUpdated > Instant.now().minusMillis(config.ore.projects.staleAge.toMillis))
          .result
      )

    def withName(owner: String, name: String): OptionT[F, Model[Project]] =
      ModelView
        .now(Project)
        .find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.name.toLowerCase === name.toLowerCase)

    def withSlug(owner: String, slug: String): OptionT[F, Model[Project]] =
      ModelView
        .now(Project)
        .find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.slug.toLowerCase === slug.toLowerCase)

    def withPluginId(pluginId: String): OptionT[F, Model[Project]] =
      ModelView.now(Project).find(equalsIgnoreCase(_.pluginId, pluginId))

    def isNamespaceAvailable(owner: String, slug: String): F[Boolean] =
      withSlug(owner, slug).isEmpty

    def exists(owner: String, name: String): F[Boolean] =
      withName(owner, name).isDefined

    def savePendingIcon(project: Project)(implicit mdc: OreMDC): F[Unit] = F.delay {
      this.fileManager.getPendingIconPath(project).foreach { iconPath =>
        val iconDir = this.fileManager.getIconDir(project.ownerName, project.name)
        if (notExists(iconDir))
          createDirectories(iconDir)
        FileUtils.cleanDirectory(iconDir)
        move(iconPath, iconDir.resolve(iconPath.getFileName))
      }
    }

    def rename(
        project: Model[Project],
        name: String
    ): F[Boolean] = {
      val newName = compact(name)
      val newSlug = slugify(newName)
      checkArgument(config.isValidProjectName(name), "invalid name", "")
      for {
        isAvailable <- this.isNamespaceAvailable(project.ownerName, newSlug)
        _ = checkArgument(isAvailable, "slug not available", "")
        res <- {
          val fileOp      = this.fileManager.renameProject(project.ownerName, project.name, newName)
          val renameModel = service.update(project)(_.copy(name = newName, slug = newSlug))

          // Project's name alter's the topic title, update it
          val dbOp =
            if (project.topicId.isDefined)
              forums.updateProjectTopic(project) <* renameModel
            else
              renameModel.as(false)

          dbOp <* fileOp
        }
      } yield res
    }

    def deleteChannel(project: Model[Project], channel: Model[Channel]): F[Unit] = {
      for {
        channels  <- service.runDBIO(project.channels(ModelView.raw(Channel)).result)
        noVersion <- channel.versions(ModelView.now(Version)).isEmpty
        nonEmptyChannels <- channels.toVector
          .parTraverse(_.versions(ModelView.now(Version)).nonEmpty)
          .map(_.count(identity))
        _                = checkArgument(channels.size > 1, "only one channel", "")
        _                = checkArgument(noVersion || nonEmptyChannels > 1, "last non-empty channel", "")
        reviewedChannels = channels.filter(!_.isNonReviewed)
        _ = checkArgument(
          channel.isNonReviewed || reviewedChannels.size > 1 || !reviewedChannels.contains(channel),
          "last reviewed channel",
          ""
        )
        versions <- service.runDBIO(channel.versions(ModelView.raw(Version)).result)
        _ <- versions.toVector.parTraverse { version =>
          val otherChannels = channels.filter(_ != channel)
          val newChannel =
            if (channel.isNonReviewed) otherChannels.find(_.isNonReviewed).getOrElse(otherChannels.head)
            else otherChannels.head
          service.update(version)(_.copy(channelId = newChannel.id))
        }
        _ <- service.delete(channel)
      } yield ()
    }

    def prepareDeleteVersion(version: Model[Version]): F[Model[Project]] = {
      for {
        proj <- version.project
        size <- proj.versions(ModelView.now(Version)).count(_.visibility === (Visibility.Public: Visibility))
        _ = checkArgument(size > 1, "only one public version", "")
        rv       <- proj.recommendedVersion(ModelView.now(Version)).sequence.subflatMap(identity).value
        projects <- service.runDBIO(proj.versions(ModelView.raw(Version)).sortBy(_.createdAt.desc).result) // TODO optimize: only query one version
        res <- {
          if (rv.contains(version))
            service.update(proj)(
              _.copy(recommendedVersionId = Some(projects.filter(v => v != version && !v.obj.isDeleted).head.id))
            )
          else F.pure(proj)
        }
      } yield res
    }

    /**
      * Irreversibly deletes this version.
      */
    def deleteVersion(version: Model[Version])(implicit mdc: OreMDC): F[Model[Project]] = {
      for {
        proj       <- prepareDeleteVersion(version)
        channel    <- version.channel
        noVersions <- channel.versions(ModelView.now(Version)).isEmpty
        _ <- {
          val versionDir = this.fileManager.getVersionDir(proj.ownerName, proj.name, version.name)
          FileUtils.deleteDirectory(versionDir)
          service.delete(version)
        }
        // Delete channel if now empty
        _ <- if (noVersions) this.deleteChannel(proj, channel) else F.unit
      } yield proj
    }

    /**
      * Irreversibly deletes this project.
      *
      * @param project Project to delete
      */
    def delete(project: Model[Project])(implicit mdc: OreMDC): F[Int] = {
      val fileEff = F.delay(FileUtils.deleteDirectory(this.fileManager.getProjectDir(project.ownerName, project.name)))
      val eff =
        if (project.topicId.isDefined)
          forums.deleteProjectTopic(project).void
        else F.unit
      // TODO: Instead, move to the "projects_deleted" table just in case we couldn't delete the topic
      eff *> service.delete(project) <* fileEff
    }

    def queryProjectPages(project: Model[Project]): F[Seq[(Model[Page], Seq[Model[Page]])]] = {
      val tablePage = TableQuery[PageTable]
      val pagesQuery = for {
        (pp, p) <- tablePage.joinLeft(tablePage).on(_.id === _.parentId)
        if pp.projectId === project.id.value && pp.parentId.isEmpty
      } yield (pp, p)

      service.runDBIO(pagesQuery.result).map(_.groupBy(_._1)).map { grouped => // group by parent page
        // Sort by key then lists too
        grouped.toSeq.sortBy(_._1.name).map {
          case (pp, p) =>
            (pp, p.flatMap(_._2).sortBy(_.name))
        }
      }
    }
  }

  def apply[F[_]](implicit projectBase: ProjectBase[F]): ProjectBase[F] = projectBase
}
