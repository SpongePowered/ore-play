package models.project

import java.sql.Timestamp
import java.time.Instant

import scala.concurrent.{ExecutionContext, Future}

import play.twirl.api.Html

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.model.common.{Describable, Downloadable, Hideable}
import db.impl.schema.{ReviewTable, VersionTable}
import db.{Model, ModelFilter, ModelQuery, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import models.admin.{Review, VersionVisibilityChange}
import models.statistic.VersionDownload
import models.user.User
import ore.OreConfig
import ore.project.{Dependency, ProjectOwned}
import util.FileUtils

import cats.data.OptionT
import cats.instances.future._
import cats.syntax.all._
import com.google.common.base.Preconditions.{checkArgument, checkNotNull}
import slick.lifted.TableQuery

/**
  * Represents a single version of a Project.
  *
  * @param id               Unique identifier
  * @param createdAt        Instant of creation
  * @param versionString    Version string
  * @param dependencyIds    List of plugin dependencies with the plugin ID and
  *                         version separated by a ':'
  * @param description     User description of version
  * @param assets           Path to assets directory within plugin
  * @param projectId        ID of project this version belongs to
  * @param _channelId        ID of channel this version belongs to
  */
case class Version(
    id: ObjectId = ObjectId.Uninitialized,
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: ObjectReference,
    versionString: String,
    dependencyIds: List[String] = List(),
    assets: Option[String] = None,
    channelId: ObjectReference,
    fileSize: Long,
    hash: String,
    authorId: ObjectReference,
    description: Option[String] = None,
    downloadCount: Long = 0,
    isReviewed: Boolean = false,
    reviewerId: Option[ObjectReference] = None,
    approvedAt: Option[Timestamp] = None,
    tagIds: List[ObjectReference] = List(),
    visibility: Visibility = Visibility.Public,
    fileName: String,
    signatureFileName: String,
    isNonReviewed: Boolean = false
) extends Model
    with ProjectOwned
    with Describable
    with Downloadable
    with Hideable {

  //TODO: Check this in some way
  //checkArgument(description.exists(_.length <= Page.maxLength), "content too long", "")

  override type M                     = Version
  override type T                     = VersionTable
  override type ModelVisibilityChange = VersionVisibilityChange

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
  def channel(implicit ec: ExecutionContext, service: ModelService): Future[Channel] =
    service
      .access[Channel]()
      .get(this.channelId)
      .getOrElse(throw new NoSuchElementException("None of Option"))

  /**
    * Returns the channel this version belongs to from the specified collection
    * of channels if present.
    *
    * @param channels   Channels to search
    * @return           Channel if present, None otherwise
    */
  def findChannelFrom(channels: Seq[Channel]): Option[Channel] = Defined {
    if (channels == null) None
    else channels.find(_.id.value == this.channelId)
  }

  /**
    * Returns this Version's markdown description in HTML.
    *
    * @return Description in html
    */
  def descriptionHtml(implicit config: OreConfig): Html =
    this.description.map(str => Page.render(str)).getOrElse(Html(""))

  /**
    * Returns the base URL for this Version.
    *
    * @return Base URL for version
    */
  def url(implicit project: Project): String = project.url + "/versions/" + this.versionString

  def author(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] = userBase.get(this.authorId)

  def reviewer(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] =
    OptionT.fromOption[Future](this.reviewerId).flatMap(userBase.get)

  def tags(implicit ec: ExecutionContext, service: ModelService): Future[List[Tag]] =
    service.access[Tag]().filter(_.id.inSetBind(tagIds)).map(_.distinct.toList)

  def isSpongePlugin(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] =
    tags.map(_.map(_.name).contains("Sponge"))

  def isForgeMod(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] =
    tags.map(_.map(_.name).contains("Forge"))

  /**
    * Returns this Versions plugin dependencies.
    *
    * @return Plugin dependencies
    */
  def dependencies: List[Dependency] =
    for (depend <- this.dependencyIds) yield {
      val data = depend.split(":")
      Dependency(data(0), if (data.length > 1) data(1) else "")
    }

  /**
    * Returns true if this version has a dependency on the specified plugin ID.
    *
    * @param pluginId Id to check for
    * @return         True if has dependency on ID
    */
  def hasDependency(pluginId: String): Boolean = this.dependencies.exists(_.pluginId == pluginId)

  /**
    * Adds a download to the amount of unique downloads this Version has.
    */
  def addDownload(implicit ec: ExecutionContext, service: ModelService): Future[Version] = Defined {
    service.update(copy(downloadCount = downloadCount + 1))
  }

  override def visibilityChanges(implicit service: ModelService): ModelAccess[VersionVisibilityChange] =
    service.access[VersionVisibilityChange](ModelFilter(_.versionId === id.value))

  override def setVisibility(visibility: Visibility, comment: String, creator: ObjectReference)(
      implicit ec: ExecutionContext,
      service: ModelService
  ): Future[(Version, VersionVisibilityChange)] = {
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
      .access[VersionVisibilityChange]()
      .add(
        VersionVisibilityChange(
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

    val updateVersion = service.update(
      copy(
        visibility = visibility
      )
    )

    updateOldChange *> (updateVersion, createNewChange).tupled
  }

  /**
    * Returns [[ModelAccess]] to the recorded unique downloads.
    *
    * @return Recorded downloads
    */
  def downloadEntries(implicit service: ModelService): ModelAccess[VersionDownload] =
    service.access[VersionDownload](ModelFilter(_.modelId === id.value))

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
  def exists(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] = {
    def hashExists = {
      val baseQuery = for {
        v <- TableQuery[VersionTable]
        if v.projectId === projectId
        if v.hash === hash
      } yield v.id

      service.doAction((baseQuery.length > 0).result)
    }

    if (this.projectId == -1) Future.successful(false)
    else
      for {
        hashExists <- hashExists
        project    <- this.project
        pExists    <- project.versions.exists(_.versionString.toLowerCase === this.versionString.toLowerCase)
      } yield hashExists && pExists
  }

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Version = this.copy(id = id, createdAt = theTime)

  def byCreationDate(first: Review, second: Review): Boolean =
    first.createdAt.value.getTime < second.createdAt.value.getTime
  def reviewEntries(implicit service: ModelService): ModelAccess[Review] =
    service.access[Review](ModelFilter(_.versionId === id.value))
  def unfinishedReviews(implicit ec: ExecutionContext, service: ModelService): Future[Seq[Review]] =
    reviewEntries.all.map(_.toSeq.filter(_.endedAt.isEmpty).sortWith(byCreationDate))
  def mostRecentUnfinishedReview(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, Review] =
    OptionT(unfinishedReviews.map(_.headOption))
  def mostRecentReviews(implicit ec: ExecutionContext, service: ModelService): Future[Seq[Review]] =
    reviewEntries.toSeq.map(_.sortWith(byCreationDate))
  def reviewById(id: ObjectReference)(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, Review] =
    reviewEntries.find(equalsLong[ReviewTable](_.id, id))
  def equalsLong[A <: Table[_]](int1: A => Rep[Long], int2: Long): A => Rep[Boolean] = int1(_) === int2

}

object Version {

  implicit val query: ModelQuery[Version] =
    ModelQuery.from[Version](TableQuery[VersionTable])

  /**
    * A helper class for easily building new Versions.
    *
    * @param service ModelService to process with
    */
  case class Builder(service: ModelService) {

    private var versionString: String         = _
    private var dependencyIds: List[String]   = List()
    private var description: String           = _
    private var projectId: ObjectReference    = -1
    private var authorId: ObjectReference     = -1
    private var fileSize: Long                = -1
    private var hash: String                  = _
    private var fileName: String              = _
    private var signatureFileName: String     = _
    private var tagIds: List[ObjectReference] = List()
    private var visibility: Visibility        = Visibility.Public

    def versionString(versionString: String): Builder = {
      this.versionString = versionString
      this
    }

    def dependencyIds(dependencyIds: List[String]): Builder = {
      this.dependencyIds = dependencyIds
      this
    }

    def description(description: String): Builder = {
      this.description = description
      this
    }

    def projectId(projectId: ObjectReference): Builder = {
      this.projectId = projectId
      this
    }

    def fileSize(fileSize: Long): Builder = {
      this.fileSize = fileSize
      this
    }

    def hash(hash: String): Builder = {
      this.hash = hash
      this
    }

    def authorId(authorId: ObjectReference): Builder = {
      this.authorId = authorId
      this
    }

    def fileName(fileName: String): Builder = {
      this.fileName = fileName
      this
    }

    def signatureFileName(signatureFileName: String): Builder = {
      this.signatureFileName = signatureFileName
      this
    }

    def tagIds(tagIds: List[ObjectReference]): Builder = {
      this.tagIds = tagIds
      this
    }

    def visibility(visibility: Visibility): Builder = {
      this.visibility = visibility
      this
    }

    def build(): Version = {
      checkArgument(this.fileSize != -1, "invalid file size", "")
      Version(
        versionString = checkNotNull(this.versionString, "name null", ""),
        dependencyIds = this.dependencyIds,
        description = Option(this.description),
        projectId = this.projectId,
        fileSize = this.fileSize,
        hash = checkNotNull(this.hash, "hash null", ""),
        authorId = checkNotNull(this.authorId, "author id null", ""),
        fileName = checkNotNull(this.fileName, "file name null", ""),
        tagIds = this.tagIds,
        visibility = visibility,
        signatureFileName = checkNotNull(this.signatureFileName, "signature file name null", ""),
        channelId = -1L
      )
    }

  }

}
