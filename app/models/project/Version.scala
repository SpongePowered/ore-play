package models.project

import java.sql.Timestamp
import java.time.Instant

import play.twirl.api.Html

import db.access.ModelAccess
import db.impl.OrePostgresDriver.api._
import db.impl.access.UserBase
import db.impl.model.common.{Describable, Downloadable, Hideable}
import db.impl.schema.VersionTable
import db.{DbRef, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.admin.{Review, VersionVisibilityChange}
import models.statistic.VersionDownload
import models.user.User
import ore.OreConfig
import ore.project.{Dependency, ProjectOwned}
import util.FileUtils

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
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
  * @param projectId        ID of project this version belongs to
  * @param _channelId        ID of channel this version belongs to
  */
case class Version(
    id: ObjId[Version] = ObjId.Uninitialized(),
    createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
    projectId: DbRef[Project],
    versionString: String,
    dependencyIds: List[String] = List(),
    channelId: DbRef[Channel],
    fileSize: Long,
    hash: String,
    authorId: DbRef[User],
    description: Option[String] = None,
    downloadCount: Long = 0,
    reviewState: ReviewState = ReviewState.Unreviewed,
    reviewerId: Option[DbRef[User]] = None,
    approvedAt: Option[Timestamp] = None,
    visibility: Visibility = Visibility.Public,
    fileName: String,
    signatureFileName: String,
) extends Model
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
  def channel(implicit service: ModelService): IO[Channel] =
    service
      .access[Channel]()
      .get(this.channelId)
      .getOrElse(throw new NoSuchElementException("None of Option")) // scalafix:ok

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

  def author(implicit userBase: UserBase): OptionT[IO, User] = userBase.get(this.authorId)

  def reviewer(implicit userBase: UserBase): OptionT[IO, User] =
    OptionT.fromOption[IO](this.reviewerId).flatMap(userBase.get)

  def tags(implicit service: ModelService): IO[List[VersionTag]] =
    service.access[VersionTag]().filter(_.versionId === this.id.value).map(_.toList)

  def isSpongePlugin(implicit service: ModelService): IO[Boolean] =
    tags.map(_.map(_.name).contains("Sponge"))

  def isForgeMod(implicit service: ModelService): IO[Boolean] =
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
  def addDownload(implicit service: ModelService): IO[Version] = Defined {
    service.update(copy(downloadCount = downloadCount + 1))
  }

  override def visibilityChanges(implicit service: ModelService): ModelAccess[VersionVisibilityChange] =
    service.access(_.versionId === id.value)

  override def setVisibility(visibility: Visibility, comment: String, creator: DbRef[User])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[(Version, VersionVisibilityChange)] = {
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
          ObjId.Uninitialized(),
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

    updateOldChange *> (updateVersion, createNewChange).parTupled
  }

  /**
    * Returns [[ModelAccess]] to the recorded unique downloads.
    *
    * @return Recorded downloads
    */
  def downloadEntries(implicit service: ModelService): ModelAccess[VersionDownload] =
    service.access(_.modelId === id.value)

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
  def exists(implicit service: ModelService): IO[Boolean] = {
    def hashExists = {
      val baseQuery = for {
        v <- TableQuery[VersionTable]
        if v.projectId === projectId
        if v.hash === hash
      } yield v.id

      service.runDBIO((baseQuery.length > 0).result)
    }

    if (this.projectId == -1) IO.pure(false)
    else
      for {
        hashExists <- hashExists
        project    <- ProjectOwned[Version].project(this)
        pExists    <- project.versions.exists(_.versionString.toLowerCase === this.versionString.toLowerCase)
      } yield hashExists && pExists
  }

  def reviewEntries(implicit service: ModelService): ModelAccess[Review] = service.access(_.versionId === id.value)

  def unfinishedReviews(implicit service: ModelService): IO[Seq[Review]] =
    reviewEntries.sorted(_.createdAt, _.endedAt.?.isEmpty)

  def mostRecentUnfinishedReview(implicit service: ModelService): OptionT[IO, Review] =
    OptionT(unfinishedReviews.map(_.headOption))

  def mostRecentReviews(implicit service: ModelService): IO[Seq[Review]] = reviewEntries.sorted(_.createdAt)

  def reviewById(id: DbRef[Review])(implicit service: ModelService): OptionT[IO, Review] =
    reviewEntries.find(_.id === id)
}

object Version {

  implicit val query: ModelQuery[Version] =
    ModelQuery.from[Version](TableQuery[VersionTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[Version] = (a: Version) => a.projectId

  /**
    * A helper class for easily building new Versions.
    *
    * @param service ModelService to process with
    */
  case class Builder(service: ModelService) {

    // scalafix:off
    private var versionString: String       = _
    private var dependencyIds: List[String] = List()
    private var description: String         = _
    private var projectId: DbRef[Project]   = -1L
    private var authorId: DbRef[User]       = -1L
    private var fileSize: Long              = -1
    private var hash: String                = _
    private var fileName: String            = _
    private var signatureFileName: String   = _
    private var visibility: Visibility      = Visibility.Public
    // scalafix:on

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

    def projectId(projectId: DbRef[Project]): Builder = {
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

    def authorId(authorId: DbRef[User]): Builder = {
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
        visibility = visibility,
        signatureFileName = checkNotNull(this.signatureFileName, "signature file name null", ""),
        channelId = -1L
      )
    }

  }

}
