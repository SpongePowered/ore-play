package db.impl.access

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.Request

import db.impl.OrePostgresDriver.api._
import db.{ModelBase, ModelService, ObjectId, ObjectTimestamp}
import models.user.{Session, User}
import ore.OreConfig
import ore.permission.Permission
import security.spauth.SpongeAuthApi
import util.StringUtils._

import cats.data.OptionT
import cats.instances.future._

/**
  * Represents a central location for all Users.
  */
class UserBase(implicit val service: ModelService, config: OreConfig) extends ModelBase[User] {

  override val modelClass: Class[User] = classOf[User]

  implicit val self: UserBase = this

  /**
    * Returns the user with the specified username. If the specified username
    * is not found in the database, an attempt will be made to fetch the user
    * from Discourse.
    *
    * @param username Username of user
    * @return User if found, None otherwise
    */
  def withName(username: String)(implicit ec: ExecutionContext, auth: SpongeAuthApi): OptionT[Future, User] =
    this.find(equalsIgnoreCase(_.name, username)).orElse {
      auth.getUser(username).map(User.fromSponge).semiflatMap(getOrCreate)
    }

  /**
    * Returns the requested user when it is the requester or has the requested permission in the orga
    *
    * @param user the requester
    * @param name the requested username
    * @param perm the requested permission
    *
    * @return the requested user
    */
  def requestPermission(user: User, name: String, perm: Permission)(
      implicit ec: ExecutionContext,
      auth: SpongeAuthApi
  ): OptionT[Future, User] = {
    this.withName(name).flatMap { toCheck =>
      if (user == toCheck) OptionT.pure[Future](user) // Same user
      else
        toCheck.toMaybeOrganization.flatMap { orga =>
          OptionT.liftF(user.can(perm) in orga).collect {
            case true => toCheck // Has Orga perm
          }
        }
    }
  }

  /**
    * Attempts to find the specified User in the database or creates a new User
    * if one does not exist.
    *
    * @param user User to find
    *
    * @return     Found or new User
    */
  def getOrCreate(user: User)(implicit ec: ExecutionContext): Future[User] = {
    def like = this.find(_.name.toLowerCase === user.name.toLowerCase)

    like.value.flatMap {
      case Some(u) => Future.successful(u)
      case None    => service.insert(user)
    }
  }

  /**
    * Creates a new [[Session]] for the specified [[User]].
    *
    * @param user User to create session for
    *
    * @return     Newly created session
    */
  def createSession(user: User)(implicit ec: ExecutionContext): Future[Session] = {
    val maxAge     = this.config.play.get[Int]("http.session.maxAge")
    val expiration = new Timestamp(new Date().getTime + maxAge * 1000L)
    val token      = UUID.randomUUID().toString
    val session    = Session(ObjectId.Uninitialized, ObjectTimestamp.Uninitialized, expiration, user.name, token)
    this.service.access[Session]().add(session)
  }

  /**
    * Returns the [[Session]] of the specified token ID. If the session has
    * expired it will be deleted immediately and None will be returned.
    *
    * @param token  Token of session
    * @return       Session if found and has not expired
    */
  private def getSession(token: String)(implicit ec: ExecutionContext): OptionT[Future, Session] =
    this.service.access[Session]().find(_.token === token).subflatMap { session =>
      if (session.hasExpired) {
        session.remove()
        None
      } else Some(session)
    }

  /**
    * Returns the currently authenticated User.c
    *
    * @param session  Current session
    * @return         Authenticated user, if any, None otherwise
    */
  def current(implicit session: Request[_], ec: ExecutionContext, authApi: SpongeAuthApi): OptionT[Future, User] =
    OptionT
      .fromOption[Future](session.cookies.get("_oretoken"))
      .flatMap(cookie => getSession(cookie.value))
      .flatMap(_.user)

}

object UserBase {
  def apply()(implicit userBase: UserBase): UserBase = userBase

  implicit def fromService(implicit service: ModelService): UserBase = service.getModelBase(classOf[UserBase])

  trait UserOrdering
  object UserOrdering {
    val Projects = "projects"
    val UserName = "username"
    val JoinDate = "joined"
    val Role     = "roles"
  }
}
