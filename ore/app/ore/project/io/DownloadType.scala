package ore.project.io

import scala.collection.immutable

import enumeratum.values._

/**
  * Represents different kind of downloads.
  */
sealed abstract class DownloadType(val value: Int) extends IntEnumEntry
object DownloadType extends IntEnum[DownloadType] {

  val values: immutable.IndexedSeq[DownloadType] = findValues

  /**
    * The download was for the file that was originally uploaded.
    */
  case object UploadedFile extends DownloadType(0)

  /**
    * The download was for just the JAR file of the upload.
    */
  case object JarFile extends DownloadType(1)
}
