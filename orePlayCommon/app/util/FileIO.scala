package util

import scala.language.higherKinds

import java.nio.file.Path

import cats.tagless.InvariantK
import cats.{Traverse, ~>}

trait FileIO[F[_]] {

  def list(path: Path): F[Stream[Path]]

  def exists(path: Path): F[Boolean]

  def notExists(path: Path): F[Boolean]

  def isDirectory(path: Path): F[Boolean]

  def createDirectories(path: Path): F[Unit]

  def move(from: Path, to: Path): F[Unit]

  def delete(path: Path): F[Unit]

  def deleteIfExists(path: Path): F[Unit]

  def traverseLimited[G[_]: Traverse, A, B](fs: G[A])(f: A => F[B]): F[List[B]]

  def executeBlocking[A](block: => A): F[A]
}
object FileIO {
  implicit val fileIOInvariantK: InvariantK[FileIO] = new InvariantK[FileIO] {
    override def imapK[F[_], G[_]](af: FileIO[F])(fk: F ~> G)(gK: G ~> F): FileIO[G] = new FileIO[G] {
      override def list(path: Path): G[Stream[Path]] = fk(af.list(path))

      override def exists(path: Path): G[Boolean] = fk(af.exists(path))

      override def notExists(path: Path): G[Boolean] = fk(af.notExists(path))

      override def isDirectory(path: Path): G[Boolean] = fk(af.isDirectory(path))

      override def createDirectories(path: Path): G[Unit] = fk(af.createDirectories(path))

      override def move(from: Path, to: Path): G[Unit] = fk(af.move(from, to))

      override def delete(path: Path): G[Unit] = fk(af.delete(path))

      override def deleteIfExists(path: Path): G[Unit] = fk(af.deleteIfExists(path))

      override def traverseLimited[T[_]: Traverse, A, B](fs: T[A])(f: A => G[B]): G[List[B]] =
        fk(af.traverseLimited(fs)(a => gK(f(a))))

      override def executeBlocking[A](block: => A): G[A] = fk(af.executeBlocking(block))
    }
  }
}
