package ore.models.project

import scala.language.higherKinds

import java.time.Instant

import ore.data.project.Dependency
import ore.db.access.{ModelView, QueryView}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common.{Describable, Downloadable, Hideable}
import ore.db.impl.schema._
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.models.admin.{Review, VersionVisibilityChange}
import ore.models.statistic.VersionDownload
import ore.models.user.User
import ore.syntax._
import ore.util.FileUtils

import cats.data.OptionT
import cats.syntax.all._
import cats.{Monad, MonadError, Parallel}
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
    approvedAt: Option[Instant] = None,
    visibility: Visibility = Visibility.Public,
    fileName: String,
    createForumPost: Boolean = true,
    postId: Option[Int] = None,
    isPostDirty: Boolean = false
) extends Describable
    with Downloadable {

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
  def channel[F[_]: ModelService](implicit F: MonadError[F, Throwable]): F[Model[Channel]] =
    ModelView
      .now(Channel)
      .get(this.channelId)
      .getOrElseF(F.raiseError(new NoSuchElementException("None of Option")))

  /**
    * Returns the base URL for this Version.
    *
    * @return Base URL for version
    */
  def url(project: Project): String = project.url + "/versions/" + this.versionString

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
      Dependency(data(0), data.lift(1))
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

  def reviewById[F[_]](id: DbRef[Review])(implicit service: ModelService[F]): OptionT[F, Model[Review]] =
    ModelView.now(Review).get(id)
}

object Version extends DefaultModelCompanion[Version, VersionTable](TableQuery[VersionTable]) {

  implicit val query: ModelQuery[Version] = ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[Version] = (a: Version) => a.projectId

  implicit def versionIsHideable[F[_]](
      implicit service: ModelService[F],
      F: Monad[F],
      par: Parallel[F]
  ): Hideable.Aux[F, Version, VersionVisibilityChange, VersionVisibilityChangeTable] = new Hideable[F, Version] {
    override type MVisibilityChange      = VersionVisibilityChange
    override type MVisibilityChangeTable = VersionVisibilityChangeTable

    override def visibility(m: Version): Visibility = m.visibility

    override def setVisibility(m: Model[Version])(
        visibility: Visibility,
        comment: String,
        creator: DbRef[User]
    ): F[(Model[Version], Model[VersionVisibilityChange])] = {
      val updateOldChange = lastVisibilityChange(m)(ModelView.now(VersionVisibilityChange))
        .semiflatMap { vc =>
          service.update(vc)(
            _.copy(
              resolvedAt = Some(Instant.now()),
              resolvedBy = Some(creator)
            )
          )
        }
        .cata((), _ => ())

      val createNewChange = service.insert(
        VersionVisibilityChange(
          Some(creator),
          m.id,
          comment,
          None,
          None,
          visibility
        )
      )

      val updateVersion = service.update(m)(
        _.copy(
          visibility = visibility
        )
      )

      updateOldChange *> (updateVersion, createNewChange).parTupled
    }

    override def visibilityChanges[V[_, _]: QueryView](m: Model[Version])(
        view: V[VersionVisibilityChangeTable, Model[VersionVisibilityChange]]
    ): V[VersionVisibilityChangeTable, Model[VersionVisibilityChange]] = view.filterView(_.versionId === m.id.value)
  }

  implicit class VersionModelOps(private val self: Model[Version]) extends AnyVal {

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
    def addDownload[F[_]](implicit service: ModelService[F]): F[Model[Version]] =
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
  }
}
