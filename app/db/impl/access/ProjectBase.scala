package db.impl.access

import java.nio.file.Files
import java.nio.file.Files._
import java.sql.Timestamp
import java.util.Date

import com.google.common.base.Preconditions._
import db.impl.OrePostgresDriver.api._
import db.impl.{PageTable, ProjectTable, ProjectTableMain, VersionTable}
import db.{ModelBase, ModelService}
import discourse.OreDiscourseApi
import models.project.{Channel, Project, Version}
import ore.project.io.ProjectFiles
import ore.{OreConfig, OreEnv}
import slick.lifted.TableQuery
import util.FileUtils
import util.StringUtils._

import scala.concurrent.{ExecutionContext, Future}

class ProjectBase(override val service: ModelService,
                  env: OreEnv,
                  config: OreConfig,
                  forums: OreDiscourseApi)
                  extends ModelBase[Project] {

  override val modelClass = classOf[Project]

  val fileManager = new ProjectFiles(this.env)

  implicit val self = this

  def missingFile(implicit ec: ExecutionContext): Future[Seq[Version]] = {
    val tableVersion = TableQuery[VersionTable]
    val tableProject = TableQuery[ProjectTableMain]

    def allVersions = for {
      v <- tableVersion
      p <- tableProject if v.projectId === p.id
    } yield {
      (p.ownerName, p.name, v)
    }

    service.DB.db.run(allVersions.result).map { versions =>
      versions.filter { case (ownerNamer, name, version) =>
        val versionDir = this.fileManager.getVersionDir(ownerNamer,name,version.name)
        Files.notExists(versionDir.resolve(version.fileName))
      }.map(_._3)
    }
  }

  /**
    * Returns projects that have not beein updated in a while.
    *
    * @return Stale projects
    */
  def stale: Future[Seq[Project]]
  = this.filter(_.lastUpdated > new Timestamp(new Date().getTime - this.config.projects.get[Int]("staleAge")))

  /**
    * Returns the Project with the specified owner name and name.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Project with name
    */
  def withName(owner: String, name: String): Future[Option[Project]]
  = this.find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.name.toLowerCase === name.toLowerCase)

  /**
    * Returns the Project with the specified owner name and URL slug, if any.
    *
    * @param owner  Owner name
    * @param slug   URL slug
    * @return       Project if found, None otherwise
    */
  def withSlug(owner: String, slug: String): Future[Option[Project]]
  = this.find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.slug.toLowerCase === slug.toLowerCase)

  /**
    * Returns the Project with the specified plugin ID, if any.
    *
    * @param pluginId Plugin ID
    * @return         Project if found, None otherwise
    */
  def withPluginId(pluginId: String): Future[Option[Project]] = this.find(equalsIgnoreCase(_.pluginId, pluginId))

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable(owner: String, slug: String)(implicit ec: ExecutionContext): Future[Boolean] = withSlug(owner, slug).map(_.isEmpty)

  /**
    * Returns true if the specified project exists.
    *
    * @param project  Project to check
    * @return         True if exists
    */
  def exists(project: Project)(implicit ec: ExecutionContext): Future[Boolean] = this.withName(project.ownerName, project.name).map(_.isDefined)

  /**
    * Saves any pending icon that has been uploaded for the specified [[Project]].
    *
    * FIXME: Weird behavior
    *
    * @param project Project to save icon for
    */
  def savePendingIcon(project: Project) = {
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
  def rename(project: Project, name: String)(implicit ec: ExecutionContext)  = {
    val newName = compact(name)
    val newSlug = slugify(newName)
    checkArgument(this.config.isValidProjectName(name), "invalid name", "")
    val future = for {
      isAvailable <- this.isNamespaceAvailable(project.ownerName, newSlug)
    } yield {
      checkArgument(isAvailable, "slug not available", "")
    }
    future.flatMap { _ =>
      this.fileManager.renameProject(project.ownerName, project.name, newName)
      project.setName(newName)
      project.setSlug(newSlug)

      // Project's name alter's the topic title, update it
      if (project.topicId != -1 && this.forums.isEnabled)
        this.forums.updateProjectTopic(project)
      else
        Future.successful(false)
    }
  }

  /**
    * Irreversibly deletes this channel and all version associated with it.
    *
    * @param context Project context
    */
  def deleteChannel(channel: Channel)(implicit context: Project = null, ec: ExecutionContext): Future[Unit] = {
    val project = if (context != null) Future.successful(context) else channel.project
    project.map { project =>
      checkArgument(project.id.get == channel.projectId, "invalid project id", "")
      val checks = for {
        channels <- project.channels.all
        noVersion <- channel.versions.isEmpty
        nonEmptyChannels <- Future.sequence(channels.map(_.versions.nonEmpty)).map(_.count(_ == true))
      } yield {
        checkArgument(channels.size > 1, "only one channel", "")
        checkArgument(noVersion || nonEmptyChannels > 1, "last non-empty channel", "")
        val reviewedChannels = channels.filter(!_.isNonReviewed)
        checkArgument(channel.isNonReviewed || reviewedChannels.size > 1 || !reviewedChannels.contains(channel),
          "last reviewed channel", "")
      }
      for {
        _ <- checks
        _ <- channel.remove()
        versions <- channel.versions.all
      } yield {
        versions.foreach { version =>
          val versionFolder = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
          FileUtils.deleteDirectory(versionFolder)
          version.remove()
        }
      }
    }
  }

  /**
    * Irreversibly deletes this version.
    *
    * @param project Project context
    */
  def deleteVersion(version: Version)(implicit project: Project = null, ec: ExecutionContext) = {
    val checks = for {
      proj <-  if (project != null) Future.successful(project) else version.project
      size <- proj.versions.size
    } yield {
      checkArgument(size > 1, "only one version", "")
      checkArgument(proj.id.get == version.projectId, "invalid context id", "")
      proj
    }

    val rcUpdate = for {
      proj <- checks
      rv <- proj.recommendedVersion
      projects <- proj.versions.sorted(_.createdAt.desc) // TODO optimize: only query one version
    } yield {
      if (version.equals(rv)) proj.setRecommendedVersion(projects.filterNot(_.equals(version)).head)
      proj
    }

    val channelCleanup = for {
      proj <- rcUpdate
      channel <- version.channel
      noVersions <- channel.versions.isEmpty
    } yield {
      // Delete channel if now empty
      if (noVersions) this.deleteChannel(channel)
      proj
    }

    channelCleanup.flatMap { proj =>
      val versionDir = this.fileManager.getVersionDir(proj.ownerName, project.name, version.name)
      FileUtils.deleteDirectory(versionDir)
      version.remove()
    }
  }

  /**
    * Irreversibly deletes this project.
    *
    * @param project Project to delete
    */
  def delete(project: Project) = {
    FileUtils.deleteDirectory(this.fileManager.getProjectDir(project.ownerName, project.name))
    if (project.topicId != -1)
      this.forums.deleteProjectTopic(project)
    // TODO: Instead, move to the "projects_deleted" table just in case we couldn't delete the topic
    project.remove()
  }


  def queryProjectPages(project: Project)(implicit ec: ExecutionContext) = {
    val tablePage = TableQuery[PageTable]
    val pagesQuery = for {
      (pp, p) <- tablePage joinLeft tablePage on (_.id === _.parentId)
    } yield {
      (pp, p)
    }
    val filtered = pagesQuery filter { case (pp, p) =>
      pp.projectId === project.id
    }

    service.DB.db.run(filtered.result)
      .map(_.groupBy(_._1).mapValues(_.flatMap(_._2))) // group by parent page
  }


}
