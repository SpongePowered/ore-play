package ore.project.io

import java.io.BufferedReader

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

import db.{Model, DbRef, ModelService}
import models.project.{TagColor, Version, VersionTag}
import ore.project.Dependency

import cats.effect.IO
import org.spongepowered.plugin.meta.McModInfo

/**
  * The metadata within a [[PluginFile]]
  *
  * @author phase
  * @param data the data within a [[PluginFile]]
  */
class PluginFileData(data: Seq[DataValue]) {

  private val dataValues = data
    .groupBy(_.key)
    .flatMap {
      case (key, values) =>
        // combine dependency lists that may come from different files
        if (values.lengthCompare(1) > 0) {
          import cats.syntax.all._
          import cats.instances.vector._

          val (otherValues, depSeq) = values.toVector.partitionEither {
            case DependencyDataValue(_, deps) => Right(deps)
            case other                        => Left(other)
          }

          otherValues :+ DependencyDataValue(key, depSeq.flatten)
        } else values
    }
    .toSeq

  def id: Option[String] =
    getString("id")

  def name: Option[String] =
    getString("name")

  def description: Option[String] =
    getString("description")

  def version: Option[String] =
    getString("version")

  def authors: Seq[String] =
    getStrings("authors").getOrElse(Seq())

  def dependencies: Seq[Dependency] =
    getDeps("dependencies").getOrElse(Seq())

  private def getString(key: String) = get(key).collect {
    case StringDataValue(_, value) => value
  }

  private def getStrings(key: String) = get(key).collect {
    case StringListValue(_, value) => value
  }

  private def getDeps(key: String) = get(key).collect {
    case DependencyDataValue(_, value) => value
  }

  private def get(key: String) = dataValues.find(_.key == key)

  def isValidPlugin: Boolean = dataValues.exists {
    case _: StringDataValue => true
    case _                  => false
  }

  def createTags(versionId: DbRef[Version])(implicit service: ModelService): IO[Seq[Model[VersionTag]]] = {
    val buffer = new ArrayBuffer[VersionTag]

    if (containsMixins) {
      val mixinTag = VersionTag(versionId, "Mixin", "", TagColor.Mixin)
      buffer += mixinTag
    }

    service.bulkInsert(buffer)
  }

  /**
    * A mod using Mixins will contain the "MixinConfigs" attribute in their MANIFEST
    *
    * @return
    */
  def containsMixins: Boolean =
    dataValues.exists {
      case p: StringDataValue => p.key == "MixinConfigs"
      case _                  => false
    }

}

object PluginFileData {
  val fileTypes: Seq[FileTypeHandler] = Seq(McModInfoHandler, ManifestHandler, ModTomlHandler)

  def fileNames: Seq[String] = fileTypes.map(_.fileName).distinct

  def getData(fileName: String, stream: BufferedReader): Seq[DataValue] =
    fileTypes.filter(_.fileName == fileName).flatMap(_.getData(stream))

}

/**
  * A data element in a data file.
  */
sealed trait DataValue {
  def key: String
}

/**
  * A data element that is a String, such as the plugin id or version
  *
  * @param value the value extracted from the file
  */
case class StringDataValue(key: String, value: String) extends DataValue

/**
  * A data element that is a list of strings, such as an authors list
  *
  * @param value the value extracted from the file
  */
case class StringListValue(key: String, value: Seq[String]) extends DataValue

/**
  * A data element that is a list of [[Dependency]]
  *
  * @param value the value extracted from the file
  */
case class DependencyDataValue(key: String, value: Seq[Dependency]) extends DataValue

sealed abstract case class FileTypeHandler(fileName: String) {
  def getData(bufferedReader: BufferedReader): Seq[DataValue]
}

object McModInfoHandler extends FileTypeHandler("mcmod.info") {

  @SuppressWarnings(Array("scalafix:DisableSyntax.null"))
  override def getData(bufferedReader: BufferedReader): Seq[DataValue] = {
    val dataValues = new ArrayBuffer[DataValue]
    try {
      val info = McModInfo.DEFAULT.read(bufferedReader).asScala
      if (info.lengthCompare(1) < 0) Nil
      else {
        val metadata = info.head

        if (metadata.getId != null)
          dataValues += StringDataValue("id", metadata.getId)

        if (metadata.getVersion != null)
          dataValues += StringDataValue("version", metadata.getVersion)

        if (metadata.getName != null)
          dataValues += StringDataValue("name", metadata.getName)

        if (metadata.getDescription != null)
          dataValues += StringDataValue("description", metadata.getDescription)

        if (metadata.getUrl != null)
          dataValues += StringDataValue("url", metadata.getUrl)

        if (metadata.getAuthors != null)
          dataValues += StringListValue("authors", metadata.getAuthors.asScala)

        if (metadata.getDependencies != null) {
          val dependencies = metadata.getDependencies.asScala.map(p => Dependency(p.getId, p.getVersion)).toSeq
          dataValues += DependencyDataValue("dependencies", dependencies)
        }

        dataValues
      }
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        Nil
    }
  }
}

object ManifestHandler extends FileTypeHandler("META-INF/MANIFEST.MF") {
  override def getData(bufferedReader: BufferedReader): Seq[DataValue] = {
    val dataValues = new ArrayBuffer[DataValue]

    val lines = Stream.continually(bufferedReader.readLine()).takeWhile(_ != null) // scalafix:ok
    for (line <- lines) {
      // Check for Mixins
      if (line.startsWith("MixinConfigs: ")) {
        val mixinConfigs = line.split(": ")(1)
        dataValues += StringDataValue("MixinConfigs", mixinConfigs)
      }
    }

    dataValues
  }
}

object ModTomlHandler extends FileTypeHandler("mod.toml") {
  override def getData(bufferedReader: BufferedReader): Seq[DataValue] =
    // TODO: Get format from Forge once it has been decided on
    Nil
}
