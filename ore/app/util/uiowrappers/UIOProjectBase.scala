package util.uiowrappers

import javax.inject.Inject

import db.impl.access.ProjectBase
import ore.db.Model
import ore.models.project.{Channel, Page, Project, Version}
import ore.util.OreMDC

import cats.data.OptionT
import com.typesafe.scalalogging.LoggerTakingImplicit
import scalaz.zio.{Task, UIO}

class UIOProjectBase @Inject()(underlying: ProjectBase[Task]) extends ProjectBase[UIO] {
  override def missingFile: UIO[Seq[Model[Version]]] = underlying.missingFile.orDie

  override def refreshHomePage(logger: LoggerTakingImplicit[OreMDC])(implicit mdc: OreMDC): UIO[Unit] =
    underlying.refreshHomePage(logger).orDie

  override def stale: UIO[Seq[Model[Project]]] = underlying.stale.orDie

  override def withName(owner: String, name: String): OptionT[UIO, Model[Project]] =
    OptionT(underlying.withName(owner, name).value.orDie)

  override def withSlug(owner: String, slug: String): OptionT[UIO, Model[Project]] =
    OptionT(underlying.withSlug(owner, slug).value.orDie)

  override def withPluginId(pluginId: String): OptionT[UIO, Model[Project]] =
    OptionT(underlying.withPluginId(pluginId).value.orDie)

  override def isNamespaceAvailable(owner: String, slug: String): UIO[Boolean] =
    underlying.isNamespaceAvailable(owner, slug).orDie

  override def exists(owner: String, name: String): UIO[Boolean] = underlying.exists(owner, name).orDie

  override def savePendingIcon(project: Project)(implicit mdc: OreMDC): UIO[Unit] =
    underlying.savePendingIcon(project).orDie

  override def rename(project: Model[Project], name: String): UIO[Boolean] = underlying.rename(project, name).orDie

  override def deleteChannel(project: Model[Project], channel: Model[Channel]): UIO[Unit] =
    underlying.deleteChannel(project, channel).orDie

  override def prepareDeleteVersion(version: Model[Version]): UIO[Model[Project]] =
    underlying.prepareDeleteVersion(version).orDie

  override def deleteVersion(version: Model[Version])(implicit mdc: OreMDC): UIO[Model[Project]] =
    underlying.deleteVersion(version).orDie

  override def delete(project: Model[Project])(implicit mdc: OreMDC): UIO[Int] = underlying.delete(project).orDie

  override def queryProjectPages(project: Model[Project]): UIO[Seq[(Model[Page], Seq[Model[Page]])]] =
    underlying.queryProjectPages(project).orDie
}
