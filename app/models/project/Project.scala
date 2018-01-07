package models.project

import java.sql.Timestamp

import com.google.common.base.Preconditions._
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.model.OreModel
import db.impl.model.common.{Describable, Downloadable, Hideable}
import db.impl.schema.ProjectSchema
import db.impl.table.ModelKeys
import db.impl.table.ModelKeys._
import db.{ModelService, Named}
import models.admin.ProjectLog
import models.api.ProjectApiKey
import models.statistic.ProjectView
import models.user.User
import models.user.role.ProjectRole
import ore.permission.scope.ProjectScope
import ore.project.Categories.Category
import ore.project.FlagReasons.FlagReason
import ore.project.{Categories, ProjectMember}
import ore.user.MembershipDossier
import ore.{Joinable, Visitable}
import util.StringUtils._

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
  * @param _isVisible             Whether this project is visible to the default user
  * @param _lastUpdated           Instant of last version release
  */
case class Project(override val id: Option[Int] = None,
                   override val createdAt: Option[Timestamp] = None,
                   pluginId: String,
                   private var _ownerName: String,
                   private var _ownerId: Int,
                   private var _name: String,
                   private var _slug: String,
                   private var recommendedVersionId: Option[Int] = None,
                   private var _category: Category = Categories.Undefined,
                   private var _description: Option[String] = None,
                   private var _stars: Int = 0,
                   private var _views: Int = 0,
                   private var _downloads: Int = 0,
                   private var _topicId: Int = -1,
                   private var _postId: Int = -1,
                   private var _isTopicDirty: Boolean = false,
                   private var _isVisible: Boolean = true,
                   private var _lastUpdated: Timestamp = null)
                   extends OreModel(id, createdAt)
                     with ProjectScope
                     with Downloadable
                     with Named
                     with Describable
                     with Visitable
                     with Hideable
                     with Joinable[ProjectMember] {

  override type M = Project
  override type T = ProjectTable
  override type S = ProjectSchema

  /**
    * Contains all information for [[User]] memberships.
    */
  override val memberships = new MembershipDossier {

    type ModelType = Project
    type RoleType = ProjectRole
    type RoleTable = ProjectRoleTable
    type MemberType = ProjectMember
    type MembersTable = ProjectMembersTable

    val membersTableClass: Class[MembersTable] = classOf[ProjectMembersTable]
    val roleClass: Class[RoleType] = classOf[ProjectRole]
    val model: ModelType = Project.this

    def newMember(userId: Int): MemberType = new ProjectMember(this.model, userId)

  }

  def this(pluginId: String, name: String, owner: String, ownerId: Int) = {
    this(pluginId=pluginId, _name=compact(name), _slug=slugify(name), _ownerName=owner, _ownerId=ownerId)
  }

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

  /**
    * Sets the [[User]] that owns this Project.
    *
    * @param user User that owns project
    */
  def owner_=(user: User) = {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    this._ownerId = user.id.get
    this._ownerName = user.name
    if (isDefined) {
      update(OwnerId)
      update(OwnerName)
    }
  }

  /**
    * Returns ModelAccess to the user's who are watching this project.
    *
    * @return Users watching project
    */
  def watchers = this.schema.getAssociation[ProjectWatchersTable, User](classOf[ProjectWatchersTable], this)

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
  def name_=(_name: String) = {
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
  def slug_=(_slug: String) = {
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
  def category_=(category: Category) = {
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
  def description_=(description: String) = {
    this._description = Option(description)
    if (isDefined) update(Description)
  }

  /**
    * Returns this [[Project]]'s [[ProjectSettings]].
    *
    * @return Project settings
    */
  def settings: ProjectSettings
  = this.service.access[ProjectSettings](classOf[ProjectSettings]).find(_.projectId === this.id.get).get

  /**
    * Sets this [[Project]]'s [[ProjectSettings]].
    *
    * @param settings Project settings
    * @return         Newly created settings instance
    */
  def settings_=(settings: ProjectSettings): ProjectSettings = Defined {
    checkNotNull(settings, "null settings", "")
    val access = this.service.access[ProjectSettings](classOf[ProjectSettings])
    // Delete previous settings
    val id = this.id.get
    access.removeAll(_.projectId === id)
    // Add new settings
    val newSettings = access.add(settings.copy(projectId = id))
    newSettings
  }

  /**
    * Returns true if this Project is visible.
    *
    * @return True if visible
    */
  override def isVisible: Boolean = this._isVisible

  /**
    * Sets whether this project is visible.
    *
    * @param visible True if visible
    */
  def setVisible(visible: Boolean) = {
    this._isVisible = visible
    if (isDefined) update(IsVisible)
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
  def lastUpdated_=(lastUpdated: Timestamp) = {
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
  def stars = Defined(this.schema.getAssociation[ProjectStarsTable, User](classOf[ProjectStarsTable], this))

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
  def setStarredBy(user: User, starred: Boolean) = Defined {
    checkNotNull(user, "null user", "")
    checkArgument(user.isDefined, "undefined user", "")
    val contains = this.stars.contains(user)
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

  /**
    * Returns the record of unique Project views.
    *
    * @return Unique project views
    */
  def views = this.schema.getChildren[ProjectView](classOf[ProjectView], this)

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
  def flags = this.schema.getChildren[Flag](classOf[Flag], this)

  /**
    * Submits a flag on this project for the specified user.
    *
    * @param user   Flagger
    * @param reason Reason for flagging
    */
  def flagFor(user: User, reason: FlagReason, comment: String) = Defined {
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
  def channels = this.schema.getChildren[Channel](classOf[Channel], this)

  /**
    * Returns all versions in this project.
    *
    * @return Versions in project
    */
  def versions = this.schema.getChildren[Version](classOf[Version], this)

  /**
    * Returns this Project's recommended version.
    *
    * @return Recommended version
    */
  def recommendedVersion: Version = this.versions.get(this.recommendedVersionId.get).get

  /**
    * Updates this project's recommended version.
    *
    * @param _version  Version to set
    * @return         Result
    */
  def recommendedVersion_=(_version: Version) = {
    checkNotNull(_version, "null version", "")
    checkArgument(_version.isDefined, "undefined version", "")
    this.recommendedVersionId = _version.id
    if (isDefined) update(RecommendedVersionId)
  }

  /**
    * Returns the pages in this Project.
    *
    * @return Pages in project
    */
  def pages = this.schema.getChildren[Page](classOf[Page], this)

  /**
    * Returns this Project's home page.
    *
    * @return Project home page
    */
  def homePage: Page = Defined {
    val page = new Page(this.id.get, Page.HomeName, Page.Template(this.name, Page.HomeMessage), false, -1)
    this.service.await(page.schema.getOrInsert(page)).get
  }

  /**
    * Returns true if a page with the specified name exists.
    *
    * @param name   Page name
    * @return       True if exists
    */
  def pageExists(name: String): Boolean = this.pages.exists(_.name === name)

  /**
    * Returns the specified Page or creates it if it doesn't exist.
    *
    * @param name   Page name
    * @return       Page with name or new name if it doesn't exist
    */
  def getOrCreatePage(name: String, parentId: Int = -1): Page = Defined {
    checkNotNull(name, "null name", "")
    val page = new Page(this.id.get, name, Page.Template(name, Page.HomeMessage), true, parentId)
    this.service.await(page.schema.getOrInsert(page)).get
  }

  /**
    * Returns the specified page (lookup up by name).
    *
    * @param name   Page name
    * @return       Some(Page) with name or None
    */
  def getPage(name: String): Option[Page] = Defined {
    checkNotNull(name, "null name", "")
    this.service.access[Page](classOf[Page]).find(p => p.name === name)
  }

  /**
    * Returns the parentless, root, pages for this project.
    *
    * @return Root pages of project
    */
  def rootPages: Seq[Page] = {
    this.service.access[Page](classOf[Page]).sorted(_.name, p => p.projectId === this.id.get && p.parentId === -1)
  }

  def logger: ProjectLog = {
    val loggers = this.service.access[ProjectLog](classOf[ProjectLog])
    loggers.find(_.projectId === this.id.get).getOrElse(loggers.add(ProjectLog(projectId = this.id.get)))
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
  def topicId_=(_topicId: Int) = Defined {
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
  def postId_=(_postId: Int) = Defined {
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
  def setTopicDirty(topicDirty: Boolean) = Defined {
    this._isTopicDirty = topicDirty
    update(IsTopicDirty)
  }

  def apiKeys: ModelAccess[ProjectApiKey] = this.schema.getChildren[ProjectApiKey](classOf[ProjectApiKey], this)

  override def projectId = Defined(this.id.get)
  override def copyWith(id: Option[Int], theTime: Option[Timestamp])
  = this.copy(id = id, createdAt = theTime, _lastUpdated = theTime.orNull)
  override def hashCode() = this.id.get.hashCode
  override def equals(o: Any) = o.isInstanceOf[Project] && o.asInstanceOf[Project].id.get == this.id.get

}

object Project {

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

    def pluginId(pluginId: String) = {
      this._pluginId = pluginId
      this
    }

    def ownerName(ownerName: String) = {
      this._ownerName = ownerName
      this
    }

    def ownerId(ownerId: Int) = {
      this._ownerId = ownerId
      this
    }

    def name(name: String) = {
      this._name = name
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
        _slug = slugify(this._name)
      ))
    }

  }

}
