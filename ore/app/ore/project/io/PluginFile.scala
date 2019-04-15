package ore.project.io

import java.io._
import java.nio.file.{Files, Path}
import java.util.jar.{JarFile, JarInputStream}
import java.util.zip.{ZipEntry, ZipFile}

import scala.collection.JavaConverters._

import play.api.i18n.Messages

import db.Model
import models.user.User
import ore.user.UserOwned

import cats.data.EitherT
import cats.effect.{IO, Resource}

/**
  * Represents an uploaded plugin file.
  *
  * @param path Path to uploaded file
  */
class PluginFile(val path: Path, val user: Model[User]) {

  /**
    * Reads the temporary file's plugin meta file and returns the result.
    *
    * TODO: More validation on PluginMetadata results (null checks, etc)
    *
    * @return Plugin metadata or an error message
    */
  def loadMeta(implicit messages: Messages): EitherT[IO, String, PluginFileWithData] = {
    val fileNames = PluginFileData.fileNames

    val res = newJarStream
      .flatMap { in =>
        val jarIn = IO(in.map(new JarInputStream(_)))
        Resource.make(jarIn) {
          case Right(is) => IO(is.close())
          case _         => IO.unit
        }
      }
      .use { eJarIn =>
        IO {
          eJarIn.map { jarIn =>
            val fileDataSeq = Iterator
              .continually(jarIn.getNextJarEntry)
              .takeWhile(_ != null) // scalafix:ok
              .filter(entry => fileNames.contains(entry.getName))
              .flatMap(entry => PluginFileData.getData(entry.getName, new BufferedReader(new InputStreamReader(jarIn))))
              .toVector

            // Mainfest file isn't read in the jar stream for whatever reason
            // so we need to use the java API
            val manifestDataSeq = if (fileNames.contains(JarFile.MANIFEST_NAME)) {
              Option(jarIn.getManifest)
                .map { manifest =>
                  val manifestLines = new BufferedReader(
                    new StringReader(
                      manifest.getMainAttributes.asScala
                        .map(p => p._1.toString + ": " + p._2.toString)
                        .mkString("\n")
                    )
                  )

                  PluginFileData.getData(JarFile.MANIFEST_NAME, manifestLines)
                }
                .getOrElse(Nil)
            } else Nil

            val data = fileDataSeq ++ manifestDataSeq

            // This won't be called if a plugin uses mixins but doesn't
            // have a mcmod.info, but the check below will catch that
            if (data.isEmpty)
              Left(messages("error.plugin.metaNotFound"))
            else {
              val fileData = new PluginFileData(data)

              if (!fileData.isValidPlugin) Left(messages("error.plugin.incomplete", "id or version"))
              else Right(new PluginFileWithData(path, user, fileData))
            }
          }
        }
      }

    EitherT(res.map(_.flatMap(identity)))
  }

  /**
    * Returns a new [[InputStream]] for this [[PluginFile]]'s main JAR file.
    *
    * @return InputStream of JAR
    */
  def newJarStream: Resource[IO, Either[String, InputStream]] = {
    if (this.path.toString.endsWith(".jar"))
      Resource
        .fromAutoCloseable[IO, InputStream](IO(Files.newInputStream(this.path)))
        .flatMap(is => Resource.pure(Right(is)))
    else
      Resource
        .fromAutoCloseable(IO(new ZipFile(this.path.toFile)))
        .flatMap { zip =>
          val jarIn = IO(findTopLevelJar(zip).map(zip.getInputStream))

          Resource.make(jarIn) {
            case Right(is) => IO(is.close())
            case _         => IO.unit
          }
        }
  }

  private def findTopLevelJar(zip: ZipFile): Either[String, ZipEntry] = {
    val pluginEntry = zip.entries().asScala.find { entry =>
      val name = entry.getName
      !entry.isDirectory && name.split("/").length == 1 && name.endsWith(".jar")
    }

    pluginEntry.toRight("error.plugin.jarNotFound")
  }
}
object PluginFile {
  implicit val isUserOwned: UserOwned[PluginFile] = (a: PluginFile) => a.user.id.value
}
