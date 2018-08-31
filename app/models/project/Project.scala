package models.project

import java.sql.Timestamp
import java.time.Instant

import _root_.util.StringUtils
import _root_.util.StringUtils._
import _root_.util.instances.future._
import _root_.util.functional.OptionT
import com.google.common.base.Preconditions._

import db.access.{ModelAccess, ModelAssociationAccess}
import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.model.OreModel
import db.impl.model.common.{Describable, Downloadable, Hideable, VisibilityChange}
import db.impl.schema.ProjectSchema
import db.impl.table.ModelKeys
import db.impl.table.ModelKeys._
import db.{ModelService, Named}
import models.admin.{ProjectLog, ProjectVisibilityChange}
import models.api.ProjectApiKey
import models.project.VisibilityTypes.{Public, Visibility}
import models.statistic.ProjectView
import models.user.User
import models.user.role.ProjectRole
import ore.permission.role.{Default, RoleTypes, Trust}
import ore.permission.scope.ProjectScope
import ore.project.Categories.Category
import ore.project.FlagReasons.FlagReason
import ore.project.{Categories, ProjectMember}
import ore.user.MembershipDossier
import ore.{Joinable, OreConfig, Visitable}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.twirl.api.Html
import slick.lifted
import slick.lifted.{Rep, TableQuery}
import scala.concurrent.{ExecutionContext, Future}

import play.api.i18n.Messages

/**
  * Represents an Ore package.
  *
  * <p>Note: As a general rule, do not handle actions / results in model classes</p>
  *
  * @param id                     Unique identifier
  * @param createdAt              Instant of creation
  * @param pluginId               Plugin ID
  * @param _ownerName             The owner Author for this project
  * @param _ownerId               User ID of Project owner
  * @param _name                  Name of plugin
  * @param _slug                  URL slug
  * @param recommendedVersionId   The ID of this project's recommended version
  * @param _stars                 Star count
  * @param _views                 View count
  * @param _downloads             How many times this project has been downloaded in total
  * @param _topicId               ID of forum topic
  * @param _postId                ID of forum topic post ID
  * @param _isTopicDirty          Whether this project's forum topic needs to be updated
  * @param _visibility            Whether this project is visible to the default user
  * @param _lastUpdated           Instant of last version release
  * @param _notes                 JSON notes
  */
case class Project(override val id: Option[Int] = None,
                   override val createdAt: Option[Timestamp] = None,
                   pluginId: String,
                   private var _ownerName: String,
                   private var _ownerId: Int,
                   private var _name: String,
                   private var _slug: String,
                   var recommendedVersionId: Option[Int] = None,
                   private var _category: Category = Categories.Undefined,
                   private var _description: Option[String] = None,
                   private var _stars: Int = 0,
                   private var _views: Int = 0,
                   private var _downloads: Int = 0,
                   private var _topicId: Int = -1,
                   private var _postId: Int = -1,
                   private var _isTopicDirty: Boolean = false,
                   private var _visibility: Visibility = Public,
                   private var _lastUpdated: Timestamp = null,
                   var _notes: String = "")
                   extends OreModel(id, createdAt)
                     with ProjectScope
                     with Downloadable
                     with Named
                     with Describable
                     with Hideable
                     with Joinable[ProjectMember]
                  with Visitable
{





  override type M = Project
  override type T = ProjectTable
  override type S = ProjectSchema
  override type ModelVisibilityChange = ProjectVisibilityChange

  /**
    * Contains all information for [[User]] memberships.
    */
  override val memberships: MembershipDossier {
  type MembersTable = ProjectMembersTable

  type MemberType = ProjectMember

  type RoleTable = ProjectRoleTable

  type ModelType = Project

  type RoleType = ProjectRole
} = new MembershipDossier {

    type ModelType = Project
    type RoleType = ProjectRole
    type RoleTable = ProjectRoleTable
    type MemberType = ProjectMember
    type MembersTable = ProjectMembersTable

    val membersTableClass: Class[MembersTable] = classOf[ProjectMembersTable]
    val roleClass: Class[RoleType] = classOf[ProjectRole]
    val model: ModelType = Project.this

    def newMember(userId: Int)(implicit ec: ExecutionContext): MemberType = new ProjectMember(this.model, userId)


    /**
      * Returns the highest level of [[ore.permission.role.Trust]] this user has.
      *
      * @param user User to get trust of
      * @return Trust of user
      */
    override def getTrust(user: User)(implicit ex: ExecutionContext): Future[Trust] = {
      this.userBase.service.DB.db.run(Project.roleForTrustQuery(id.get, user.id.get).result).map { l =>
        val ordering: Ordering[ProjectRole] = Ordering.by(m => m.roleType.trust)
        l.sorted(ordering).headOption.map(_.roleType.trust).getOrElse(Default)
      }
    }

    def clearRoles(user: User): Future[Int] = this.roleAccess.removeAll({ s => (s.userId === user.id.get) && (s.projectId === id.get) })

  }

  def this(pluginId: String, name: String, owner: String, ownerId: Int) = {
    this(pluginId=pluginId, _name=compact(name), _slug=slugify(name), _ownerName=owner, _ownerId=ownerId)
  }

  def isOwner(user: User) : Boolean = user.id.contains(_ownerId)

  /**
    * Returns the ID of the [[User]] that owns this Project.
    *
    * @return ID of user
    */
  def ownerId: Int = this._ownerId

  /**
    * Returns the name of the [[User]] that owns this Project.
    *
    * @return Name of user that owns project
    */
  def ownerName: String = this._ownerName

  /**
    * Returns the owner [[ProjectMember]] of this project.
    *
    * @return Owner Member of project
    */
  override def owner: ProjectMember = new ProjectMember(this, this.ownerId)

  override def transferOwner(member: ProjectMember)(implicit ex: ExecutionContext): Future[Int] = {
    // Down-grade current owner to "Developer"
    for {
      owner <- this.owner.user
      ownerRoles <- this.memberships.getRoles(owner)
      user <- member.user
      userRoles <- this.memberships.getRoles(user)
      setOwner <- this.setOwner(user)
    } yield {
      ownerRoles.filter(_.roleType == RoleTypes.ProjectOwner)
        .foreach(_.setRoleType(RoleTypes.ProjectDev))

      userRoles.foreach(_.setRoleType(RoleTypes.ProjectOwner))

      setOwner
    }
  }

  /**
    * Sets the [[User]] that owns this Project.
    *
    * @param user User that owns project
    */
  def setOwner(user: User): Future[Int] = {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    this._ownerId = user.id.get
    this._ownerName = user.name
    if (isDefined) {
      update(OwnerId)
      update(OwnerName)
      // TODO one update
    }
    Future.successful(0)
  }

  /**
    * Returns ModelAccess to the user's who are watching this project.
    *
    * @return Users watching project
    */
  def watchers: ModelAssociationAccess[ProjectWatchersTable, User] = this.schema.getAssociation[ProjectWatchersTable, User](classOf[ProjectWatchersTable], this)

  /**
    * Returns the name of this Project.
    *
    * @return Name of project
    */
  override def name: String = this._name

  /**
    * Sets the name of this project.
    *
    * @param _name New name
    */
  def setName(_name: String) = {
    checkNotNull(_name, "null name", "")
    this._name = _name
    if (isDefined) update(Name)
  }

  /**
    * Returns this Project's URL slug.
    *
    * @return URL slug
    */
  def slug: String = this._slug

  /**
    * Sets the URL slug of this project.
    *
    * @param _slug New slug
    */
  def setSlug(_slug: String) = {
    checkNotNull(_slug, "null slug", "")
    this._slug = _slug
    if (isDefined) update(Slug)
  }

  def namespace: String = this.ownerName + '/' + this.slug

  /**
    * Returns the base URL for this Project.
    *
    * @return Base URL for project
    */
  override def url: String = this.ownerName + '/' + this.slug

  /**
    * Returns this Project's [[Category]].
    *
    * @return Project category
    */
  def category: Category = this._category

  /**
    * Sets this Project's [[Category]].
    *
    * @param category Project category
    */
  def setCategory(category: Category) = {
    checkNotNull(category, "null category", "")
    this._category = category
    if (isDefined) update(ModelKeys.Category)
  }

  /**
    * Returns this Project's description.
    *
    * @return Project description
    */
  override def description: Option[String] = this._description

  /**
    * Sets this Project's description.
    *
    * @param description Project description.
    */
  def setDescription(description: String) = {
    this._description = Option(description)
    if (isDefined) update(Description)
  }

  /**
    * Returns this [[Project]]'s [[ProjectSettings]].
    *
    * @return Project settings
    */
  def settings(implicit ec: ExecutionContext): Future[ProjectSettings]
  = this.service.access[ProjectSettings](classOf[ProjectSettings]).find(_.projectId === this.id.get).getOrElse(throw new NoSuchElementException("Get on None"))

  /**
    * Sets this [[Project]]'s [[ProjectSettings]].
    *
    * @param settings Project settings
    * @return         Newly created settings instance
    */
  def updateSettings(settings: ProjectSettings)(implicit ec: ExecutionContext): Future[ProjectSettings] = Defined {
    checkNotNull(settings, "null settings", "")
    val access = this.service.access[ProjectSettings](classOf[ProjectSettings])
    val id = this.id.get
    for {
      // Delete previous settings
      _ <- access.removeAll(_.projectId === id)
      // Add new settings
      newSettings <- access.add(settings.copy(projectId = id))
    } yield {
      newSettings
    }

  }

  override def visibilityChanges: ModelAccess[ProjectVisibilityChange] =
    this.schema.getChildren[ProjectVisibilityChange](classOf[ProjectVisibilityChange], this)

  /**
    * Returns true if this Project is visible.
    *
    * @return True if visible
    */
  override def visibility: Visibility = this._visibility

  override def setVisibility(visibility: Visibility, comment: String, creator: Int)(implicit ec: ExecutionContext): Future[ProjectVisibilityChange] = {
    this._visibility = visibility
    if (isDefined) update(ModelKeys.Visibility)

    val cnt = lastVisibilityChange.fold(0) { vc =>
        vc.setResolvedAt(Timestamp.from(Instant.now()))
        vc.setResolvedById(creator)
        0
    }
    cnt.flatMap { _ =>
      val change = ProjectVisibilityChange(None, Some(Timestamp.from(Instant.now())), Some(creator), this.id.get, comment, None, None, visibility.id)
      this.service.access[ProjectVisibilityChange](classOf[ProjectVisibilityChange]).add(change)
    }
  }

  /**
    * Returns the last time this [[Project]] was updated.
    *
    * @return Last time project was updated
    */
  def lastUpdated: Timestamp = this._lastUpdated

  /**
    * Sets the last time this [[Project]] was updated.
    *
    * @param lastUpdated Last time project was updated
    */
  def setLastUpdated(lastUpdated: Timestamp) = {
    checkNotNull(lastUpdated, "null timestamp", "")
    this._lastUpdated = lastUpdated
    if (isDefined) update(LastUpdated)
  }

  /**
    * Returns [[db.access.ModelAccess]] to [[User]]s who have starred this
    * project.
    *
    * @return Users who have starred this project
    */
  def stars: ModelAssociationAccess[ProjectStarsTable, User] = Defined(this.schema.getAssociation[ProjectStarsTable, User](classOf[ProjectStarsTable], this))

  /**
    * Returns the amount of stars this [[Project]] has.
    *
    * @return Amount of stars
    */
  def starCount: Int = this._stars

  /**
    * Sets the "starred" state of this Project for the specified User.
    *
    * @param user User to set starred state of
    * @param starred True if should star
    */
  def setStarredBy(user: User, starred: Boolean)(implicit ec: ExecutionContext): Future[Future[Int]] = Defined {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    for {
      contains <- this.stars.contains(user)
    } yield {
      if (starred) {
        if (!contains) {
          this.stars.add(user)
          this._stars += 1
        }
      } else if (contains) {
        this.stars.remove(user)
        this._stars -= 1
      }
      update(Stars)
    }
  }

  /**
    * Returns the record of unique Project views.
    *
    * @return Unique project views
    */
  def views: ModelAccess[ProjectView] = this.schema.getChildren[ProjectView](classOf[ProjectView], this)

  /**
    * Returns the amount of views this project has.
    *
    * @return Amount of views
    */
  def viewCount: Int = this._views

  /**
    * Adds a view to this Project.
    */
  def addView() = {
    this._views += 1
    if (isDefined) update(Views)
  }

  /**
    * Returns the amount of unique downloads this Project has.
    *
    * @return Amount of unique downloads
    */
  override def downloadCount: Int = this._downloads

  /**
    * Increments this Project's downloadc count by one.
    *
    * @return Future result
    */
  def addDownload() = {
    this._downloads += 1
    if (isDefined) update(Downloads)
  }

  /**
    * Returns all flags on this project.
    *
    * @return Flags on project
    */
  def flags: ModelAccess[Flag] = this.schema.getChildren[Flag](classOf[Flag], this)

  /**
    * Submits a flag on this project for the specified user.
    *
    * @param user   Flagger
    * @param reason Reason for flagging
    */
  def flagFor(user: User, reason: FlagReason, comment: String)(implicit ec: ExecutionContext): Future[Flag] = Defined {
    checkNotNull(user, "null user", "")
    checkNotNull(reason, "null reason", "")
    checkArgument(user.isDefined, "undefined user", "")
    val userId = user.id.get
    checkArgument(userId != this.ownerId, "cannot flag own project", "")
    this.service.access[Flag](classOf[Flag]).add(new Flag(this.id.get, user.id.get, reason, comment))
  }

  /**
    * Returns the Channels in this Project.
    *
    * @return Channels in project
    */
  def channels: ModelAccess[Channel] = this.schema.getChildren[Channel](classOf[Channel], this)

  /**
    * Returns all versions in this project.
    *
    * @return Versions in project
    */
  def versions: ModelAccess[Version] = this.schema.getChildren[Version](classOf[Version], this)

  /**
    * Returns this Project's recommended version.
    *
    * @return Recommended version
    */
  def recommendedVersion(implicit ec: ExecutionContext): Future[Version] =
    this.versions.get(this.recommendedVersionId.get).getOrElse(throw new NoSuchElementException("Get on None"))

  /**
    * Updates this project's recommended version.
    *
    * @param _version  Version to set
    * @return         Result
    */
  def setRecommendedVersion(_version: Version): Future[AnyVal] = {
    checkNotNull(_version, "null version", "")
    checkArgument(_version.isDefined, "undefined version", "")
    this.recommendedVersionId = _version.id
    if (isDefined) update(RecommendedVersionId) else Future.unit
  }

  /**
    * Get a collection of tags that represent a project through its versions
    */
  def tags(implicit ec: ExecutionContext, service: ModelService): Future[Seq[Tag]] = {
    schema(service)
    // get all the versions for the project
    this.service.access(classOf[Version]).filter(_.projectId === id.get).flatMap { versions =>
      val tagIds = versions.flatMap(_.tagIds).distinct
      // get all the tags for all the versions
      this.service.access(classOf[Tag]).filter(t => t.id inSet tagIds).map { list =>
        list.distinct
          // get the latest tag from the versions
          .groupBy(_.name)
          .map { case (_, tags) =>
            tags.maxBy { tag =>
              versions
                .filter(_.tagIds.contains(tag.id.get))
                .filter(!_.isDeleted)
                // get the latest version
                .map(_.createdAt.get.toInstant.toEpochMilli)
                .max
            }
          }
          .toSeq
      }
    }
  }

  /**
    * Returns the pages in this Project.
    *
    * @return Pages in project
    */
  def pages: ModelAccess[Page] = this.schema.getChildren[Page](classOf[Page], this)

  /**
    * Returns this Project's home page.
    *
    * @return Project home page
    */
  def homePage(implicit ec: ExecutionContext): Page = Defined {
    val page = new Page(this.id.get, Page.HomeName, Page.Template(this.name, Page.HomeMessage), false, -1)
    this.service.await(page.schema.getOrInsert(page)).get
  }

  /**
    * Returns true if a page with the specified name exists.
    *
    * @param name   Page name
    * @return       True if exists
    */
  def pageExists(name: String)(implicit ec: ExecutionContext): Future[Boolean] = this.pages.exists(_.name === name)

  /**
    * Returns the specified Page or creates it if it doesn't exist.
    *
    * @param name   Page name
    * @return       Page with name or new name if it doesn't exist
    */
  def getOrCreatePage(name: String, parentId: Int = -1, content: Option[String] = None)(implicit ec: ExecutionContext): Future[Page] = Defined {
    checkNotNull(name, "null name", "")
    val c = content match {
      case None => Page.Template(name, Page.HomeMessage)
      case Some(text) =>
        checkNotNull(text, "null contents", "")
        checkArgument(text.length <= Page.MaxLengthPage, "contents too long", "")
        text
    }
    val page = new Page(this.id.get, name, c, true, parentId)
    page.schema.getOrInsert(page)
  }

  /**
    * Returns the parentless, root, pages for this project.
    *
    * @return Root pages of project
    */
  def rootPages(implicit ec: ExecutionContext): Future[Seq[Page]] = {
    this.service.access[Page](classOf[Page]).sorted(_.name, p => p.projectId === this.id.get && p.parentId === -1)
  }

  def logger(implicit ec: ExecutionContext): Future[ProjectLog] = {
    val loggers = this.service.access[ProjectLog](classOf[ProjectLog])
    loggers.find(_.projectId === this.id.get).getOrElseF(loggers.add(ProjectLog(projectId = this.id.get)))
  }

  /**
    * Returns the forum topic ID for this Project.
    *
    * @return Forum topic ID
    */
  def topicId: Int = this._topicId

  /**
    * Sets the forum topic ID for this Project.
    *
    * @param _topicId ID to set
    */
  def setTopicId(_topicId: Int): Future[Int] = Defined {
    this._topicId = _topicId
    update(TopicId)
  }

  /**
    * Returns the forum post ID for this Project.
    *
    * @return Forum post ID
    */
  def postId: Int = this._postId

  /**
    * Sets the forum post ID for this Project.
    *
    * @param _postId Forum post ID
    */
  def setPostId(_postId: Int): Future[Int] = Defined {
    this._postId = _postId
    update(PostId)
  }

  /**
    * Returns true if this Project's topic is out of sync with the forums.
    *
    * @return True if topic out of sync
    */
  def isTopicDirty: Boolean = this._isTopicDirty

  /**
    * Sets whether this Project's topic is out of sync with the forums and
    * needs an update.
    *
    * @param topicDirty True if topic is dirty
    */
  def setTopicDirty(topicDirty: Boolean): Future[Int] = Defined {
    this._isTopicDirty = topicDirty
    update(IsTopicDirty)
  }

  def apiKeys: ModelAccess[ProjectApiKey] = this.schema.getChildren[ProjectApiKey](classOf[ProjectApiKey], this)

  override def projectId: Int = Defined(this.id.get)
  override def copyWith(id: Option[Int], theTime: Option[Timestamp]): Project
  = this.copy(id = id, createdAt = theTime, _lastUpdated = theTime.orNull)
  override def hashCode(): Int = this.id.get.hashCode
  override def equals(o: Any): Boolean = o.isInstanceOf[Project] && o.asInstanceOf[Project].id.get == this.id.get

  /**
    * Set a message and update the database
    * @param content
    * @return
    */
  private def setNote(content: String) = {
    this._notes = content
    update(Notes)
  }

  /**
    * Helper function to decode the json
    */
  implicit val notesRead: Reads[Note] = (
    (JsPath \ "message").read[String] and
      (JsPath \ "user").read[Int] and
      (JsPath \ "time").read[Long]
    ) (Note.apply _)

  /**
    * Add new note
    * @param message
    * @return
    */
  def addNote(message: Note): Future[Int] = {

    /**
      * Helper function to encode to json
      */
    implicit val noteWrites: Writes[Note] = new Writes[Note] {
      def writes(note: Note): JsObject = Json.obj(
        "message" -> note.message,
        "user" -> note.user,
        "time" -> note.time
      )
    }

    val messages = getNotes() :+ message
    val js: Seq[JsValue] = messages.map(m => Json.toJson(m))
    setNote(
      Json.stringify(
        JsObject(Seq(
          "messages" -> JsArray(
            js
          )
        ))
      )
    )
  }

  /**
    * Get all messages
    * @return
    */
  def getNotes(): Seq[Note] = {
    if (this._notes.startsWith("{")  && _notes.endsWith("}")) {
      val messages: JsValue = Json.parse(_notes)
      (messages \ "messages").as[Seq[Note]]
    } else {
      Seq()
    }
  }
}

/**
  * This modal is needed to convert the json
  * @param time
  * @param message
  */
case class Note(message: String, user: Int, time: Long = System.currentTimeMillis()) {
  def getTime(implicit messages: Messages): String = StringUtils.prettifyDateAndTime(new Timestamp(time))
  def render(implicit oreConfig: OreConfig): Html = Page.Render(message)
}

object Project {

  private def queryRoleForTrust(projectId: Rep[Int], userId: Rep[Int]) = {
    val memberTable = TableQuery[ProjectMembersTable]
    val roleTable = TableQuery[ProjectRoleTable]

    for {
      m <- memberTable if m.projectId === projectId && m.userId === userId
      r <- roleTable if m.userId === r.userId && r.projectId === projectId
    } yield {
      r
    }
  }

  lazy val roleForTrustQuery = lifted.Compiled(queryRoleForTrust _)

  /**
    * Helper class for easily building new Projects.
    *
    * @param service ModelService to process with
    */
  case class Builder(service: ModelService) {

    private var _pluginId: String = _
    private var _ownerName: String = _
    private var _ownerId: Int = -1
    private var _name: String = _
    private var _visibility: Visibility = _

    def pluginId(pluginId: String): Builder = {
      this._pluginId = pluginId
      this
    }

    def ownerName(ownerName: String): Builder = {
      this._ownerName = ownerName
      this
    }

    def ownerId(ownerId: Int): Builder = {
      this._ownerId = ownerId
      this
    }

    def name(name: String): Builder = {
      this._name = name
      this
    }

    def visibility(visibility: Visibility): Builder = {
      this._visibility = visibility
      this
    }

    def build(): Project = {
      checkNotNull(this._pluginId, "plugin id null", "")
      checkNotNull(this._ownerName, "owner name null", "")
      checkNotNull(this._name, "name null", "")
      checkArgument(this._ownerId != -1, "invalid owner id", "")
      this.service.processor.process(Project(
        pluginId = this._pluginId,
        _ownerName = this._ownerName,
        _ownerId = this._ownerId,
        _name = this._name,
        _slug = slugify(this._name),
        _visibility = this._visibility
      ))
    }

  }

}
