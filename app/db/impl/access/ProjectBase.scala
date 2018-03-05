package db.impl.access

import java.nio.file.Files
import java.nio.file.Files._
import java.sql.Timestamp
import java.util.Date

import com.google.common.base.Preconditions._
import db.impl.OrePostgresDriver.api._
import db.{ModelBase, ModelService}
import discourse.OreDiscourseApi
import models.project.{Channel, Project, Version}
import ore.project.io.ProjectFiles
import ore.{OreConfig, OreEnv}
import util.FileUtils
import util.StringUtils._

class ProjectBase(override val service: ModelService,
                  env: OreEnv,
                  config: OreConfig,
                  forums: OreDiscourseApi)
                  extends ModelBase[Project] {

  override val modelClass = classOf[Project]

  val fileManager = new ProjectFiles(this.env)

  implicit val self = this

  def missingFile: Seq[Version] = {
    var versions = Seq.empty[Version]
    for (version <- this.service.access[Version](classOf[Version]).all) {
      val project = version.project
      val versionDir = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
      if (Files.notExists(versionDir.resolve(version.fileName))) {
        versions :+= version
      }
    }
    versions
  }

  /**
    * Returns projects that have not beein updated in a while.
    *
    * @return Stale projects
    */
  def stale: Seq[Project]
  = this.filter(_.lastUpdated > new Timestamp(new Date().getTime - this.config.projects.getInt("staleAge").get))

  /**
    * Returns the Project with the specified owner name and name.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Project with name
    */
  def withName(owner: String, name: String): Option[Project]
  = this.find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.name.toLowerCase === name.toLowerCase)

  /**
    * Returns the Project with the specified owner name and URL slug, if any.
    *
    * @param owner  Owner name
    * @param slug   URL slug
    * @return       Project if found, None otherwise
    */
  def withSlug(owner: String, slug: String): Option[Project]
  = this.find(p => p.ownerName.toLowerCase === owner.toLowerCase && p.slug.toLowerCase === slug.toLowerCase)

  /**
    * Returns the Project with the specified plugin ID, if any.
    *
    * @param pluginId Plugin ID
    * @return         Project if found, None otherwise
    */
  def withPluginId(pluginId: String): Option[Project]
  = this.find(equalsIgnoreCase(_.pluginId, pluginId))

  /**
    * Returns true if the Project's desired slug is available.
    *
    * @return True if slug is available
    */
  def isNamespaceAvailable(owner: String, slug: String): Boolean = withSlug(owner, slug).isEmpty

  /**
    * Returns true if the specified project exists.
    *
    * @param project  Project to check
    * @return         True if exists
    */
  def exists(project: Project): Boolean = this.withName(project.ownerName, project.name).isDefined

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
  def rename(project: Project, name: String) = {
    val newName = compact(name)
    val newSlug = slugify(newName)
    checkArgument(this.config.isValidProjectName(name), "invalid name", "")
    checkArgument(this.isNamespaceAvailable(project.ownerName, newSlug), "slug not available", "")

    this.fileManager.renameProject(project.ownerName, project.name, newName)
    project.name = newName
    project.slug = newSlug

    // Project's name alter's the topic title, update it
    if (project.topicId != -1 && this.forums.isEnabled)
      this.forums.updateProjectTopic(project)
  }

  /**
    * Irreversibly deletes this channel and all version associated with it.
    *
    * @param context Project context
    */
  def deleteChannel(channel: Channel)(implicit context: Project = null) = {
    val project = if (context != null) context else channel.project
    checkArgument(project.id.get == channel.projectId, "invalid project id", "")

    val channels = project.channels.all
    checkArgument(channels.size > 1, "only one channel", "")
    checkArgument(channel.versions.isEmpty ||
      channels.count(c => c.versions.nonEmpty) > 1, "last non-empty channel", "")

    val reviewedChannels = channels.filter(!_.isNonReviewed)
    checkArgument(channel.isNonReviewed || reviewedChannels.size > 1 || !reviewedChannels.contains(channel),
      "last reviewed channel", "")

    channel.remove()

    channel.versions.all.foreach { version: Version =>
      val versionFolder = this.fileManager.getVersionDir(project.ownerName, project.name, version.name)
      FileUtils.deleteDirectory(versionFolder)
      version.remove()
    }
  }

  /**
    * Irreversibly deletes this version.
    *
    * @param project Project context
    */
  def deleteVersion(version: Version)(implicit project: Project = null) = {
    val proj = if (project != null) project else version.project
    checkArgument(proj.versions.size > 1, "only one version", "")
    checkArgument(proj.id.get == version.projectId, "invalid context id", "")

    val rv = proj.recommendedVersion

    // Set recommended version to latest (excluding the version to delete)
    // version if the deleted version was the rv
    if (version.equals(rv))
      proj.recommendedVersion = proj.versions.sorted(_.createdAt.desc).filterNot(_.equals(version)).head

    version.remove()

    // Delete channel if now empty
    val channel: Channel = version.channel
    if (channel.versions.isEmpty)
      this.deleteChannel(channel)

    val versionDir = this.fileManager.getVersionDir(proj.ownerName, project.name, version.name)
    FileUtils.deleteDirectory(versionDir)
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

}
