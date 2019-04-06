package models.project

import scala.language.higherKinds

import java.sql.Timestamp
import java.time.Instant

import play.twirl.api.Html

import db.access.{ModelView, QueryView}
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.{Describable, Downloadable, Hideable, HideableOps}
import db.impl.schema.{
  ReviewTable,
  UserTable,
  VersionDownloadsTable,
  VersionTable,
  VersionTagTable,
  VersionVisibilityChangeTable
}
import db.{Model, DbRef, DefaultModelCompanion, ModelQuery, ModelService}
import discourse.OreDiscourseApi
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
  * @param versionString    Version string
  * @param dependencyIds    List of plugin dependencies with the plugin ID and
  *                         version separated by a ':'
  * @param description     User description of version
  * @param projectId        ID of project this version belongs to
  * @param channelId        ID of channel this version belongs to
  */
case class Version(
    projectId: DbRef[Project],
    versionString: String,
    dependencyIds: List[String],
    channelId: DbRef[Channel],
    fileSize: Long,
    hash: String,
    authorId: DbRef[User],
    description: Option[String],
    downloadCount: Long = 0,
    reviewState: ReviewState = ReviewState.Unreviewed,
    reviewerId: Option[DbRef[User]] = None,
    approvedAt: Option[Timestamp] = None,
    visibility: Visibility = Visibility.Public,
    fileName: String,
    createForumPost: Boolean = true,
    postId: Option[Int] = None,
    isPostDirty: Boolean = false,
) extends Describable
    with Downloadable
    with Hideable {

  //TODO: Check this in some way
  //checkArgument(description.exists(_.length <= Page.maxLength), "content too long", "")

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
  def channel(implicit service: ModelService): IO[Model[Channel]] =
    ModelView
      .now(Channel)
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

  def author[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, VersionTagTable, Model[VersionTag]]): QOptRet =
    view.get(this.authorId)

  def reviewer[QOptRet, SRet[_]](view: ModelView[QOptRet, SRet, UserTable, Model[User]]): Option[QOptRet] =
    this.reviewerId.map(view.get)

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
    * Returns a human readable file size for this Version.
    *
    * @return Human readable file size
    */
  def humanFileSize: String = FileUtils.formatFileSize(this.fileSize)

  def reviewById(id: DbRef[Review])(implicit service: ModelService): OptionT[IO, Model[Review]] =
    ModelView.now(Review).get(id)
}

object Version extends DefaultModelCompanion[Version, VersionTable](TableQuery[VersionTable]) {

  implicit val query: ModelQuery[Version] = ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[Version] = (a: Version) => a.projectId

  implicit class VersionModelOps(private val self: Model[Version])
      extends AnyVal
      with HideableOps[Version, VersionVisibilityChange, VersionVisibilityChangeTable] {

    override def visibilityChanges[V[_, _]: QueryView](
        view: V[VersionVisibilityChangeTable, Model[VersionVisibilityChange]]
    ): V[VersionVisibilityChangeTable, Model[VersionVisibilityChange]] =
      view.filterView(_.versionId === self.id.value)

    override def setVisibility(visibility: Visibility, comment: String, creator: DbRef[User])(
        implicit service: ModelService,
        cs: ContextShift[IO]
    ): IO[(Model[Version], Model[VersionVisibilityChange])] = {
      val updateOldChange = lastVisibilityChange(ModelView.now(VersionVisibilityChange))
        .semiflatMap { vc =>
          service.update(vc)(
            _.copy(
              resolvedAt = Some(Timestamp.from(Instant.now())),
              resolvedBy = Some(creator)
            )
          )
        }
        .cata((), _ => ())

      val createNewChange = service.insert(
        VersionVisibilityChange(
          Some(creator),
          self.id,
          comment,
          None,
          None,
          visibility
        )
      )

      val updateVersion = service.update(self)(
        _.copy(
          visibility = visibility
        )
      )

      updateOldChange *> (updateVersion, createNewChange).parTupled
    }

    def tags[V[_, _]: QueryView](
        view: V[VersionTagTable, Model[VersionTag]]
    ): V[VersionTagTable, Model[VersionTag]] =
      view.filterView(_.versionId === self.id.value)

    def isSpongePlugin[QOptRet, SRet[_]](
        view: ModelView[QOptRet, SRet, VersionTagTable, Model[VersionTag]]
    ): SRet[Boolean] =
      tags(view).exists(_.name === "Sponge")

    def isForgeMod[QOptRet, SRet[_]](
        view: ModelView[QOptRet, SRet, VersionTagTable, Model[VersionTag]]
    ): SRet[Boolean] =
      tags(view).exists(_.name === "Forge")

    /**
      * Adds a download to the amount of unique downloads this Version has.
      */
    def addDownload(implicit service: ModelService): IO[Model[Version]] =
      service.update(self)(_.copy(downloadCount = self.downloadCount + 1))

    /**
      * Returns [[ModelView]] to the recorded unique downloads.
      *
      * @return Recorded downloads
      */
    def downloadEntries[V[_, _]: QueryView](
        view: V[VersionDownloadsTable, VersionDownload]
    ): V[VersionDownloadsTable, VersionDownload] =
      view.filterView(_.modelId === self.id.value)

    def reviewEntries[V[_, _]: QueryView](view: V[ReviewTable, Model[Review]]): V[ReviewTable, Model[Review]] =
      view.filterView(_.versionId === self.id.value)

    def unfinishedReviews[V[_, _]: QueryView](view: V[ReviewTable, Model[Review]]): V[ReviewTable, Model[Review]] =
      reviewEntries(view).sortView(_.createdAt).filterView(_.endedAt.?.isEmpty)

    def mostRecentUnfinishedReview[QOptRet, SRet[_]](
        view: ModelView[QOptRet, SRet, ReviewTable, Model[Review]]
    ): QOptRet =
      unfinishedReviews(view).one

    def mostRecentReviews[V[_, _]: QueryView](view: V[ReviewTable, Model[Review]]): V[ReviewTable, Model[Review]] =
      reviewEntries(view).sortView(_.createdAt)

    def updateDescriptionWithForum(
        project: Model[Project],
        description: String
    )(implicit service: ModelService, forums: OreDiscourseApi): IO[Model[Version]] = {
      for {
        updated <- service.update(self)(_.copy(description = Some(description)))
        _ <- if (project.topicId.isDefined && self.postId.isDefined) forums.updateVersionPost(project, updated)
        else IO.pure(false)
      } yield updated
    }
  }
}
