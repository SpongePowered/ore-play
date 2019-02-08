package models.project

import scala.language.higherKinds

import java.sql.Timestamp
import java.time.Instant

import play.twirl.api.Html

import db.access.{ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.{Describable, Downloadable, Hideable}
import db.impl.schema.VersionTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjTimestamp}
import models.admin.{Review, VersionVisibilityChange}
import models.statistic.VersionDownload
import models.user.User
import ore.OreConfig
import ore.project.{Dependency, ProjectOwned}
import util.FileUtils
import util.syntax._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
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
  * @param channelId        ID of channel this version belongs to
  */
case class Version private (
    id: ObjId[Version],
    createdAt: ObjTimestamp,
    projectId: DbRef[Project],
    versionString: String,
    dependencyIds: List[String],
    channelId: DbRef[Channel],
    fileSize: Long,
    hash: String,
    authorId: DbRef[User],
    description: Option[String],
    downloadCount: Long,
    reviewState: ReviewState,
    reviewerId: Option[DbRef[User]],
    approvedAt: Option[Timestamp],
    visibility: Visibility,
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
    ModelView
      .now[Channel]
      .get(this.channelId)
      .getOrElseF(IO.raiseError(new NoSuchElementException("None of Option")))

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

  def author[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, VersionTag#T, VersionTag]): QOptRet =
    view.get(this.authorId)

  def reviewer[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, User#T, User]): Option[QOptRet] =
    this.reviewerId.map(view.get)

  def tags[V[_, _]: QueryView](view: V[VersionTag#T, VersionTag]): V[VersionTag#T, VersionTag] =
    view.filterView(_.versionId === this.id.value)

  def isSpongePlugin[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, VersionTag#T, VersionTag]): SRet[Boolean] =
    tags(view).exists(_.name === "Sponge")

  def isForgeMod[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, VersionTag#T, VersionTag]): SRet[Boolean] =
    tags(view).exists(_.name === "Forge")

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
  def addDownload(implicit service: ModelService): IO[Version] =
    service.update(copy(downloadCount = downloadCount + 1))

  override def visibilityChanges[V[_, _]: QueryView](
      view: V[VersionVisibilityChange#T, VersionVisibilityChange]
  ): V[VersionVisibilityChange#T, VersionVisibilityChange] =
    view.filterView(_.versionId === id.value)

  override def setVisibility(visibility: Visibility, comment: String, creator: DbRef[User])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[(Version, VersionVisibilityChange)] = {
    val updateOldChange = lastVisibilityChange(ModelView.now[VersionVisibilityChange])
      .semiflatMap { vc =>
        service.update(
          vc.copy(
            resolvedAt = Some(Timestamp.from(Instant.now())),
            resolvedBy = Some(creator)
          )
        )
      }
      .cata((), _ => ())

    val createNewChange = service.insert(
      VersionVisibilityChange.partial(
        Some(creator),
        this.id,
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
    * Returns [[ModelView]] to the recorded unique downloads.
    *
    * @return Recorded downloads
    */
  def downloadEntries[V[_, _]: QueryView](
      view: V[VersionDownload#T, VersionDownload]
  ): V[VersionDownload#T, VersionDownload] =
    view.filterView(_.modelId === id.value)

  /**
    * Returns a human readable file size for this Version.
    *
    * @return Human readable file size
    */
  def humanFileSize: String = FileUtils.formatFileSize(this.fileSize)

  def reviewEntries[V[_, _]: QueryView](view: V[Review#T, Review]): V[Review#T, Review] =
    view.filterView(_.versionId === id.value)

  def unfinishedReviews[V[_, _]: QueryView](view: V[Review#T, Review]): V[Review#T, Review] =
    reviewEntries(view).sortView(_.createdAt).filterView(_.endedAt.?.isEmpty)

  def mostRecentUnfinishedReview[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, Review#T, Review]): QOptRet =
    unfinishedReviews(view).one

  def mostRecentReviews[V[_, _]: QueryView](view: V[Review#T, Review]): V[Review#T, Review] =
    reviewEntries(view).sortView(_.createdAt)

  def reviewById(id: DbRef[Review])(implicit service: ModelService): OptionT[IO, Review] =
    ModelView.now[Review].get(id)
}

object Version {

  def partial(
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
  ): InsertFunc[Version] =
    (id, time) =>
      Version(
        id,
        time,
        projectId,
        versionString,
        dependencyIds,
        channelId,
        fileSize,
        hash,
        authorId,
        description,
        downloadCount,
        reviewState,
        reviewerId,
        approvedAt,
        visibility,
        fileName,
        signatureFileName
    )

  implicit val query: ModelQuery[Version] =
    ModelQuery.from[Version](TableQuery[VersionTable], _.copy(_, _))

  implicit val isProjectOwned: ProjectOwned[Version] = (a: Version) => a.projectId
}
