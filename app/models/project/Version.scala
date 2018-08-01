package models.project

import java.sql.Timestamp
import java.time.Instant

import com.google.common.base.Preconditions.{checkArgument, checkNotNull}

import db.{Model, ModelService, ObjectId, ObjectReference, ObjectTimestamp}
import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.{Describable, Downloadable, Hideable}
import db.impl.schema.VersionSchema
import db.impl.{ReviewTable, VersionTable}
import models.admin.{Review, VersionVisibilityChange}
import models.statistic.VersionDownload
import models.user.User
import ore.permission.scope.ProjectScope
import ore.project.Dependency
import play.twirl.api.Html
import util.FileUtils
import util.instances.future._
import util.functional.OptionT
import util.syntax._
import scala.concurrent.{ExecutionContext, Future}

import db.impl.access.UserBase
import models.project.VisibilityTypes.Visibility
import ore.OreConfig

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
case class Version(id: ObjectId = ObjectId.Uninitialized,
                   createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                   projectId: ObjectReference,
                   versionString: String,
                   dependencyIds: List[String] = List(),
                   assets: Option[String] = None,
                   channelId: ObjectReference = -1,
                   fileSize: Long,
                   hash: String,
                   authorId: ObjectReference = -1,
                   description: Option[String] = None,
                   downloadCount: Int = 0,
                   isReviewed: Boolean = false,
                   reviewerId: ObjectReference = -1,
                   approvedAt: Option[Timestamp] = None,
                   tagIds: List[ObjectReference] = List(),
                   visibility: Visibility = VisibilityTypes.Public,
                   fileName: String,
                   signatureFileName: String,
                   isNonReviewed: Boolean = false)
                   extends Model
                     with ProjectScope
                     with Describable
                     with Downloadable
                     with Hideable {

  //TODO: Check this in some way
  //checkArgument(description.exists(_.length <= Page.maxLength), "content too long", "")

  override type M = Version
  override type T = VersionTable
  override type S = VersionSchema
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
    service.access[Channel](classOf[Channel]).get(this.channelId).getOrElse(throw new NoSuchElementException("None of Option"))

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
  def descriptionHtml(implicit config: OreConfig): Html
  = this.description.map(str => Page.render(str)).getOrElse(Html(""))

  /**
    * Returns the base URL for this Version.
    *
    * @return Base URL for version
    */
  def url(implicit project: Project): String = project.url + "/versions/" + this.versionString

  def author(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] = userBase.get(this.authorId)

  def reviewer(implicit ec: ExecutionContext, userBase: UserBase): OptionT[Future, User] = userBase.get(this.reviewerId)

  def tags(implicit ec: ExecutionContext, service: ModelService): Future[List[Tag]] = {
    service.access(classOf[Tag]).filter(_.id inSetBind tagIds).map { list =>
      list.distinct.toList
    }
  }


  def isSpongePlugin(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] = tags.map(_.map(_.name).contains("Sponge"))

  def isForgeMod(implicit ec: ExecutionContext, service: ModelService): Future[Boolean] = tags.map(_.map(_.name).contains("Forge"))

  /**
    * Returns this Versions plugin dependencies.
    *
    * @return Plugin dependencies
    */
  def dependencies: List[Dependency] = {
    for (depend <- this.dependencyIds) yield {
      val data = depend.split(":")
      Dependency(data(0), if (data.length > 1) data(1) else "")
    }
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
    this.schema.getChildren[VersionVisibilityChange](classOf[VersionVisibilityChange], this)

  override def setVisibility(visibility: Visibility, comment: String, creator: Int)(implicit ec: ExecutionContext, service: ModelService): Future[(Version, VersionVisibilityChange)] = {
    val updateOldChange = lastVisibilityChange.semiFlatMap { vc =>
      service.update(
        vc.copy(
          resolvedAt = Some(Timestamp.from(Instant.now())),
          resolvedBy = Some(creator)
        )
      )
    }.cata((), _ => ())

    val createNewChange = service.access(classOf[VersionVisibilityChange]).add(
      VersionVisibilityChange(ObjectId.Uninitialized, ObjectTimestamp(Timestamp.from(Instant.now())), Some(creator), this.id.value, comment, None, None, visibility.id)
    )

    val updateVersion = service.update(
      copy(
        visibility = visibility
      )
    )

    updateOldChange *> (updateVersion, createNewChange).parTupled
  }

  /**
    * Returns [[ModelAccess]] to the recorded unique downloads.
    *
    * @return Recorded downloads
    */
  def downloadEntries(implicit service: ModelService): ModelAccess[VersionDownload] = this.schema.getChildren[VersionDownload](classOf[VersionDownload], this)

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

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): Version = this.copy(id = id, createdAt = theTime)

  def byCreationDate(first: Review, second: Review): Boolean = first.createdAt.value.getTime < second.createdAt.value.getTime
  def reviewEntries(implicit service: ModelService): ModelAccess[Review] = this.schema.getChildren[Review](classOf[Review], this)
  def unfinishedReviews(implicit ec: ExecutionContext, service: ModelService): Future[Seq[Review]] = reviewEntries.all.map(_.toSeq.filter(_.endedAt.isEmpty).sortWith(byCreationDate))
  def mostRecentUnfinishedReview(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, Review] = OptionT(unfinishedReviews.map(_.headOption))
  def mostRecentReviews(implicit ec: ExecutionContext, service: ModelService): Future[Seq[Review]] = reviewEntries.toSeq.map(_.sortWith(byCreationDate))
  def reviewById(id: ObjectReference)(implicit ec: ExecutionContext, service: ModelService): OptionT[Future, Review] = reviewEntries.find(equalsInt[ReviewTable](_.id, id))
  def equalsInt[T <: Table[_]](int1: T => Rep[Int], int2: Int): T => Rep[Boolean] = int1(_) === int2

}

object Version {

  /**
    * A helper class for easily building new Versions.
    *
    * @param service ModelService to process with
    */
  case class Builder(service: ModelService) {

    private var versionString: String = _
    private var dependencyIds: List[String] = List()
    private var description: String = _
    private var projectId: Int = -1
    private var authorId: Int = -1
    private var fileSize: Long = -1
    private var hash: String = _
    private var fileName: String = _
    private var signatureFileName: String = _
    private var tagIds: List[Int] = List()
    private var visibility: Visibility = VisibilityTypes.Public

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

    def projectId(projectId: Int): Builder = {
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

    def authorId(authorId: Int): Builder = {
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

    def tagIds(tagIds: List[Int]): Builder = {
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
        signatureFileName = checkNotNull(this.signatureFileName, "signature file name null", ""))
    }

  }

}
