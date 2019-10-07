package ore.models.project.io

import scala.language.higherKinds

import java.nio.file.{Files, Path}

import ore.db.Model
import ore.models.user.User
import ore.util.StringUtils

import cats.effect.Sync

class PluginFileWithData(val path: Path, val user: Model[User], val data: PluginFileData) {

  def delete[F[_]](implicit F: Sync[F]): F[Unit] = F.delay(Files.delete(path))

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = StringUtils.md5ToHex(Files.readAllBytes(this.path))
}
