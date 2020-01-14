package db.impl.access

import scala.language.higherKinds

import db.impl.query.SharedQueries
import ore.OreConfig
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{PageTable, ProjectTable, VersionTable}
import ore.db.{Model, ModelService}
import ore.models.Job
import ore.models.project._
import ore.models.project.io.ProjectFiles
import ore.util.StringUtils._
import ore.util.{FileUtils, OreMDC}
import util.syntax._
import util.{FileIO, TaskUtils}

import cats.Parallel
import cats.effect.syntax.all._
import cats.instances.option._
import cats.instances.vector._
import cats.syntax.all._
import cats.tagless.autoFunctorK
import com.google.common.base.Preconditions._
import com.typesafe.scalalogging.LoggerTakingImplicit

@autoFunctorK
trait ProjectBase[+F[_]] {

  def missingFile: F[Seq[Model[Version]]]

  def refreshHomePage(logger: LoggerTakingImplicit[OreMDC])(implicit mdc: OreMDC): F[Unit]

  /**
    * Returns the Project with the specified owner name and name.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Project with name
    */
  def withName(owner: String, name: String): F[Option[Model[Project]]]

  /**
    * Returns the Project with the specified owner name and URL slug, if any.
    *
    * @param owner  Owner name
    * @param slug   URL slug
    * @return       Project if found, None otherwise
    */
  def withSlug(owner: String, slug: String): F[Option[Model[Project]]]

  /**
    * Returns the Project with the specified plugin ID, if any.
    *
    * @param pluginId Plugin ID
    * @return         Project if found, None otherwise
    */
  def withPluginId(pluginId: String): F[Option[Model[Project]]]

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
    * Renames the specified [[Project]].
    *
    * @param project  Project to rename
    * @param name     New name to assign Project
    */
  def rename(project: Model[Project], name: String): F[Unit]

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
  class ProjectBaseF[F[_]](
      implicit service: ModelService[F],
      config: OreConfig,
      fileManager: ProjectFiles[F],
      fileIO: FileIO[F],
      F: cats.effect.Effect[F],
      par: Parallel[F]
  ) extends ProjectBase[F] {

    def missingFile: F[Seq[Model[Version]]] = {
      def allVersions =
        for {
          v <- TableQuery[VersionTable]
          p <- TableQuery[ProjectTable] if v.projectId === p.id
        } yield (p.ownerName, p.name, v)

      service.runDBIO(allVersions.result).flatMap { versions =>
        fileIO
          .traverseLimited(versions.toVector) {
            case t @ (ownerNamer, name, version) =>
              val res = F
                .attempt(
                  F.delay(this.fileManager.getVersionDir(ownerNamer, name, version.name))
                    .flatMap(versionDir => fileIO.notExists(versionDir.resolve(version.fileName)))
                )
                .map {
                  case Left(_)      => false //Invalid file name
                  case Right(value) => value
                }

              res.tupleLeft(t)
          }
          .map {
            _.collect {
              case ((_, _, v), true) => v
            }
          }
      }
    }

    def refreshHomePage(logger: LoggerTakingImplicit[OreMDC])(implicit mdc: OreMDC): F[Unit] =
      service
        .runDbCon(SharedQueries.refreshHomeView.run)
        .runAsync(TaskUtils.logCallback("Failed to refresh home page", logger))
        .to[F]

    def withName(owner: String, name: String): F[Option[Model[Project]]] =
      ModelView
        .now(Project)
        .find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.name.toLowerCase === name.toLowerCase)
        .value

    def withSlug(owner: String, slug: String): F[Option[Model[Project]]] =
      ModelView
        .now(Project)
        .find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.slug.toLowerCase === slug.toLowerCase)
        .value

    def withPluginId(pluginId: String): F[Option[Model[Project]]] =
      ModelView.now(Project).find(equalsIgnoreCase(_.pluginId, pluginId)).value

    def isNamespaceAvailable(owner: String, slug: String): F[Boolean] =
      withSlug(owner, slug).map(_.isEmpty)

    def exists(owner: String, name: String): F[Boolean] =
      withName(owner, name).map(_.isDefined)

    def rename(
        project: Model[Project],
        name: String
    ): F[Unit] = {
      val newName = compact(name)
      val newSlug = slugify(newName)
      checkArgument(config.isValidProjectName(name), "invalid name", "")
      for {
        isAvailable <- this.isNamespaceAvailable(project.ownerName, newSlug)
        _ = checkArgument(isAvailable, "slug not available", "")
        _ <- {
          val fileOp      = this.fileManager.renameProject(project.ownerName, project.name, newName)
          val renameModel = service.update(project)(_.copy(name = newName, slug = newSlug))
          val addForumJob = service.insert(Job.UpdateDiscourseProjectTopic.newJob(project.id).toJob)

          // Project's name alter's the topic title, update it
          val dbOp =
            if (project.topicId.isDefined)
              renameModel *> addForumJob.void
            else
              renameModel.void

          dbOp <* fileOp
        }
      } yield ()
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
        _ <- F
          .pure(version.visibility != Visibility.SoftDelete)
          .ifM(
            proj
              .versions(ModelView.now(Version))
              .count(_.visibility === (Visibility.Public: Visibility))
              .ensure(new IllegalStateException("only one public version"))(_ > 1)
              .void,
            F.unit
          )
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
          fileIO.executeBlocking(FileUtils.deleteDirectory(versionDir)) *> service.delete(version)
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
      val fileEff = fileIO.executeBlocking(
        FileUtils.deleteDirectory(this.fileManager.getProjectDir(project.ownerName, project.name))
      )
      val addForumJob = (id: Int) => service.insert(Job.DeleteDiscourseTopic.newJob(id).toJob).void

      val eff = project.topicId.fold(F.unit)(addForumJob)
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
