package ore.project.io
import java.nio.file.{Files, Path}

import db.Model
import models.user.User

import cats.effect.IO
import org.apache.commons.codec.digest.DigestUtils

class PluginFileWithData(val path: Path, val signaturePath: Path, val user: Model[User], val data: PluginFileData) {

  def delete: IO[Unit] = IO(Files.delete(path))

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = DigestUtils.md5Hex(Files.newInputStream(this.path))
}
