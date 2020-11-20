package db.impl.access

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

import play.api.mvc.Request

import ore.OreConfig
import ore.auth.SpongeAuthApi
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{Model, ModelService}
import ore.models.organization.Organization
import ore.models.user.{Session, User}
import ore.permission.Permission
import ore.util.OreMDC
import ore.util.StringUtils._
import util.syntax._

import cats.syntax.all._
import zio.{IO, UIO, ZIO}
import zio.interop.catz._

/**
  * Represents a central location for all Users.
  */
trait UserBase {

  /**
    * Returns the user with the specified username. If the specified username
    * is not found in the database, an attempt will be made to fetch the user
    * from Discourse.
    *
    * @param username Username of user
    * @return User if found, None otherwise
    */
  def withName(username: String)(implicit mdc: OreMDC): IO[Option[Nothing], Model[User]]

  /**
    * Returns the requested user when it is the requester or has the requested permission in the orga
    *
    * @param user the requester
    * @param name the requested username
    * @param perm the requested permission
    *
    * @return the requested user
    */
  def requestPermission(user: Model[User], name: String, perm: Permission)(
      implicit mdc: OreMDC
  ): IO[Option[Nothing], Model[User]]

  /**
    * Attempts to find the specified User in the database or creates a new User
    * if one does not exist.
    *
    * @param user User to find
    *
    * @return     Found or new User
    */
  def getOrCreate(
      username: String,
      user: User,
      ifInsert: Model[User] => UIO[Unit]
  ): UIO[Model[User]]

  /**
    * Creates a new [[Session]] for the specified [[User]].
    *
    * @param user User to create session for
    *
    * @return     Newly created session
    */
  def createSession(user: Model[User]): UIO[Model[Session]]

  /**
    * Returns the currently authenticated User.c
    *
    * @param session  Current session
    * @return         Authenticated user, if any, None otherwise
    */
  def current(implicit session: Request[_], mdc: OreMDC): IO[Option[Nothing], Model[User]]
}

object UserBase {

  /**
    * Default live implementation of [[UserBase]]
    */
  class UserBaseF(implicit service: ModelService[UIO], auth: SpongeAuthApi, config: OreConfig) extends UserBase {

    def withName(username: String)(implicit mdc: OreMDC): IO[Option[Nothing], Model[User]] =
      ModelView.now(User).find(equalsIgnoreCase(_.name, username)).value.someOrElseM {
        auth.getUser(username).map(_.toUser).option.get.flatMap(service.insert(_))
      }

    def requestPermission(user: Model[User], name: String, perm: Permission)(
        implicit mdc: OreMDC
    ): IO[Option[Nothing], Model[User]] = {
      this.withName(name).flatMap { toCheck =>
        if (user.id == toCheck.id) ZIO.succeed(user) // Same user
        else
          for {
            orga     <- toCheck.toMaybeOrganization(ModelView.now(Organization)).value.get
            hasPerms <- user.permissionsIn[Model[Organization], UIO](orga).map(_.has(perm))
            res      <- if (hasPerms) ZIO.succeed(toCheck) else ZIO.fail(None)
          } yield res
      }
    }

    def getOrCreate(
        username: String,
        user: User,
        ifInsert: Model[User] => UIO[Unit]
    ): UIO[Model[User]] = {
      val like = ModelView.now(User).find(_.name.toLowerCase === username.toLowerCase)

      like.value.someOrElseM(service.insert(user).tap(ifInsert))
    }

    def createSession(user: Model[User]): UIO[Model[Session]] = {
      val maxAge     = config.ore.session.maxAge
      val expiration = OffsetDateTime.now().plus(maxAge.toMillis, ChronoUnit.MILLIS)
      val token      = UUID.randomUUID().toString
      service.insert(Session(expiration, user.id, token))
    }

    /**
      * Returns the [[Session]] of the specified token ID. If the session has
      * expired it will be deleted immediately and None will be returned.
      *
      * @param token  Token of session
      * @return       Session if found and has not expired
      */
    private def getSession(token: String): IO[Option[Nothing], Model[Session]] =
      ModelView.now(Session).find(_.token === token).value.get.flatMap { session =>
        if (session.hasExpired)
          service.delete(session) *> ZIO.fail(None)
        else
          ZIO.succeed(session)
      }

    def current(implicit session: Request[_], mdc: OreMDC): IO[Option[Nothing], Model[User]] =
      ZIO
        .fromOption(session.cookies.get("_oretoken"))
        .flatMap(cookie => getSession(cookie.value))
        .flatMap(s => ModelView.now(User).get(s.userId).value)
        .someOrFail(None)
  }

  trait UserOrdering
  object UserOrdering {
    val Projects = "projects"
    val UserName = "username"
    val JoinDate = "joined"
    val Role     = "roles"
  }
}
