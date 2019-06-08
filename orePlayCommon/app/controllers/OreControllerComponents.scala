package controllers

import scala.language.higherKinds

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.http.FileMimeTypes
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc.{ControllerComponents, DefaultActionBuilder, PlayBodyParsers}

import controllers.sugar.Bakery
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import ore.OreConfig
import ore.auth.SSOApi
import ore.db.ModelService

import scalaz.zio
import scalaz.zio.{Task, UIO}
import scalaz.zio.blocking.Blocking

trait OreControllerComponents extends ControllerComponents {
  def uioEffects: OreControllerEffects[UIO]
  def taskEffects: OreControllerEffects[Task]
  def bakery: Bakery
  def config: OreConfig
  def zioRuntime: zio.Runtime[Blocking]
}
trait OreControllerEffects[F[_]] {
  def service: ModelService[F]
  def sso: SSOApi[F]
  def users: UserBase[F]
  def projects: ProjectBase[F]
  def organizations: OrganizationBase[F]
}

case class DefaultOreControllerComponents @Inject()(
    uioEffects: OreControllerEffects[UIO],
    taskEffects: OreControllerEffects[Task],
    bakery: Bakery,
    config: OreConfig,
    actionBuilder: DefaultActionBuilder,
    parsers: PlayBodyParsers,
    messagesApi: MessagesApi,
    langs: Langs,
    fileMimeTypes: FileMimeTypes,
    executionContext: ExecutionContext,
    zioRuntime: zio.Runtime[Blocking]
) extends OreControllerComponents

case class TaskOreControllerEffects @Inject()(
    service: ModelService[Task],
    sso: SSOApi[Task],
    users: UserBase[Task],
    projects: ProjectBase[Task],
    organizations: OrganizationBase[Task],
) extends OreControllerEffects[Task]

case class UIOOreControllerEffects @Inject()(
    service: ModelService[UIO],
    sso: SSOApi[UIO],
    users: UserBase[UIO],
    projects: ProjectBase[UIO],
    organizations: OrganizationBase[UIO],
) extends OreControllerEffects[UIO]
