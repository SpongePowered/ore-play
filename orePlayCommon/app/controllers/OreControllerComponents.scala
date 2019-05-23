package controllers

import scala.language.higherKinds

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import controllers.sugar.Bakery
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import ore.OreConfig
import ore.auth.SSOApi
import ore.db.ModelService

import cats.effect.IO

trait OreControllerComponents[F[_]] {
  def service: ModelService[F]
  def sso: SSOApi[F]
  def bakery: Bakery
  def config: OreConfig
  def users: UserBase[F]
  def projects: ProjectBase[F]
  def organizations: OrganizationBase[F]
  def ec: ExecutionContext
}
case class DefaultOreControllerComponents @Inject() (
    service: ModelService[IO],
    sso: SSOApi[IO],
    bakery: Bakery,
    config: OreConfig,
    users: UserBase[IO],
    projects: ProjectBase[IO],
    organizations: OrganizationBase[IO],
    ec: ExecutionContext
) extends OreControllerComponents[IO]
