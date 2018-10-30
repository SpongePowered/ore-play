package ore.project.io

import java.io.BufferedReader

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

import db.ObjId
import models.project.{Tag, TagColor}
import ore.project.Dependency

import org.spongepowered.plugin.meta.McModInfo

/**
  * The metadata within a [[PluginFile]]
  *
  * @author phase
  * @param data the data within a [[PluginFile]]
  */
class PluginFileData(data: Seq[DataValue[_]]) {

  private val dataValues = data
    .groupBy(_.key)
    .flatMap {
      case (key, value) =>
        // combine dependency lists that may come from different files
        if (value.lengthCompare(1) > 0 && value.head.isInstanceOf[DependencyDataValue]) {
          val dependencies = value.flatMap(_.value.asInstanceOf[Seq[Dependency]])
          Seq(DependencyDataValue(key, dependencies))
        } else value
    }
    .toSeq

  def id: Option[String] =
    get[String]("id")

  def version: Option[String] =
    get[String]("version")

  def authors: Seq[String] =
    get[Seq[String]]("authors").getOrElse(Seq())

  def dependencies: Seq[Dependency] =
    get[Seq[Dependency]]("dependencies").getOrElse(Seq())

  def get[T](key: String): Option[T] =
    dataValues
      .filter(_.key == key)
      .filter(_.isInstanceOf[DataValue[T @unchecked]])
      .map(_.asInstanceOf[DataValue[T]].value)
      .headOption

  def isValidPlugin: Boolean =
    dataValues.exists(_.isInstanceOf[StringDataValue]) &&
      dataValues.exists(_.isInstanceOf[StringDataValue])

  def ghostTags: Seq[Tag] = {
    val buffer = new ArrayBuffer[Tag]

    if (containsMixins) {
      val mixinTag = Tag(ObjId.Uninitialized(), List(), "Mixin", "", TagColor.Mixin)
      buffer += mixinTag
    }

    println("PluginFileData#getGhostTags: " + buffer)
    buffer
  }

  /**
    * A mod using Mixins will contain the "MixinConfigs" attribute in their MANIFEST
    *
    * @return
    */
  def containsMixins: Boolean =
    dataValues.exists(p => p.key == "MixinConfigs" && p.isInstanceOf[StringDataValue])

}

object PluginFileData {
  val fileTypes: Seq[FileTypeHandler] = Seq(McModInfoHandler, ManifestHandler, ModTomlHandler)

  def fileNames: Seq[String] = fileTypes.map(_.fileName).distinct

  def getData(fileName: String, stream: BufferedReader): Seq[DataValue[_]] =
    fileTypes.filter(_.fileName == fileName).flatMap(_.getData(stream))

}

/**
  * A data element in a data file
  *
  * @param key   the key for the value
  * @param value the value extracted from the file
  * @tparam T the type of the value
  */
sealed trait DataValue[T] {
  def key: String
  def value: T
}

/**
  * A data element that is a String, such as the plugin id or version
  *
  * @param value the value extracted from the file
  */
case class StringDataValue(key: String, value: String) extends DataValue[String]

/**
  * A data element that is a list of strings, such as an authors list
  *
  * @param value the value extracted from the file
  */
case class StringListValue(key: String, value: Seq[String]) extends DataValue[Seq[String]]

/**
  * A data element that is a list of [[Dependency]]
  *
  * @param value the value extracted from the file
  */
case class DependencyDataValue(key: String, value: Seq[Dependency]) extends DataValue[Seq[Dependency]]

sealed abstract case class FileTypeHandler(fileName: String) {
  def getData(bufferedReader: BufferedReader): Seq[DataValue[_]]
}

object McModInfoHandler extends FileTypeHandler("mcmod.info") {
  override def getData(bufferedReader: BufferedReader): Seq[DataValue[_]] = {
    val dataValues = new ArrayBuffer[DataValue[_]]
    try {
      val info = McModInfo.DEFAULT.read(bufferedReader).asScala
      if (info.lengthCompare(1) < 0) return Nil

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
    } catch {
      case e: Exception => e.printStackTrace()
    }

    dataValues
  }
}

object ManifestHandler extends FileTypeHandler("META-INF/MANIFEST.MF") {
  override def getData(bufferedReader: BufferedReader): Seq[DataValue[_]] = {
    val dataValues = new ArrayBuffer[DataValue[_]]

    val lines = Stream.continually(bufferedReader.readLine()).takeWhile(_ != null)
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
  override def getData(bufferedReader: BufferedReader): Seq[DataValue[_]] =
    // TODO: Get format from Forge once it has been decided on
    Nil
}
