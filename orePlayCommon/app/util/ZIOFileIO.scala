package util

import scala.language.higherKinds

import java.nio.file.{Files, Path}
import javax.inject.Inject

import scala.collection.JavaConverters._

import ore.OreConfig

import cats.Traverse
import cats.syntax.all._
import zio.ZIO
import zio.blocking._

class ZIOFileIO(nioBlockingFibers: Long) extends FileIO[ZIO[Blocking, Throwable, ?]] {

  type BlockIO[A] = ZIO[Blocking, Throwable, A]

  override def list(path: Path): BlockIO[Stream[Path]] = effectBlocking(Files.list(path).iterator.asScala.toStream)

  override def exists(path: Path): BlockIO[Boolean] = effectBlocking(Files.exists(path))

  override def notExists(path: Path): BlockIO[Boolean] = effectBlocking(Files.notExists(path))

  override def isDirectory(path: Path): BlockIO[Boolean] = effectBlocking(Files.isDirectory(path))

  override def createDirectories(path: Path): BlockIO[Unit] = effectBlocking {
    Files.createDirectories(path)
    ()
  }

  override def move(from: Path, to: Path): BlockIO[Unit] = effectBlocking {
    Files.move(from, to)
    ()
  }

  override def delete(path: Path): BlockIO[Unit] = effectBlocking(Files.delete(path))

  override def deleteIfExists(path: Path): BlockIO[Unit] = effectBlocking {
    Files.deleteIfExists(path)
    ()
  }

  override def traverseLimited[G[_]: Traverse, A, B](fs: G[A])(f: A => BlockIO[B]): BlockIO[List[B]] =
    ZIO.foreachParN(nioBlockingFibers)(fs.toList)(f)

  override def executeBlocking[A](block: => A): BlockIO[A] = effectBlocking(block)
}
object ZIOFileIO {
  def apply(config: OreConfig): ZIOFileIO = new ZIOFileIO(config.performance.nioBlockingFibers)
}
