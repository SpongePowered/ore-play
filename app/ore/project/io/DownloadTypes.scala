package ore.project.io

import db.impl.OrePostgresDriver
import db.table.MappedType

import scala.language.implicitConversions

import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

/**
  * Represents different kind of downloads.
  */
object DownloadTypes extends Enumeration {

  /**
    * The download was for the file that was originally uploaded.
    */
  val UploadedFile = DownloadType(0)

  /**
    * The download was for just the JAR file of the upload.
    */
  val JarFile = DownloadType(1)

  /**
    * The download was for the signature file of the upload.
    */
  val SignatureFile = DownloadType(2)

  case class DownloadType(i: Int) extends super.Val(i) with MappedType[DownloadType] {
    override implicit val mapper: JdbcType[DownloadType] with BaseTypedType[DownloadType] = OrePostgresDriver.api.downloadTypeTypeMapper
  }
  implicit def convert(v: Value): DownloadType = v.asInstanceOf[DownloadType]

}
