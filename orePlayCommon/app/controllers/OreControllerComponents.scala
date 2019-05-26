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

import cats.effect.IO

trait OreControllerComponents[F[_]] extends ControllerComponents {
  def service: ModelService[F]
  def sso: SSOApi[F]
  def bakery: Bakery
  def config: OreConfig
  def users: UserBase[F]
  def projects: ProjectBase[F]
  def organizations: OrganizationBase[F]
}
case class DefaultOreControllerComponents @Inject()(
    service: ModelService[IO],
    sso: SSOApi[IO],
    bakery: Bakery,
    config: OreConfig,
    users: UserBase[IO],
    projects: ProjectBase[IO],
    organizations: OrganizationBase[IO],
    actionBuilder: DefaultActionBuilder,
    parsers: PlayBodyParsers,
    messagesApi: MessagesApi,
    langs: Langs,
    fileMimeTypes: FileMimeTypes,
    executionContext: ExecutionContext
) extends OreControllerComponents[IO]
