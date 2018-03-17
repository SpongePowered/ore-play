package db.impl.access

import java.sql.Timestamp
import java.util.{Date, UUID}

import db.impl.OrePostgresDriver.api._
import db.impl.model.OreModel
import db.impl.schema.ProjectSchema
import db.{ModelBase, ModelService, Named}
import models.user.{Session, User}
import ore.OreConfig
import ore.permission.Permission
import ore.permission.scope.ScopeSubject
import ore.user.UserOwned
import play.api.mvc.Request
import play.api.mvc.Results.Unauthorized
import security.spauth.SpongeAuthApi
import util.StringUtils._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a central location for all Users.
  */
class UserBase(override val service: ModelService,
               auth: SpongeAuthApi,
               implicit val config: OreConfig)
  extends ModelBase[User] {

  import UserBase._

  override val modelClass = classOf[User]

  implicit val self = this

  /**
    * Returns the user with the specified username. If the specified username
    * is not found in the database, an attempt will be made to fetch the user
    * from Discourse.
    *
    * @param username Username of user
    * @return User if found, None otherwise
    */
  def withName(username: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
    this.find(equalsIgnoreCase(_.name, username)).flatMap {
      case Some(u) => Future.successful(Some(u))
      case None => this.auth.getUser(username) flatMap {
        case None => Future.successful(None)
        case Some(u) => User.fromSponge(u).flatMap(getOrCreate).map(Some(_))
      }
    }
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
  def requestPermission(user: User, name: String, perm: Permission)(implicit ec: ExecutionContext): Future[Option[User]] = {
    this.withName(name).flatMap {
      case None => Future.successful(None) // Name not found
      case Some(toCheck) =>
        if (user.equals(toCheck)) Future.successful(Some(user)) // Same user
        else {
          // TODO remove double DB access for orga check
          toCheck.isOrganization.flatMap {
            case false => Future.successful(None) // Not an orga
            case true => toCheck.toOrganization.flatMap { orga =>
              user can perm in orga map { perm =>
                if (perm) Some(toCheck) // Has Orga perm
                else None // Has not Orga perm
              }
            }
          }
        }
    }
  }

  /**
    * Returns a page of [[User]]s with at least one [[models.project.Project]].
    *
    * FIXME: Ordering is messed up
    *
    * @return Users with at least one project
    */
  def getAuthors(ordering: String = ORDERING_PROJECTS, page: Int = 1)(implicit ec: ExecutionContext): Future[Seq[User]] = {
    // determine ordering
    val (sort, reverse) = if (ordering.startsWith("-")) (ordering.substring(1), false) else (ordering, true)

    // TODO page and order should be done in Database!
    // get authors
    this.service.getSchema(classOf[ProjectSchema]).distinctAuthors.map { users =>
      sort match { // Sort
        case ORDERING_PROJECTS => users.sortBy(u => (service.await(u.projects.size).get, u.username))
        case ORDERING_JOIN_DATE => users.sortBy(u => (u.joinDate.getOrElse(u.createdAt.get), u.username))
        case ORDERING_USERNAME => users.sortBy(_.username)
        case ORDERING_ROLE => users.sortBy(_.globalRoles.toList.sortBy(_.trust).headOption.map(_.trust.level).getOrElse(-1))
        case _ => users.sortBy(u => (service.await(u.projects.size).get, u.username))
      }
    } map { users => // Reverse?
      if (reverse) users.reverse else users
    } map { users =>
      // get page slice
      val pageSize = this.config.users.get[Int]("author-page-size")
      val offset = (page - 1) * pageSize
      users.slice(offset, offset + pageSize)
    }
  }

  implicit val timestampOrdering: Ordering[Timestamp] = (x: Timestamp, y: Timestamp) => x compareTo y

  /**
    * Attempts to find the specified User in the database or creates a new User
    * if one does not exist.
    *
    * @param user User to find
    *
    * @return     Found or new User
    */
  def getOrCreate(user: User): Future[User] = user.schema(this.service).getOrInsert(user)

  /**
    * Creates a new [[Session]] for the specified [[User]].
    *
    * @param user User to create session for
    *
    * @return     Newly created session
    */
  def createSession(user: User)(implicit ec: ExecutionContext): Future[Session] = {
    val maxAge = this.config.play.get[Int]("http.session.maxAge")
    val expiration = new Timestamp(new Date().getTime + maxAge * 1000L)
    val token = UUID.randomUUID().toString
    val session = Session(None, None, expiration, user.username, token)
    this.service.access[Session](classOf[Session]).add(session)
  }

  /**
    * Returns the [[Session]] of the specified token ID. If the session has
    * expired it will be deleted immediately and None will be returned.
    *
    * @param token  Token of session
    * @return       Session if found and has not expired
    */
  private def getSession(token: String)(implicit ec: ExecutionContext): Future[Option[Session]] =
    this.service.access[Session](classOf[Session]).find(_.token === token).map { _.flatMap { session =>
      if (session.hasExpired) {
        session.remove()
        None
      } else
        Some(session)
    }
  }

  /**
    * Returns the currently authenticated User.c
    *
    * @param session  Current session
    * @return         Authenticated user, if any, None otherwise
    */
  def current(implicit session: Request[_], ec: ExecutionContext): Future[Option[User]] = {
    session.cookies.get("_oretoken") match {
      case None => Future.successful(None)
      case Some(cookie) => getSession(cookie.value).flatMap {
          case None => Future.successful(None)
          case Some(s) => s.user
        }
      }
  }

}

object UserBase {

  val ORDERING_PROJECTS = "projects"
  val ORDERING_USERNAME = "username"
  val ORDERING_JOIN_DATE = "joined"
  val ORDERING_ROLE = "roles"

}
