package models.project

import java.sql.Timestamp
import java.time.Instant

import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.twirl.api.Html

import db.access.{ModelAccess, ModelAssociationAccess}
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.{Describable, Downloadable, Hideable}
import db.impl.schema.{
  ProjectMembersTable,
  ProjectRoleTable,
  ProjectSchema,
  ProjectStarsTable,
  ProjectTable,
  ProjectWatchersTable
}
import db.{Model, ModelService, Named, ObjectId, ObjectReference, ObjectTimestamp}
import models.admin.{ProjectLog, ProjectVisibilityChange}
import models.api.ProjectApiKey
import models.project.Visibility.Public
import models.statistic.ProjectView
import models.user.User
import models.user.role.ProjectRole
import ore.permission.role.RoleType
import ore.permission.scope.ProjectScope
import ore.project.{Category, FlagReason, ProjectMember}
import ore.user.MembershipDossier
import ore.{Joinable, OreConfig, Visitable}
import _root_.util.StringUtils
import _root_.util.StringUtils._

import cats.instances.future._
import cats.syntax.all._
import com.google.common.base.Preconditions._
import slick.lifted
import slick.lifted.{Rep, TableQuery}

/**
  * Represents an Ore package.
  *
  * <p>Note: As a general rule, do not handle actions / results in model classes</p>
  *
  * @param id                     Unique identifier
  * @param createdAt              Instant of creation
  * @param pluginId               Plugin ID
  * @param ownerName              The owner Author for this project
  * @param ownerId                User ID of Project owner
  * @param name                   Name of plugin
  * @param slug                   URL slug
  * @param recommendedVersionId   The ID of this project's recommended version
  * @param starCount                  Star count
  * @param viewCount                  View count
  * @param downloadCount              How many times this project has been downloaded in total
  * @param topicId                ID of forum topic
  * @param postId                 ID of forum topic post ID
  * @param isTopicDirty           Whether this project's forum topic needs to be updated
  * @param visibility             Whether this project is visible to the default user
  * @param lastUpdated            Instant of last version release
  * @param notes                  JSON notes
  */
case class Project(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    pluginId: String,
    ownerName: String,
    ownerId: ObjectReference,
    name: String,
    slug: String,
    recommendedVersionId: Option[ObjectReference] = None,
    category: Category = Category.Undefined,
    description: Option[String] = None,
    starCount: Long = 0,
    viewCount: Long = 0,
    downloadCount: Long = 0,
    topicId: Option[Int] = None,
    postId: Option[Int] = None,
    isTopicDirty: Boolean = false,
    visibility: Visibility = Public,
    lastUpdated: Timestamp = null,
    notes: String = ""
) extends Model
    with ProjectScope
    with Downloadable
    with Named
    with Describable
    with Hideable
    with Joinable[ProjectMember, Project]
    with Visitable {

  def this(pluginId: String, name: String, owner: String, ownerId: ObjectReference) = {
    this(pluginId = pluginId, name = compact(name), slug = slugify(name), ownerName = owner, ownerId = ownerId)
  }

  override type M                     = Project
  override type T                     = ProjectTable
  override type S                     = ProjectSchema
  override type ModelVisibilityChange = ProjectVisibilityChange

  /**
    * Contains all information for [[User]] memberships.
    */
  override def memberships(
      implicit ec: ExecutionContext,
      service: ModelService
  ): MembershipDossier.Aux[Future, Project, ProjectRole, ProjectMember] =
    MembershipDossier[Future, Project]

  def isOwner(user: User): Boolean = user.id.value == ownerId

  /**
    * Returns the owner [[ProjectMember]] of this project.
    *
    * @return Owner Member of project
    */
  override def owner(implicit service: ModelService): ProjectMember = new ProjectMember(this, this.ownerId)

  override def transferOwner(
      member: ProjectMember
  )(implicit ex: ExecutionContext, service: ModelService): Future[Project] = {
    // Down-grade current owner to "Developer"
    for {
      (owner, user)           <- this.owner.user.zip(member.user)
      (ownerRoles, userRoles) <- this.memberships.getRoles(this, owner).zip(this.memberships.getRoles(this, user))
      setOwner                <- this.setOwner(user)
      _ <- Future.sequence(
        ownerRoles
          .filter(_.roleType == RoleType.ProjectOwner)
          .map(role => service.update(role.copy(roleType = RoleType.ProjectDeveloper)))
      )
      _ <- Future.sequence(
        userRoles.map(role => service.update(role.copy(roleType = RoleType.ProjectOwner)))
      )
    } yield setOwner
  }

  /**
    * Sets the [[User]] that owns this Project.
    *
    * @param user User that owns project
    */
  def setOwner(user: User)(implicit ec: ExecutionContext, service: ModelService): Future[Project] = {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    service.update(
      copy(
        ownerId = user.id.value,
        ownerName = user.name
      )
    )
  }

  /**
    * Returns ModelAccess to the user's who are watching this project.
    *
    * @return Users watching project
    */
  def watchers(implicit service: ModelService): ModelAssociationAccess[ProjectWatchersTable, User] =
    this.schema.getAssociation[ProjectWatchersTable, User](classOf[ProjectWatchersTable], this)

  def namespace: String = this.ownerName + '/' + this.slug

  /**
    * Returns the base URL for this Project.
    *
    * @return Base URL for project
    */
  override def url: String = this.ownerName + '/' + this.slug

  /**
    * Returns this [[Project]]'s [[ProjectSettings]].
    *
    * @return Project settings
    */
  def settings(implicit ec: ExecutionContext, service: ModelService): Future[ProjectSettings] =
    service
      .access[ProjectSettings](classOf[ProjectSettings])
      .find(_.projectId === this.id.value)
      .getOrElse(throw new NoSuchElementException("Get on None"))

  /**
    * Sets this [[Project]]'s [[ProjectSettings]].
    *
    * @param settings Project settings
    * @return         Newly created settings instance
    */
  def updateSettings(
      settings: ProjectSettings
  )(implicit ec: ExecutionContext, service: ModelService): Future[ProjectSettings] = Defined {
    checkNotNull(settings, "null settings", "")
    val newSettings = settings.copy(projectId = this.id.value)
    if (settings.isDefined) service.update(newSettings)
    else service.insert(newSettings)
  }

  /**
    * Sets whether this project is visible.
    *
    * @param visibility True if visible
    */
  def setVisibility(visibility: Visibility, comment: String, creator: ObjectReference)(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[(Project, ProjectVisibilityChange)] = {
    val updateOldChange = lastVisibilityChange
      .semiflatMap { vc =>
        service.update(
          vc.copy(
            resolvedAt = Some(Timestamp.from(Instant.now())),
            resolvedBy = Some(creator)
          )
        )
      }
      .cata((), _ => ())

    val createNewChange = service
      .access(classOf[ProjectVisibilityChange])
      .add(
        ProjectVisibilityChange(
          ObjectId.Uninitialized,
          ObjectTimestamp(Timestamp.from(Instant.now())),
          Some(creator),
          this.id.value,
          comment,
          None,
          None,
          visibility
        )
      )

    val updateProject = service.update(
      copy(
        visibility = visibility
      )
    )

    updateOldChange *> (updateProject, createNewChange).tupled
  }

  /**
    * Get VisibilityChanges
    */
  override def visibilityChanges(implicit service: ModelService): ModelAccess[ProjectVisibilityChange] =
    this.schema.getChildren[ProjectVisibilityChange](classOf[ProjectVisibilityChange], this)

  /**
    * Returns [[db.access.ModelAccess]] to [[User]]s who have starred this
    * project.
    *
    * @return Users who have starred this project
    */
  def stars(implicit service: ModelService): ModelAssociationAccess[ProjectStarsTable, User] =
    Defined(this.schema.getAssociation[ProjectStarsTable, User](classOf[ProjectStarsTable], this))

  /**
    * Sets the "starred" state of this Project for the specified User.
    *
    * @param user User to set starred state of
    * @param starred True if should star
    */
  def setStarredBy(
      user: User,
      starred: Boolean
  )(implicit ec: ExecutionContext, service: ModelService): Future[Project] = Defined {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    for {
      contains <- this.stars.contains(user)
      res <- if (starred) {
        if (!contains) {
          this.stars.add(user) *> service.update(copy(starCount = starCount + 1))
        } else Future.successful(this)
      } else if (contains) {
        this.stars.remove(user) *> service.update(copy(starCount = starCount - 1))
      } else Future.successful(this)
    } yield res
  }

  /**
    * Returns the record of unique Project views.
    *
    * @return Unique project views
    */
  def views(implicit service: ModelService): ModelAccess[ProjectView] =
    this.schema.getChildren[ProjectView](classOf[ProjectView], this)

  /**
    * Adds a view to this Project.
    */
  def addView(implicit ec: ExecutionContext, service: ModelService): Future[Project] =
    service.update(copy(viewCount = viewCount + 1))

  /**
    * Increments this Project's downloadc count by one.
    *
    * @return Future result
    */
  def addDownload(implicit ec: ExecutionContext, service: ModelService): Future[Project] =
    service.update(copy(downloadCount = downloadCount + 1))

  /**
    * Returns all flags on this project.
    *
    * @return Flags on project
    */
  def flags(implicit service: ModelService): ModelAccess[Flag] = this.schema.getChildren[Flag](classOf[Flag], this)

  /**
    * Submits a flag on this project for the specified user.
    *
    * @param user   Flagger
    * @param reason Reason for flagging
    */
  def flagFor(user: User, reason: FlagReason, comment: String)(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[Flag] = Defined {
    checkNotNull(user, "null user", "")
    checkNotNull(reason, "null reason", "")
    checkArgument(user.isDefined, "undefined user", "")
    val userId = user.id.value
    checkArgument(userId != this.ownerId, "cannot flag own project", "")
    service.access[Flag](classOf[Flag]).add(new Flag(this.id.value, user.id.value, reason, comment))
  }

  /**
    * Returns the Channels in this Project.
    *
    * @return Channels in project
    */
  def channels(implicit service: ModelService): ModelAccess[Channel] =
    this.schema.getChildren[Channel](classOf[Channel], this)

  /**
    * Returns all versions in this project.
    *
    * @return Versions in project
    */
  def versions(implicit service: ModelService): ModelAccess[Version] =
    this.schema.getChildren[Version](classOf[Version], this)

  /**
    * Returns this Project's recommended version.
    *
    * @return Recommended version
    */
  def recommendedVersion(implicit ec: ExecutionContext, service: ModelService): Future[Version] =
    this.versions
      .get(
        this.recommendedVersionId
          .getOrElse(throw new NoSuchElementException(s"Tried to get non-existant recommended version for $name"))
      )
      .getOrElse(
        throw new NoSuchElementException(
          s"Tried to get non-existant recommended version for $name and id ${recommendedVersionId.get}"
        )
      )

  /**
    * Returns the pages in this Project.
    *
    * @return Pages in project
    */
  def pages(implicit service: ModelService): ModelAccess[Page] = this.schema.getChildren[Page](classOf[Page], this)

  /**
    * Returns this Project's home page.
    *
    * @return Project home page
    */
  def homePage(implicit ec: ExecutionContext, service: ModelService, config: OreConfig): Page = Defined {
    val page = new Page(this.id.value, Page.homeName, Page.template(this.name, Page.homeMessage), false, None)
    service.await(page.schema.getOrInsert(page)).get
  }

  /**
    * Returns true if a page with the specified name exists.
    *
    * @param name   Page name
    * @return       True if exists
    */
  def pageExists(name: String)(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] =
    this.pages.exists(_.name === name)

  /**
    * Returns the specified Page or creates it if it doesn't exist.
    *
    * @param name   Page name
    * @return       Page with name or new name if it doesn't exist
    */
  def getOrCreatePage(
      name: String,
      parentId: Option[ObjectReference],
      content: Option[String] = None
  )(implicit ec: ExecutionContext, config: OreConfig, service: ModelService): Future[Page] = Defined {
    checkNotNull(name, "null name", "")
    val c = content match {
      case None => Page.template(name, Page.homeMessage)
      case Some(text) =>
        checkNotNull(text, "null contents", "")
        checkArgument(text.length <= Page.maxLengthPage, "contents too long", "")
        text
    }
    val page = new Page(this.id.value, name, c, true, parentId)
    page.schema.getOrInsert(page)
  }

  /**
    * Returns the parentless, root, pages for this project.
    *
    * @return Root pages of project
    */
  def rootPages(implicit service: ModelService): Future[Seq[Page]] =
    service.access[Page](classOf[Page]).sorted(_.name, p => p.projectId === this.id.value && p.parentId === -1L)

  def logger(implicit ec: ExecutionContext, service: ModelService): Future[ProjectLog] = {
    val loggers = service.access[ProjectLog](classOf[ProjectLog])
    loggers.find(_.projectId === this.id.value).getOrElseF(loggers.add(ProjectLog(projectId = this.id.value)))
  }

  def apiKeys(implicit service: ModelService): ModelAccess[ProjectApiKey] =
    this.schema.getChildren[ProjectApiKey](classOf[ProjectApiKey], this)

  override def projectId: ObjectReference = this.id.value
  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Project =
    this.copy(id = id, createdAt = theTime, lastUpdated = theTime.value)

  /**
    * Add new note
    */
  def addNote(message: Note)(implicit ec: ExecutionContext, service: ModelService): Future[Project] = {
    val messages = decodeNotes :+ message
    val js       = Json.toJson(messages)
    service.update(
      copy(
        notes = Json.stringify(
          JsObject(
            Seq(
              "messages" -> js
            )
          )
        )
      )
    )
  }

  /**
    * Get all messages
    * @return
    */
  def decodeNotes: Seq[Note] = {
    if (this.notes.startsWith("{") && notes.endsWith("}")) {
      val messages: JsValue = Json.parse(notes)
      (messages \ "messages").as[Seq[Note]]
    } else {
      Seq()
    }
  }
}

/**
  * This modal is needed to convert the json
  */
case class Note(message: String, user: ObjectReference, time: Long = System.currentTimeMillis()) {
  def printTime(implicit oreConfig: Messages): String = StringUtils.prettifyDateAndTime(new Timestamp(time))
  def render(implicit oreConfig: OreConfig): Html     = Page.render(message)
}
object Note {
  implicit val noteWrites: Writes[Note] = (note: Note) =>
    Json.obj(
      "message" -> note.message,
      "user"    -> note.user,
      "time"    -> note.time
  )

  implicit val notesRead: Reads[Note] =
    (JsPath \ "message")
      .read[String]
      .and((JsPath \ "user").read[ObjectReference])
      .and((JsPath \ "time").read[Long])(Note.apply _)
}

object Project {

  private def queryRoleForTrust(projectId: Rep[ObjectReference], userId: Rep[ObjectReference]) = {
    val q = for {
      m <- TableQuery[ProjectMembersTable] if m.projectId === projectId && m.userId === userId
      r <- TableQuery[ProjectRoleTable] if m.userId === r.userId && r.projectId === projectId
    } yield r.roleType
    q.to[Set]
  }

  lazy val roleForTrustQuery = lifted.Compiled(queryRoleForTrust _)

  /**
    * Helper class for easily building new Projects.
    *
    * @param service ModelService to process with
    */
  case class Builder(service: ModelService) {

    private var pluginId: String         = _
    private var ownerName: String        = _
    private var ownerId: ObjectReference = -1
    private var name: String             = _
    private var visibility: Visibility   = _

    def pluginId(pluginId: String): Builder = {
      this.pluginId = pluginId
      this
    }

    def ownerName(ownerName: String): Builder = {
      this.ownerName = ownerName
      this
    }

    def ownerId(ownerId: ObjectReference): Builder = {
      this.ownerId = ownerId
      this
    }

    def name(name: String): Builder = {
      this.name = name
      this
    }

    def visibility(visibility: Visibility): Builder = {
      this.visibility = visibility
      this
    }

    def build(): Project = {
      checkNotNull(this.pluginId, "plugin id null", "")
      checkNotNull(this.ownerName, "owner name null", "")
      checkNotNull(this.name, "name null", "")
      checkArgument(this.ownerId != -1, "invalid owner id", "")
      Project(
        pluginId = this.pluginId,
        ownerName = this.ownerName,
        ownerId = this.ownerId,
        name = this.name,
        slug = slugify(this.name),
        visibility = this.visibility
      )
    }

  }

}
