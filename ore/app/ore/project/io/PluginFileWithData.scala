package ore.project.io
import java.nio.file.{Files, Path}

import db.Model
import models.user.User
import util.StringUtils

import cats.effect.IO

class PluginFileWithData(val path: Path, val user: Model[User], val data: PluginFileData) {

  def delete: IO[Unit] = IO(Files.delete(path))

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = StringUtils.md5ToHex(Files.readAllBytes(this.path))
}
