package db.impl.access

import java.sql.Timestamp
import java.util.{Date, UUID}

import db.impl.OrePostgresDriver.api._
import db.impl.schema.ProjectSchema
import db.{ModelBase, ModelService}
import models.user.{Session, User}
import ore.OreConfig
import play.api.mvc.Request
import security.spauth.SpongeAuthApi
import util.StringUtils._

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
  def withName(username: String): Option[User] = {
    this.find(equalsIgnoreCase(_.name, username)).orElse {
      this.auth.getUser(username).map(u => getOrCreate(User.fromSponge(u)))
    }
  }

  /**
    * Returns a page of [[User]]s with at least one [[models.project.Project]].
    *
    * FIXME: Ordering is messed up
    *
    * @return Users with at least one project
    */
  def getAuthors(ordering: String = ORDERING_PROJECTS, page: Int = 1): Seq[User] = {
    // determine ordering
    var sort = ordering
    val reverse = if (sort.startsWith("-")) {
      sort = sort.substring(1)
      false
    } else
      true

    // get authors
    var users: Seq[User] = this.service.await {
      this.service.getSchema(classOf[ProjectSchema]).distinctAuthors
    }.get

    // sort
    sort match {
      case ORDERING_PROJECTS => users = users.sortBy(u => (u.projects.size, u.username))
      case ORDERING_JOIN_DATE => users = users.sortBy(u => (u.joinDate.getOrElse(u.createdAt.get), u.username))
      case ORDERING_USERNAME => users = users.sortBy(_.username)
      case ORDERING_ROLE => users = users.sortBy(_.globalRoles.toList.sortBy(_.trust).headOption.map(_.trust.level).getOrElse(-1))
      case _ => users.sortBy(u => (u.projects.size, u.username))
    }

    // get page slice
    val pageSize = this.config.users.getInt("author-page-size").get
    val offset = (page - 1) * pageSize
    users = if (reverse) users.reverse else users
    users.slice(offset, offset + pageSize)
  }

  implicit val timestampOrdering: Ordering[Timestamp] = new Ordering[Timestamp] {
    def compare(x: Timestamp, y: Timestamp) = x compareTo y
  }

  /**
    * Attempts to find the specified User in the database or creates a new User
    * if one does not exist.
    *
    * @param user User to find
    * @return     Found or new User
    */
  def getOrCreate(user: User): User = this.service.await(user.schema(this.service).getOrInsert(user)).get

  /**
    * Creates a new [[Session]] for the specified [[User]].
    *
    * @param user User to create session for
    * @return     Newly created session
    */
  def createSession(user: User): Session = {
    val maxAge = this.config.play.getInt("http.session.maxAge").get
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
  def getSession(token: String): Option[Session] = {
    this.service.access[Session](classOf[Session]).find(_.token === token).flatMap { session =>
      if (session.hasExpired) {
        session.remove()
        None
      } else
        Some(session)
    }
  }

  /**
    * Returns the currently authenticated User.
    *
    * @param session  Current session
    * @return         Authenticated user, if any, None otherwise
    */
  def current(implicit session: Request[_]): Option[User] = session.cookies.get("_oretoken").flatMap { token =>
    getSession(token.value).map(_.user)
  }

}

object UserBase {

  val ORDERING_PROJECTS = "projects"
  val ORDERING_USERNAME = "username"
  val ORDERING_JOIN_DATE = "joined"
  val ORDERING_ROLE = "roles"

}
