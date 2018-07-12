package models.project

import java.sql.Timestamp
import java.time.Instant

import com.google.common.base.Preconditions.{checkArgument, checkNotNull}
import db.ModelService
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.model.OreModel
import db.impl.model.common.{Describable, Downloadable}
import db.impl.schema.VersionSchema
import db.impl.table.ModelKeys._
import db.impl.{ReviewTable, VersionTable}
import models.admin.Review
import models.statistic.VersionDownload
import models.user.User
import ore.permission.scope.ProjectScope
import ore.project.Dependency
import play.twirl.api.Html
import util.FileUtils
import util.instances.future._
import util.functional.OptionT

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a single version of a Project.
  *
  * @param id               Unique identifier
  * @param createdAt        Instant of creation
  * @param versionString    Version string
  * @param dependencyIds    List of plugin dependencies with the plugin ID and
  *                         version separated by a ':'
  * @param _description     User description of version
  * @param assets           Path to assets directory within plugin
  * @param projectId        ID of project this version belongs to
  * @param channelId        ID of channel this version belongs to
  */
case class Version(override val id: Option[Int] = None,
                   override val createdAt: Option[Timestamp] = None,
                   override val projectId: Int,
                   versionString: String,
                   dependencyIds: List[String] = List(),
                   assets: Option[String] = None,
                   channelId: Int = -1,
                   fileSize: Long,
                   hash: String,
                   private var _authorId: Int = -1,
                   private var _description: Option[String] = None,
                   private var _downloads: Int = 0,
                   private var _isReviewed: Boolean = false,
                   private var _reviewerId: Int = -1,
                   private var _approvedAt: Option[Timestamp] = None,
                   private var _tagIds: List[Int] = List(),
                   fileName: String,
                   signatureFileName: String)
                   extends OreModel(id, createdAt)
                     with ProjectScope
                     with Describable
                     with Downloadable {

  override type M = Version
  override type T = VersionTable
  override type S = VersionSchema

  /**
    * Returns the name of this Channel.
    *
    * @return Name of channel
    */
  def name: String = this.versionString

  /**
    * Returns the channel this version belongs to.
    *
    * @return Channel
    */
  def channel(implicit ec: ExecutionContext): Future[Channel] =
    this.service.access[Channel](classOf[Channel]).get(this.channelId).getOrElse(throw new NoSuchElementException("None of Option"))

  /**
    * Returns the channel this version belongs to from the specified collection
    * of channels if present.
    *
    * @param channels   Channels to search
    * @return           Channel if present, None otherwise
    */
  def findChannelFrom(channels: Seq[Channel]): Option[Channel] = Defined {
    if (channels == null) None
    else channels.find(_.id.get == this.channelId)
  }

  /**
    * Returns this Version's description.
    *
    * @return Version description
    */
  override def description: Option[String] = this._description

  /**
    * Sets this Version's description.
    *
    * @param _description Version description
    */
  def setDescription(_description: String) = {
    checkArgument(_description == null || _description.length <= Page.MaxLength, "content too long", "")
    this._description = Option(_description)
    if (isDefined) update(Description)
  }

  /**
    * Returns this Version's markdown description in HTML.
    *
    * @return Description in html
    */
  def descriptionHtml: Html
  = this.description.map(str => Page.Render(str)).getOrElse(Html(""))

  /**
    * Returns the base URL for this Version.
    *
    * @return Base URL for version
    */
  def url(implicit project: Project): String = project.url + "/versions/" + this.versionString

  /**
    * Returns true if this version has been reviewed by the moderation staff.
    *
    * @return True if reviewed
    */
  def isReviewed: Boolean = this._isReviewed

  /**
    * Sets whether this version has been reviewed by the moderation staff.
    *
    * @param reviewed True if reviewed
    */
  def setReviewed(reviewed: Boolean) = {
    this._isReviewed = reviewed
    if (isDefined) update(IsReviewed)
  }

  def authorId: Int = this._authorId

  def author(implicit ec: ExecutionContext): OptionT[Future, User] = this.userBase.get(this._authorId)

  def setAuthorId(authorId: Int) = {
    this._authorId = authorId
    // If the project is in the Database
    if (isDefined) {
      update(AuthorId)
    }
  }

  def reviewerId: Int = this._reviewerId

  def reviewer(implicit ec: ExecutionContext): OptionT[Future, User] = this.userBase.get(this._reviewerId)

  def setReviewer(reviewer: User) = Defined {
    this._reviewerId = reviewer.id.get
    update(ReviewerId)
  }

  def setReviewerId(reviewer: Int) = Defined {
    this._reviewerId = reviewer
    update(ReviewerId)
  }

  def approvedAt: Option[Timestamp] = this._approvedAt

  def setApprovedAt(approvedAt: Timestamp) = Defined {
    this._approvedAt = Option(approvedAt)
    update(ApprovedAt)
  }

  def tagIds: List[Int] = this._tagIds

  def setTagIds(tags: List[Int]) = {
    this._tagIds = tags
    if(isDefined) update(TagIds)
  }

  def addTag(tag: Tag) = {
    this._tagIds = this._tagIds :+ tag.id.get
    if (isDefined) {
      update(TagIds)
    }
  }

  def tags(implicit ec: ExecutionContext, service: ModelService = null): Future[List[Tag]] = {
    schema(service)
    this.service.access(classOf[Tag]).filter(_.id inSetBind tagIds).map { list =>
      list.toSet.toList
    }
  }


  def isSpongePlugin(implicit ec: ExecutionContext): Future[Boolean] = tags.map(_.map(_.name).contains("Sponge"))

  def isForgeMod(implicit ec: ExecutionContext): Future[Boolean] = tags.map(_.map(_.name).contains("Forge"))

  /**
    * Returns this Versions plugin dependencies.
    *
    * @return Plugin dependencies
    */
  def dependencies: List[Dependency] = {
    for (depend <- this.dependencyIds) yield {
      val data = depend.split(":")
      Dependency(data(0), data(1))
    }
  }

  /**
    * Returns true if this version has a dependency on the specified plugin ID.
    *
    * @param pluginId Id to check for
    * @return         True if has dependency on ID
    */
  //noinspection ComparingUnrelatedTypes
  def hasDependency(pluginId: String) = this.dependencies.exists(_.pluginId.equals(pluginId))

  /**
    * Returns the amount of unique downloads this Version has.
    *
    * @return Amount of unique downloads
    */
  override def downloadCount: Int = this._downloads

  /**
    * Adds a download to the amount of unique downloads this Version has.
    */
  def addDownload() = Defined {
    this._downloads += 1
    update(Downloads)
  }

  /**
    * Returns [[ModelAccess]] to the recorded unique downloads.
    *
    * @return Recorded downloads
    */
  def downloadEntries = this.schema.getChildren[VersionDownload](classOf[VersionDownload], this)

  /**
    * Returns a human readable file size for this Version.
    *
    * @return Human readable file size
    */
  def humanFileSize: String = FileUtils.formatFileSize(this.fileSize)

  /**
    * Returns true if a project ID is defined on this Model, there is no
    * matching hash in the Project, and there is no duplicate version with
    * the same name in the Project.
    *
    * @return True if exists
    */
  def exists(implicit ec: ExecutionContext): Future[Boolean] = {
    if (this.projectId == -1) Future.successful(false)
    else {
      for {
        hashExists <- this.schema.hashExists(this.projectId, this.hash)
        project <- this.project
        pExists <- project.versions.exists(_.versionString.toLowerCase === this.versionString.toLowerCase)
      } yield {
        hashExists && pExists
      }
    }
  }

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)
  override def hashCode() = this.id.hashCode
  override def equals(o: Any) = o.isInstanceOf[Version] && o.asInstanceOf[Version].id.get == this.id.get

  def byCreationDate(first: Review, second: Review) = first.createdAt.getOrElse(Timestamp.from(Instant.MIN)).getTime < second.createdAt.getOrElse(Timestamp.from(Instant.MIN)).getTime
  def reviewEntries = this.schema.getChildren[Review](classOf[Review], this)
  def unfinishedReviews(implicit ec: ExecutionContext): Future[Seq[Review]] = reviewEntries.all.map(_.toSeq.filter(rev => rev.createdAt.isDefined && rev.endedAt.isEmpty).sortWith(byCreationDate))
  def mostRecentUnfinishedReview(implicit ec: ExecutionContext): OptionT[Future, Review] = OptionT(unfinishedReviews.map(_.headOption))
  def mostRecentReviews(implicit ec: ExecutionContext): Future[Seq[Review]] = reviewEntries.toSeq.map(_.sortWith(byCreationDate))
  def reviewById(id: Int)(implicit ec: ExecutionContext): OptionT[Future, Review] = reviewEntries.find(equalsInt[ReviewTable](_.id, id))
  def equalsInt[T <: Table[_]](int1: T => Rep[Int], int2: Int): T => Rep[Boolean] = int1(_) === int2

}

object Version {

  /**
    * A helper class for easily building new Versions.
    *
    * @param service ModelService to process with
    */
  case class Builder(service: ModelService) {

    private var _versionString: String = _
    private var _dependencyIds: List[String] = List()
    private var _description: String = _
    private var _projectId: Int = -1
    private var _authorId: Int = -1
    private var _fileSize: Long = -1
    private var _hash: String = _
    private var _fileName: String = _
    private var _signatureFileName: String = _
    private var _tagIds: List[Int] = List()

    def versionString(versionString: String) = {
      this._versionString = versionString
      this
    }

    def dependencyIds(dependencyIds: List[String]) = {
      this._dependencyIds = dependencyIds
      this
    }

    def description(description: String) = {
      this._description = description
      this
    }

    def projectId(projectId: Int) = {
      this._projectId = projectId
      this
    }

    def fileSize(fileSize: Long) = {
      this._fileSize = fileSize
      this
    }

    def hash(hash: String) = {
      this._hash = hash
      this
    }

    def authorId(authorId: Int) = {
      this._authorId = authorId
      this
    }

    def fileName(fileName: String) = {
      this._fileName = fileName
      this
    }

    def signatureFileName(signatureFileName: String) = {
      this._signatureFileName = signatureFileName
      this
    }

    def tagIds(tagIds: List[Int]) = {
      this._tagIds = tagIds
      this
    }

    def build() = {
      checkArgument(this._fileSize != -1, "invalid file size", "")
      this.service.processor.process(Version(
        versionString = checkNotNull(this._versionString, "name null", ""),
        dependencyIds = this._dependencyIds,
        _description = Option(this._description),
        projectId = this._projectId,
        fileSize = this._fileSize,
        hash = checkNotNull(this._hash, "hash null", ""),
        _authorId = checkNotNull(this._authorId, "author id null", ""),
        fileName = checkNotNull(this._fileName, "file name null", ""),
        _tagIds = this._tagIds,
        signatureFileName = checkNotNull(this._signatureFileName, "signature file name null", "")))
    }

  }

}
