package plugin

import java.nio.file.{Files, Path}

import com.google.common.base.Preconditions
import com.google.common.base.Preconditions._
import db.Storage
import models.auth.User
import models.project.Project.PendingProject
import models.project.Version.PendingVersion
import models.project.{Channel, Project, Version}
import play.api.libs.Files.TemporaryFile
import util.Dirs._

import scala.util.{Failure, Success, Try}

/**
  * Handles management of uploaded projects.
  */
object ProjectManager {

  /**
    * Initializes a new PluginFile with the specified owner and temporary file.
    *
    * @param tmp    Temporary file
    * @param owner  Project owner
    * @return       New plugin file
    */
  def initUpload(tmp: TemporaryFile, name: String, owner: User): Try[PluginFile] = Try {
    val tmpPath = TEMP_DIR.resolve(owner.username).resolve(name)
    val plugin = new PluginFile(tmpPath, owner)
    if (Files.notExists(tmpPath.getParent)) {
      Files.createDirectories(tmpPath.getParent)
    }
    tmp.moveTo(plugin.getPath.toFile, replace = true)
    plugin.loadMeta
    plugin
  }

  /**
    * Uploads the specified PluginFile to it's appropriate location.
    *
    * @param plugin   PluginFile to upload
    * @return         Result
    */
  def uploadPlugin(channel: Channel, plugin: PluginFile): Try[Unit] = Try {
    plugin.getMeta match {
      case None => throw new IllegalArgumentException("Specified PluginFile has no meta loaded.")
      case Some(meta) =>
        var oldPath = plugin.getPath
        if (!plugin.isZipped) {
          oldPath = plugin.zip
        }
        val newPath = getUploadPath(plugin.getOwner.username, meta.getName, meta.getVersion, channel.getName)
        if (!Files.exists(newPath.getParent)) {
          Files.createDirectories(newPath.getParent)
        }
        Files.move(oldPath, newPath)
        Files.delete(oldPath)
    }
  }

  /**
    * Creates a new Project from the specified PendingProject
    *
    * @param pending  PendingProject
    * @return         New Project
    * @throws         IllegalArgumentException if the project already exists
    */
  def createProject(pending: PendingProject): Try[Project] = Try[Project] {
    checkArgument(!pending.project.exists, "project already exists", "")
    checkArgument(pending.project.isNamespaceAvailable, "slug not available", "")
    checkArgument(Project.isValidName(pending.project.getName), "invalid name", "")
    Storage.now(Storage.createProject(pending.project)) match {
      case Failure(thrown) =>
        pending.cancel()
        throw thrown
      case Success(newProject) =>
        Pages.createHomePage(newProject.owner, newProject.getName)
        newProject
    }
  }

  /**
    * Creates a new version from the specified PendingVersion.
    *
    * @param pending  PendingVersion
    * @return         New version
    */
  def createVersion(pending: PendingVersion): Try[Version] = Try[Version] {
    // Get project
    Storage.now(Storage.getProjectBySlug(pending.owner, pending.projectSlug)) match {
      case Failure(thrown) =>
        pending.cancel()
        throw thrown
      case Success(project) =>
        var channel: Channel = null
        // Create channel if not exists
        Storage.now(project.getChannel(pending.getChannelName)) match {
          case Failure(thrown) =>
            pending.cancel()
            throw thrown
          case Success(channelOpt) => channelOpt match {
            case None =>
              project.newChannel(pending.getChannelName, pending.getChannelColor) match {
                case Failure(thrown) =>
                  pending.cancel()
                  throw thrown
                case Success(newChannel) => channel = newChannel
              }
            case Some(existingChannel) => channel = existingChannel
          }
        }

        // Create version
        val version = pending.version
        Storage.now(Storage.isDefined(Storage.getVersion(channel.id.get, version.versionString))) match {
          case Failure(ignored) => ;
          case Success(m) => throw new Exception("Version already exists.")
        }

        val versionResult = Storage.now(channel.newVersion(version.versionString, version.dependencies,
          version.getDescription.orNull, version.assets.orNull))
        versionResult match {
          case Failure(thrown) =>
            pending.cancel()
            throw thrown
          case Success(newVersion) =>
            // Upload plugin file
            uploadPlugin(channel, pending.plugin)
            newVersion
        }
    }
  }

  /**
    * Returns the Path to where the specified Version should be.
    *
    * @param owner    Project owner
    * @param name     Project name
    * @param version  Project version
    * @param channel  Project channel
    * @return         Path to supposed file
    */
  def getUploadPath(owner: String, name: String, version: String, channel: String): Path = {
    getProjectDir(owner, name).resolve(channel).resolve("%s-%s.zip".format(name, version.toLowerCase))
  }

  /**
    * Returns the specified project's plugin directory.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Plugin directory
    */
  def getProjectDir(owner: String, name: String): Path = {
    getUserDir(owner).resolve(name)
  }

  /**
    * Returns the specified user's plugin directory.
    *
    * @param owner  Owner name
    * @return       Plugin directory
    */
  def getUserDir(owner: String): Path = {
    PLUGIN_DIR.resolve(owner)
  }

  /**
    * Renames this specified project in the file system.
    *
    * @param owner    Owner name
    * @param oldName  Old project name
    * @param newName  New project name
    * @return         New path
    */
  def renameProject(owner: String, oldName: String, newName: String): Try[Unit] = Try {
    val newProjectDir = getProjectDir(owner, newName)
    Files.move(getProjectDir(owner, oldName), newProjectDir)
    Files.move(Pages.getDocsDir(owner, oldName), Pages.getDocsDir(owner, newName))
    for (channelDir <- newProjectDir.toFile.listFiles()) {
      if (channelDir.isDirectory) {
        val channelName = channelDir.getName
        for (pluginFile <- channelDir.listFiles()) {
          val fileName = pluginFile.getName
          val versionString = fileName.substring(fileName.indexOf('-') + 1, fileName.lastIndexOf('.'))
          Files.move(pluginFile.toPath, getUploadPath(owner, newName, versionString, channelName))
        }
      }
    }
  }

  /**
    * Renames the specified channel in the file system.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @param oldName      Old channel name
    * @param newName      New channel name
    * @return             New path
    */
  def renameChannel(owner: String, projectName: String, oldName: String, newName: String): Try[Unit] = Try {
    val newPath = getProjectDir(owner, projectName).resolve(newName)
    val oldPath = getProjectDir(owner, projectName).resolve(oldName)
    Files.move(oldPath, newPath)
  }

}
