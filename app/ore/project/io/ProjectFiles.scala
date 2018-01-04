package ore.project.io

import java.io.IOException
import java.nio.file.Files._
import java.nio.file.Path

import models.project.Project
import ore.OreEnv
import play.api.Logger

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Handles file management of Projects.
  */
class ProjectFiles(val env: OreEnv) {

  /**
    * Returns the specified project's plugin directory.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Plugin directory
    */
  def getProjectDir(owner: String, name: String): Path = getUserDir(owner).resolve(name)

  /**
    * Returns the specified plugin version directory for a project.
    *
    * @param owner    Owner name
    * @param name     Project name
    * @param version  Version string
    * @return         Version directory
    */
  def getProjectVersionDir(owner: String, name: String, version: String): Path = this.getProjectDir(owner, name).resolve(version)

  /**
    * Returns the specified user's plugin directory.
    *
    * @param user User name
    * @return     Plugin directory
    */
  def getUserDir(user: String): Path = this.env.plugins.resolve(user)

  /**
    * Renames this specified project in the file system.
    *
    * @param owner    Owner name
    * @param oldName  Old project name
    * @param newName  New project name
    * @return         New path
    */
  def renameProject(owner: String, oldName: String, newName: String): Try[Unit] = Try { //TODO
    val newProjectDir = getProjectDir(owner, newName)
    move(getProjectDir(owner, oldName), newProjectDir)
    // Rename plugin files
    for (channelDir <- newProjectDir.toFile.listFiles()) {
      if (channelDir.isDirectory) {
        for (pluginFile <- channelDir.listFiles()) {
          move(pluginFile.toPath, getProjectDir(owner, newName).resolve(pluginFile.getName))
        }
      }
    }
  }

  /**
    * Returns the directory that contains a [[Project]]'s custom icons.
    *
    * @param owner  Project owner
    * @param name   Project name
    * @return       Icons directory path
    */
  def getIconsDir(owner: String, name: String): Path = getProjectDir(owner, name).resolve("icons")

  /**
    * Returns the directory that contains a [[Project]]'s main icon.
    *
    * @param owner  Project owner
    * @param name   Project name
    * @return       Icon directory path
    */
  def getIconDir(owner: String, name: String): Path = getIconsDir(owner, name).resolve("icon")

  /**
    * Returns the path to a custom [[Project]] icon, if any, None otherwise.
    *
    * @param project Project to get icon for
    * @return Project icon
    */
  def getIconPath(project: Project): Option[Path] = findFirstFile(getIconDir(project.ownerName, project.name))

  /**
    * Returns the directory that contains an icon that has not yet been saved.
    *
    * @param owner  Project owner
    * @param name   Project name
    * @return       Pending icon path
    */
  def getPendingIconDir(owner: String, name: String): Path = getIconsDir(owner, name).resolve("pending")

  /**
    * Returns the directory to a custom [[Project]] icon that has not yet been
    * saved.
    *
    * @param project Project to get icon for
    * @return Pending icon path
    */
  def getPendingIconPath(project: Project): Option[Path]
  = findFirstFile(getPendingIconDir(project.ownerName, project.name))

  private def findFirstFile(dir: Path): Option[Path] = {
    if (exists(dir)) {
      var stream: java.util.stream.Stream[Path] = null
      try {
        stream = list(dir)
        stream.iterator.asScala.filterNot(isDirectory(_)).toStream.headOption
      } catch {
        case e: IOException =>
          Logger.error("an error occurred while searching a directory", e)
          None
      } finally {
        if (stream != null)
          stream.close()
      }
    } else
      None
  }

}
