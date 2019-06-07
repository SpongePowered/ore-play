package ore.models.project.io

import scala.language.higherKinds

import java.io.IOException
import java.nio.file.Files._
import java.nio.file.Path
import javax.inject.Inject

import scala.collection.JavaConverters._

import ore.models.project.Project
import ore.OreEnv
import ore.util.OreMDC

import cats.effect.{IO, Resource, Sync}
import cats.syntax.all._
import com.typesafe.scalalogging

/**
  * Handles file management of Projects.
  */
class ProjectFiles @Inject()(val env: OreEnv) {

  private val Logger    = scalalogging.Logger("ProjectFiles")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  /**
    * Returns the specified project's plugin directory.
    *
    * @param owner  Owner name
    * @param name   Project name
    * @return       Plugin directory
    */
  def getProjectDir(owner: String, name: String): Path = getUserDir(owner).resolve(name)

  /**
    * Returns the specified version's directory
    *
    * @param owner   Owner name
    * @param name    Project name
    * @param version Version
    * @return        Version directory
    */
  def getVersionDir(owner: String, name: String, version: String): Path =
    getProjectDir(owner, name).resolve("versions").resolve(version)

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
  def renameProject[F[_]](owner: String, oldName: String, newName: String)(implicit F: Sync[F]): F[Unit] = F.delay {
    val newProjectDir = getProjectDir(owner, newName)
    move(getProjectDir(owner, oldName), newProjectDir)
    ()
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
    * @param owner  Project owner
    * @param name   Project name
    * @return Project icon
    */
  def getIconPath(owner: String, name: String)(implicit mdc: OreMDC): Option[Path] =
    findFirstFile(getIconDir(owner, name))

  /**
    * Returns the path to a custom [[Project]] icon, if any, None otherwise.
    *
    * @param project Project to get icon for
    * @return Project icon
    */
  def getIconPath(project: Project)(implicit mdc: OreMDC): Option[Path] =
    getIconPath(project.ownerName, project.name)

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
  def getPendingIconPath(project: Project)(implicit mdc: OreMDC): Option[Path] =
    getPendingIconPath(project.ownerName, project.name)

  /**
    * Returns the directory to a custom [[Project]] icon that has not yet been
    * saved.
    *
    * @param ownerName Owner of the project to get icon for
    * @param name Name of the project to get icon for
    * @return Pending icon path
    */
  def getPendingIconPath(ownerName: String, name: String)(implicit mdc: OreMDC): Option[Path] =
    findFirstFile(getPendingIconDir(ownerName, name))

  private def findFirstFile(dir: Path)(implicit MDC: OreMDC): Option[Path] = {
    if (exists(dir)) {
      Resource
        .fromAutoCloseable(IO(list(dir)))
        .use { stream =>
          IO.pure(stream.iterator.asScala.filterNot(isDirectory(_)).toStream.headOption)
        }
        .recoverWith {
          case e: IOException => IO(MDCLogger.error("an error occurred while searching a directory", e)).as(None)
        }
        .unsafeRunSync()
    } else
      None
  }

}
