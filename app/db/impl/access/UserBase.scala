package db.impl.access

import java.sql.Timestamp
import java.util.{Date, UUID}

import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectSchema, UserSchema}
import db.{ModelBase, ModelService, ObjectId, ObjectTimestamp}
import models.user.{Session, User}
import ore.OreConfig
import ore.permission.Permission
import play.api.mvc.Request
import security.spauth.SpongeAuthApi
import util.StringUtils._

import scala.concurrent.{ExecutionContext, Future}

import ore.permission.role
import ore.permission.role.RoleType
import cats.data.OptionT
import cats.instances.future._

/**
  * Represents a central location for all Users.
  */
class UserBase(implicit val service: ModelService, config: OreConfig)
  extends ModelBase[User] {

  import UserBase.UserOrdering

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
  def withName(username: String)(implicit ec: ExecutionContext, auth: SpongeAuthApi): OptionT[Future, User] = {
    this.find(equalsIgnoreCase(_.name, username)).orElse {
      auth.getUser(username).map(User.fromSponge).semiflatMap(getOrCreate)
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
  def requestPermission(user: User, name: String, perm: Permission)(implicit ec: ExecutionContext, auth: SpongeAuthApi): OptionT[Future, User] = {
    this.withName(name).flatMap { toCheck =>
      if(user == toCheck) OptionT.pure[Future](user) // Same user
      else toCheck.toMaybeOrganization.flatMap { orga =>
        OptionT.liftF(user can perm in orga).collect {
          case true => toCheck // Has Orga perm
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
  def getAuthors(ordering: String = UserOrdering.Projects, page: Int = 1)(implicit ec: ExecutionContext): Future[Seq[(User, Int)]] = {
    // determine ordering
    val (sort, reverse) = if (ordering.startsWith("-")) (ordering.substring(1), false) else (ordering, true)

    // TODO page and order should be done in Database!
    // get authors
    this.service.getSchema(classOf[ProjectSchema]).distinctAuthors.map { users =>
      sort match { // Sort
        case UserOrdering.Projects => users.sortBy(u => (service.await(u.projects.size).get, u.name))
        case UserOrdering.JoinDate => users.sortBy(u => (u.joinDate.getOrElse(u.createdAt.value), u.name))
        case UserOrdering.UserName => users.sortBy(_.name)
        case UserOrdering.Role => users.sortBy(_.globalRoles.toList.sortBy(_.trust).headOption.map(_.trust.level).getOrElse(-1))
        case _ => users.sortBy(u => (service.await(u.projects.size).get, u.name))
      }
    } map { users => // Reverse?
      if (reverse) users.reverse else users
    } map { users =>
      // get page slice
      val pageSize = this.config.users.get[Int]("author-page-size")
      val offset = (page - 1) * pageSize
      users.slice(offset, offset + pageSize)
    } flatMap { users =>
      Future.sequence(users.map(u => u.projects.size.map((u, _))))
    }
  }

  /**
    * Returns a page of [[User]]s that have Ore staff roles.
    */
  def getStaff(ordering: String = UserOrdering.Role, page: Int = 1)(implicit ec: ExecutionContext): Future[Seq[User]] = {
    // determine ordering
    val (sort, reverse) = if (ordering.startsWith("-")) (ordering.substring(1), false) else (ordering, true)
    val staffRoles: List[RoleType] = List(RoleType.OreAdmin, RoleType.OreMod)

    val pageSize = this.config.users.get[Int]("author-page-size")
    val offset = (page - 1) * pageSize

    val dbio = this.service.getSchema(classOf[UserSchema]).baseQuery
      .filter(u => u.globalRoles.asColumnOf[List[RoleType]] @& staffRoles.bind.asColumnOf[List[RoleType]])
      .sortBy { users =>
        sort match { // Sort
          case UserOrdering.JoinDate => if(reverse) users.joinDate.asc else users.joinDate.desc
          case UserOrdering.Role => if(reverse) users.globalRoles.asc else users.globalRoles.desc
          case _ => if(reverse) users.name.asc else users.joinDate.desc
        }
      }
      .drop(offset)
      .take(pageSize)
      .result

    service.DB.db.run(dbio)
  }

  implicit val timestampOrdering: Ordering[Timestamp] = (x: Timestamp, y: Timestamp) => x compareTo y
  implicit val rolesOrdering: Ordering[List[RoleType]] = (x: List[RoleType], y: List[RoleType]) => {
    def maxOrZero[A, B: Ordering](xs: List[A])(f: A => B, zero: B) = if(xs.isEmpty) zero else xs.map(f).max
    maxOrZero(x)(_.trust, role.Default) compareTo maxOrZero(y)(_.trust, role.Default)
  }

  /**
    * Attempts to find the specified User in the database or creates a new User
    * if one does not exist.
    *
    * @param user User to find
    *
    * @return     Found or new User
    */
  def getOrCreate(user: User)(implicit ec: ExecutionContext): Future[User] = user.schema(this.service).getOrInsert(user)

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
    val session = Session(ObjectId.Uninitialized, ObjectTimestamp.Uninitialized, expiration, user.name, token)
    this.service.access[Session](classOf[Session]).add(session)
  }

  /**
    * Returns the [[Session]] of the specified token ID. If the session has
    * expired it will be deleted immediately and None will be returned.
    *
    * @param token  Token of session
    * @return       Session if found and has not expired
    */
  private def getSession(token: String)(implicit ec: ExecutionContext): OptionT[Future, Session] =
    this.service.access[Session](classOf[Session]).find(_.token === token).subflatMap { session =>
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
  def current(implicit session: Request[_], ec: ExecutionContext, authApi: SpongeAuthApi): OptionT[Future, User] = {
    OptionT.fromOption[Future](session.cookies.get("_oretoken"))
      .flatMap(cookie => getSession(cookie.value))
      .flatMap(_.user)
  }

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
