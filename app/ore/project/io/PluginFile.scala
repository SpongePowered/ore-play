package ore.project.io

import java.io._
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarFile, JarInputStream}
import java.util.zip.{ZipEntry, ZipFile}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

import play.api.i18n.Messages

import db.DbRef
import models.user.User
import ore.user.UserOwned

import com.google.common.base.Preconditions._
import org.apache.commons.codec.digest.DigestUtils

/**
  * Represents an uploaded plugin file.
  *
  * @param _path Path to uploaded file
  */
class PluginFile(private var _path: Path, val signaturePath: Path, val user: User) extends UserOwned {

  private var _data: Option[PluginFileData] = None

  /**
    * Returns the actual file path associated with this plugin.
    *
    * @return Path of plugin file
    */
  def path: Path = {
    checkNotNull(this._path, "file is deleted", "")
    this._path
  }

  /**
    * Deletes the File at this PluginFile's Path
    */
  def delete(): Unit = {
    Files.delete(this._path)
    this._path = null
  }

  /**
    * Returns the loaded PluginMetadata, if any.
    *
    * @return PluginMetadata if present, None otherwise
    */
  def data: Option[PluginFileData] = this._data

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = DigestUtils.md5Hex(Files.newInputStream(this.path))

  /**
    * Reads the temporary file's plugin meta file and returns the result.
    *
    * TODO: More validation on PluginMetadata results (null checks, etc)
    *
    * @return Plugin metadata or an error message
    */
  @throws[InvalidPluginFileException]
  def loadMeta()(implicit messages: Messages): Either[String, PluginFileData] = {
    val fileNames = PluginFileData.fileNames

    var jarIn: JarInputStream = null
    try {
      // Find plugin JAR
      jarIn = new JarInputStream(newJarStream)

      val data = new ArrayBuffer[DataValue[_]]()

      // Find plugin meta file
      var entry: JarEntry = jarIn.getNextJarEntry
      while (entry != null) {
        if (fileNames.contains(entry.getName)) {
          data ++= PluginFileData.getData(entry.getName, new BufferedReader(new InputStreamReader(jarIn)))
        }
        entry = jarIn.getNextJarEntry
      }

      // Mainfest file isn't read in the jar stream for whatever reason
      // so we need to use the java API
      if (fileNames.contains(JarFile.MANIFEST_NAME)) {
        val manifest = jarIn.getManifest
        if (manifest != null) {
          val manifestLines = new BufferedReader(
            new StringReader(
              jarIn.getManifest.getMainAttributes.asScala
                .map(p => p._1.toString + ": " + p._2.toString)
                .mkString("\n")
            )
          )

          data ++= PluginFileData.getData(JarFile.MANIFEST_NAME, manifestLines)
        }
      }

      // This won't be called if a plugin uses mixins but doesn't
      // have a mcmod.info, but the check below will catch that
      if (data.isEmpty)
        return Left(messages("error.plugin.metaNotFound"))

      val fileData = new PluginFileData(data)

      this._data = Some(fileData)
      if (!fileData.isValidPlugin) {
        return Left(messages("error.plugin.incomplete", "id or version"))
      }

      Right(fileData)
    } catch {
      case e: Exception =>
        Left(e.getMessage)
    } finally {
      if (jarIn != null)
        jarIn.close()
      else
        return Left(messages("error.plugin.unexpected"))
    }
  }

  /**
    * Returns a new [[InputStream]] for this [[PluginFile]]'s main JAR file.
    *
    * @return InputStream of JAR
    */
  @throws[IOException]
  def newJarStream(implicit messages: Messages): InputStream = {
    if (this.path.toString.endsWith(".jar"))
      Files.newInputStream(this.path)
    else {
      val zip = new ZipFile(this.path.toFile)
      zip.getInputStream(findTopLevelJar(zip))
    }
  }

  private def findTopLevelJar(zip: ZipFile)(implicit messages: Messages): ZipEntry = {
    if (this.path.toString.endsWith(".jar"))
      throw new Exception("Plugin is already JAR")

    var pluginEntry: ZipEntry = null
    val entries               = zip.entries()
    breakable {
      while (entries.hasMoreElements) {
        val entry = entries.nextElement()
        val name  = entry.getName
        if (!entry.isDirectory && name.split("/").length == 1 && name.endsWith(".jar")) {
          pluginEntry = entry
          break
        }
      }
    }

    if (pluginEntry == null)
      throw InvalidPluginFileException(messages("error.plugin.jarNotFound"))
    pluginEntry
  }

  override def userId: DbRef[User] = this.user.id.value

}
