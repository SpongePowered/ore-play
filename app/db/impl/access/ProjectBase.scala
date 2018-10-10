package db.impl.access

import java.nio.file.Files
import java.nio.file.Files._
import java.sql.Timestamp
import java.util.Date

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{PageTable, ProjectTableMain, VersionTable}
import db.{ModelBase, ModelService}
import discourse.OreDiscourseApi
import models.project.{Channel, Page, Project, Version, Visibility}
import ore.project.io.ProjectFiles
import ore.{OreConfig, OreEnv}
import util.FileUtils
import util.StringUtils._

import cats.data.OptionT
import cats.instances.future._
import com.google.common.base.Preconditions._
import slick.lifted.TableQuery

class ProjectBase(implicit val service: ModelService, env: OreEnv, config: OreConfig) extends ModelBase[Project] {

  override val modelClass: Class[Project] = classOf[Project]

  val fileManager = new ProjectFiles(this.env)

  implicit val self: ProjectBase = this

  def missingFile(implicit ec: ExecutionContext): Future[Seq[Version]] = {
    def allVersions =
      for {
        v <- TableQuery[VersionTable]
        p <- TableQuery[ProjectTableMain] if v.projectId === p.id
      } yield (p.ownerName, p.name, v)

    service.doAction(allVersions.result).map { versions =>
      versions
        .filter {
          case (ownerNamer, name, version) =>
            val versionDir = this.fileManager.getVersionDir(ownerNamer, name, version.name)
            Files.notExists(versionDir.resolve(version.fileName))
        }
        .map(_._3)
    }
  }

  /**
    * Returns projects that have not beein updated in a while.
    *
    * @return Stale projects
    */
  def stale: Future[Seq[Project]] =
    this.filter(_.lastUpdated > new Timestamp(new Date().getTime - this.config.projects.get[Int]("staleAge")))

  /**
    * Returns the Project with the specified owner name and name.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Project with name
    */
  def withName(owner: String, name: String)(implicit ec: ExecutionContext): OptionT[Future, Project] =
    this.find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.name.toLowerCase === name.toLowerCase)

  /**
    * Returns the Project with the specified owner name and URL slug, if any.
    *
    * @param owner  Owner name
    * @param slug   URL slug
    * @return       Project if found, None otherwise
    */
  def withSlug(owner: String, slug: String)(implicit ec: ExecutionContext): OptionT[Future, Project] =
    this.find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.slug.toLowerCase === slug.toLowerCase)

  /**
    * Returns the Project with the specified plugin ID, if any.
    *
    * @param pluginId Plugin ID
    * @return         Project if found, None otherwise
    */
  def withPluginId(pluginId: String)(implicit ec: ExecutionContext): OptionT[Future, Project] =
    this.find(equalsIgnoreCase(_.pluginId, pluginId))

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable(owner: String, slug: String)(implicit ec: ExecutionContext): Future[Boolean] =
    withSlug(owner, slug).isEmpty

  /**
    * Returns true if the specified project exists.
    *
    * @param project  Project to check
    * @return         True if exists
    */
  def exists(project: Project)(implicit ec: ExecutionContext): Future[Boolean] =
    this.withName(project.ownerName, project.name).isDefined

  /**
    * Saves any pending icon that has been uploaded for the specified [[Project]].
    *
    * FIXME: Weird behavior
    *
    * @param project Project to save icon for
    */
  def savePendingIcon(project: Project): Unit = {
    this.fileManager.getPendingIconPath(project).foreach { iconPath =>
      val iconDir = this.fileManager.getIconDir(project.ownerName, project.name)
      if (notExists(iconDir))
        createDirectories(iconDir)
      FileUtils.cleanDirectory(iconDir)
      move(iconPath, iconDir.resolve(iconPath.getFileName))
    }
  }

  /**
    * Renames the specified [[Project]].
    *
    * @param project  Project to rename
    * @param name     New name to assign Project
    */
  def rename(
      project: Project,
      name: String
  )(implicit ec: ExecutionContext, forums: OreDiscourseApi): Future[Boolean] = {
    val newName = compact(name)
    val newSlug = slugify(newName)
    checkArgument(this.config.isValidProjectName(name), "invalid name", "")
    for {
      isAvailable <- this.isNamespaceAvailable(project.ownerName, newSlug)
      _ = checkArgument(isAvailable, "slug not available", "")
      res <- {
        this.fileManager.renameProject(project.ownerName, project.name, newName)
        service.update(project.copy(name = newName, slug = newSlug))

        // Project's name alter's the topic title, update it
        if (project.topicId.isDefined && forums.isEnabled)
          forums.updateProjectTopic(project)
        else
          Future.successful(false)
      }
    } yield res
  }

  /**
    * Irreversibly deletes this channel and all version associated with it.
    */
  def deleteChannel(project: Project, channel: Channel)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      channels         <- project.channels.all
      noVersion        <- channel.versions.isEmpty
      nonEmptyChannels <- Future.traverse(channels.toSeq)(_.versions.nonEmpty).map(_.count(identity))
      _                = checkArgument(channels.size > 1, "only one channel", "")
      _                = checkArgument(noVersion || nonEmptyChannels > 1, "last non-empty channel", "")
      reviewedChannels = channels.filter(!_.isNonReviewed)
      _ = checkArgument(
        channel.isNonReviewed || reviewedChannels.size > 1 || !reviewedChannels.contains(channel),
        "last reviewed channel",
        ""
      )
      versions <- channel.versions.all
      _ <- Future.traverse(versions) { version =>
        val otherChannels = channels.filter(_ != channel)
        val newChannel =
          if (channel.isNonReviewed) otherChannels.find(_.isNonReviewed).getOrElse(otherChannels.head)
          else otherChannels.head
        service.update(version.copy(channelId = newChannel.id.value))
      }
      _ <- channel.remove()
    } yield ()
  }

  def prepareDeleteVersion(version: Version)(implicit ec: ExecutionContext): Future[Project] =
    for {
      proj <- version.project
      size <- proj.versions.count(_.visibility === (Visibility.Public: Visibility))
      _ = checkArgument(size > 1, "only one public version", "")
      rv       <- proj.recommendedVersion
      projects <- proj.versions.sorted(_.createdAt.desc) // TODO optimize: only query one version
      res <- {
        if (version == rv)
          service.update(
            proj.copy(recommendedVersionId = Some(projects.filter(v => v != version && !v.isDeleted).head.id.value))
          )
        else Future.successful(proj)
      }
    } yield res

  /**
    * Irreversibly deletes this version.
    */
  def deleteVersion(version: Version)(implicit ec: ExecutionContext): Future[Project] = {
    for {
      proj       <- prepareDeleteVersion(version)
      channel    <- version.channel
      noVersions <- channel.versions.isEmpty
      _ <- {
        val versionDir = this.fileManager.getVersionDir(proj.ownerName, proj.name, version.name)
        FileUtils.deleteDirectory(versionDir)
        version.remove()
      }
      // Delete channel if now empty
      _ <- if (noVersions) this.deleteChannel(proj, channel) else Future.unit
    } yield proj
  }

  /**
    * Irreversibly deletes this project.
    *
    * @param project Project to delete
    */
  def delete(project: Project)(implicit ec: ExecutionContext, forums: OreDiscourseApi): Future[Int] = {
    FileUtils.deleteDirectory(this.fileManager.getProjectDir(project.ownerName, project.name))
    if (project.topicId.isDefined)
      forums.deleteProjectTopic(project)
    // TODO: Instead, move to the "projects_deleted" table just in case we couldn't delete the topic
    project.remove()
  }

  def queryProjectPages(project: Project)(implicit ec: ExecutionContext): Future[Seq[(Page, Seq[Page])]] = {
    val tablePage = TableQuery[PageTable]
    val pagesQuery = for {
      (pp, p) <- tablePage.joinLeft(tablePage).on(_.id === _.parentId)
      if pp.projectId === project.id.value && pp.parentId.isEmpty
    } yield (pp, p)

    service.doAction(pagesQuery.result).map(_.groupBy(_._1)).map { grouped => // group by parent page
      // Sort by key then lists too
      grouped.toSeq.sortBy(_._1.name).map {
        case (pp, p) =>
          (pp, p.flatMap(_._2).sortBy(_.name))
      }
    }
  }

}
object ProjectBase {
  def apply()(implicit projectBase: ProjectBase): ProjectBase = projectBase

  implicit def fromService(implicit service: ModelService): ProjectBase = service.getModelBase(classOf[ProjectBase])
}
