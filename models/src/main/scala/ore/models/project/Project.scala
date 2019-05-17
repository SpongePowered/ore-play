package ore.models.project

import scala.language.higherKinds

import java.time.Instant
import java.util.Locale

import ore.data.project.{Category, FlagReason, ProjectNamespace}
import ore.db._
import ore.db.access._
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common._
import ore.db.impl.schema._
import ore.db.impl.{ModelCompanionPartial, OrePostgresDriver}
import ore.member.{Joinable, MembershipDossier}
import ore.models.admin.{ProjectLogEntry, ProjectVisibilityChange}
import ore.models.api.ProjectApiKey
import ore.models.statistic.ProjectView
import ore.models.user.role.ProjectUserRole
import ore.models.user.{User, UserOwned}
import ore.permission.role.Role
import ore.permission.scope.HasScope
import ore.syntax._
import ore.util.StringLocaleFormatterUtils

import cats.syntax.all._
import cats.{Functor, Monad, MonadError, Parallel}
import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.syntax._
import slick.lifted
import slick.lifted.{Rep, TableQuery}

/**
  * Represents an Ore package.
  *
  * <p>Note: As a general rule, do not handle actions / results in model classes</p>
  *
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
    pluginId: String,
    ownerName: String,
    ownerId: DbRef[User],
    name: String,
    slug: String,
    recommendedVersionId: Option[DbRef[Version]] = None,
    category: Category = Category.Undefined,
    description: Option[String],
    starCount: Long = 0,
    viewCount: Long = 0,
    downloadCount: Long = 0,
    topicId: Option[Int] = None,
    postId: Option[Int] = None,
    isTopicDirty: Boolean = false,
    visibility: Visibility = Visibility.Public,
    lastUpdated: Instant = Instant.now(),
    notes: Json = Json.obj(),
    keywords: List[String] = Nil
) extends Downloadable
    with Named
    with Describable
    with Visitable {

  def namespace: ProjectNamespace = ProjectNamespace(ownerName, slug)

  /**
    * Returns the base URL for this Project.
    *
    * @return Base URL for project
    */
  override def url: String = namespace.toString

  /**
    * Get all messages
    * @return
    */
  def decodeNotes: Seq[Note] =
    notes.hcursor.getOrElse[Seq[Note]]("messages")(Nil).toTry.get //Should be safe. If it's not we got bigger problems

  def isOwner(user: Model[User]): Boolean = user.id.value == ownerId
}

/**
  * This modal is needed to convert the json
  */
@JsonCodec case class Note(message: String, user: DbRef[User], time: Long = System.currentTimeMillis()) {
  def printTime(implicit locale: Locale): String =
    StringLocaleFormatterUtils.prettifyDateAndTime(Instant.ofEpochMilli(time))
}

object Project extends ModelCompanionPartial[Project, ProjectTableMain](TableQuery[ProjectTableMain]) {

  implicit val query: ModelQuery[Project] = ModelQuery.from(this)

  override def asDbModel(
      model: Project,
      id: ObjId[Project],
      time: ObjInstant
  ): Model[Project] = Model(id, time, model.copy(lastUpdated = time))

  implicit val assocWatchersQuery: AssociationQuery[ProjectWatchersTable, Project, User] =
    AssociationQuery.from[ProjectWatchersTable, Project, User](TableQuery[ProjectWatchersTable])(_.projectId, _.userId)

  implicit val hasScope: HasScope[Model[Project]] = HasScope.projectScope(_.id)

  private def queryRoleForTrust(projectId: Rep[DbRef[Project]], userId: Rep[DbRef[User]]) = {
    val q = for {
      m <- TableQuery[ProjectMembersTable] if m.projectId === projectId && m.userId === userId
      r <- TableQuery[ProjectRoleTable] if m.userId === r.userId && r.projectId === projectId
    } yield r.roleType
    q.to[Set]
  }

  lazy val roleForTrustQuery = lifted.Compiled(queryRoleForTrust _)

  implicit def projectHideable[F[_], G[_]](
      implicit service: ModelService[F],
      F: Monad[F],
      parallel: Parallel[F, G]
  ): Hideable.Aux[F, Project, ProjectVisibilityChange, ProjectVisibilityChangeTable] = new Hideable[F, Project] {
    override type MVisibilityChange      = ProjectVisibilityChange
    override type MVisibilityChangeTable = ProjectVisibilityChangeTable

    override def visibility(m: Project): Visibility = m.visibility

    /**
      * Sets whether this project is visible.
      *
      * @param visibility True if visible
      */
    override def setVisibility(m: Model[Project])(
        visibility: Visibility,
        comment: String,
        creator: DbRef[User]
    ): F[(Model[Project], Model[ProjectVisibilityChange])] = {
      val updateOldChange = lastVisibilityChange(m)(ModelView.now(ProjectVisibilityChange))
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
        ProjectVisibilityChange(
          Some(creator),
          m.id,
          comment,
          None,
          None,
          visibility
        )
      )

      val updateProject = service.update(m)(
        _.copy(
          visibility = visibility
        )
      )

      updateOldChange *> (updateProject, createNewChange).parTupled
    }

    /**
      * Get VisibilityChanges
      */
    override def visibilityChanges[V[_, _]: QueryView](m: Model[Project])(
        view: V[ProjectVisibilityChangeTable, Model[ProjectVisibilityChange]]
    ): V[ProjectVisibilityChangeTable, Model[ProjectVisibilityChange]] = view.filterView(_.projectId === m.id.value)
  }

  implicit val isUserOwned: UserOwned[Project] = (a: Project) => a.ownerId

  implicit def projectJoinable[F[_], G[_]](
      implicit service: ModelService[F],
      F: MonadError[F, Throwable],
      par: Parallel[F, G]
  ): Joinable.Aux[F, Project, ProjectUserRole, ProjectRoleTable] = new Joinable[F, Project] {
    type RoleType      = ProjectUserRole
    type RoleTypeTable = ProjectRoleTable

    override def transferOwner(m: Model[Project])(newOwner: DbRef[User]): F[Model[Project]] = {
      // Down-grade current owner to "Developer"
      import cats.instances.vector._
      val oldOwner = m.ownerId
      for {
        newOwnerUser <- ModelView
          .now(User)
          .get(newOwner)
          .getOrElseF(F.raiseError(new Exception("Could not find user to transfer owner to")))
        t2 <- (this.memberships.getRoles(m)(oldOwner), this.memberships.getRoles(m)(newOwner)).parTupled
        (ownerRoles, userRoles) = t2
        setOwner <- setOwner(m)(newOwnerUser)
        _ <- ownerRoles
          .filter(_.role == Role.ProjectOwner)
          .toVector
          .parTraverse(role => service.update(role)(_.copy(role = Role.ProjectDeveloper)))
        _ <- userRoles.toVector.parTraverse(role => service.update(role)(_.copy(role = Role.ProjectOwner)))
      } yield setOwner
    }

    private def setOwner(m: Model[Project])(user: Model[User]): F[Model[Project]] = {
      service.update(m)(
        _.copy(
          ownerId = user.id,
          ownerName = user.name
        )
      )
    }

    override def memberships: MembershipDossier.Aux[F, Project, RoleType, RoleTypeTable] =
      MembershipDossier.projectHasMemberships

    override def userOwned: UserOwned[Project] = isUserOwned
  }

  implicit class ProjectModelOps(private val self: Model[Project]) extends AnyVal {

    /**
      * Returns ModelAccess to the user's who are watching this project.
      *
      * @return Users watching project
      */
    def watchers[F[_]: ModelService: Functor]
      : ParentAssociationAccess[ProjectWatchersTable, Project, User, ProjectTableMain, UserTable, F] =
      new ModelAssociationAccessImpl(OrePostgresDriver)(Project, User).applyParent(self.id)

    /**
      * Returns [[ore.db.access.ChildAssociationAccess]] to [[User]]s who have starred this
      * project.
      *
      * @return Users who have starred this project
      */
    def stars[F[_]: ModelService: Functor]
      : ChildAssociationAccess[ProjectStarsTable, User, Project, UserTable, ProjectTableMain, F] =
      new ModelAssociationAccessImpl[ProjectStarsTable, User, Project, UserTable, ProjectTableMain, F](
        OrePostgresDriver
      )(
        User,
        Project
      ).applyChild(self.id)

    /**
      * Returns this [[Project]]'s [[ProjectSettings]].
      *
      * @return Project settings
      */
    def settings[F[_]](implicit service: ModelService[F], F: MonadError[F, Throwable]): F[Model[ProjectSettings]] =
      ModelView
        .now(ProjectSettings)
        .find(_.projectId === self.id.value)
        .getOrElseF(F.raiseError(new NoSuchElementException("Get on None")))

    /**
      * Returns this Project's recommended version.
      *
      * @return Recommended version
      */
    def recommendedVersion[QOptRet, SRet[_]](
        view: ModelView[QOptRet, SRet, VersionTable, Model[Version]]
    ): Option[QOptRet] =
      self.recommendedVersionId.map(versions(view).get)

    /**
      * Sets the "starred" state of this Project for the specified User.
      *
      * @param user User to set starred state of
      */
    def toggleStarredBy[F[_]](
        user: Model[User]
    )(implicit service: ModelService[F], F: Monad[F]): F[Project] =
      for {
        contains <- self.stars.contains(user.id)
        res <- if (contains)
          self.stars.removeAssoc(user.id) *> service.update(self)(_.copy(starCount = self.starCount - 1))
        else
          self.stars.addAssoc(user.id) *> service.update(self)(_.copy(starCount = self.starCount + 1))
      } yield res

    /**
      * Returns the record of unique Project views.
      *
      * @return Unique project views
      */
    def views[V[_, _]: QueryView](
        view: V[ProjectViewsTable, Model[ProjectView]]
    ): V[ProjectViewsTable, Model[ProjectView]] =
      view.filterView(_.modelId === self.id.value)

    /**
      * Adds a view to this Project.
      */
    def addView[F[_]](implicit service: ModelService[F]): F[Model[Project]] =
      service.update(self)(_.copy(viewCount = self.viewCount + 1))

    /**
      * Increments this Project's downloadc count by one.
      *
      * @return IO result
      */
    def addDownload[F[_]](implicit service: ModelService[F]): F[Model[Project]] =
      service.update(self)(_.copy(downloadCount = self.downloadCount + 1))

    /**
      * Returns all flags on this project.
      *
      * @return Flags on project
      */
    def flags[V[_, _]: QueryView](view: V[FlagTable, Model[Flag]]): V[FlagTable, Model[Flag]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Submits a flag on this project for the specified user.
      *
      * @param user   Flagger
      * @param reason Reason for flagging
      */
    def flagFor[F[_]](user: Model[User], reason: FlagReason, comment: String)(
        implicit service: ModelService[F]
    ): F[Model[Flag]] = {
      val userId = user.id.value
      require(userId != self.ownerId, "cannot flag own project")
      service.insert(Flag(self.id, user.id, reason, comment))
    }

    /**
      * Returns the Channels in this Project.
      *
      * @return Channels in project
      */
    def channels[V[_, _]: QueryView](view: V[ChannelTable, Model[Channel]]): V[ChannelTable, Model[Channel]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Returns all versions in this project.
      *
      * @return Versions in project
      */
    def versions[V[_, _]: QueryView](view: V[VersionTable, Model[Version]]): V[VersionTable, Model[Version]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Returns the pages in this Project.
      *
      * @return Pages in project
      */
    def pages[V[_, _]: QueryView](view: V[PageTable, Model[Page]]): V[PageTable, Model[Page]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Returns the parentless, root, pages for this project.
      *
      * @return Root pages of project
      */
    def rootPages[V[_, _]: QueryView](view: V[PageTable, Model[Page]]): V[PageTable, Model[Page]] =
      view.sortView(_.name).filterView(p => p.projectId === self.id.value && p.parentId.isEmpty)

    def apiKeys[V[_, _]: QueryView](
        view: V[ProjectApiKeyTable, Model[ProjectApiKey]]
    ): V[ProjectApiKeyTable, Model[ProjectApiKey]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Add new note
      */
    def addNote[F[_]](message: Note)(implicit service: ModelService[F]): F[Model[Project]] = {
      val messages = self.decodeNotes :+ message
      service.update(self)(
        _.copy(
          notes = Json.obj(
            "messages" := messages
          )
        )
      )
    }

    def loggerEntries[V[_, _]: QueryView](
        view: V[ProjectLogEntryTable, Model[ProjectLogEntry]]
    ): V[ProjectLogEntryTable, Model[ProjectLogEntry]] = view.filterView(_.projectId === self.id.value)

    /**
      * Adds a new log entry with an "error" tag to the log.
      *
      * @param message  Message to log
      * @return         New entry
      */
    def logError[F[_]](message: String)(implicit service: ModelService[F], F: Monad[F]): F[Model[ProjectLogEntry]] = {
      val tag = "error"
      loggerEntries(ModelView.now(ProjectLogEntry))
        .find(e => e.message === message && e.tag === tag)
        .semiflatMap { entry =>
          service.update(entry)(
            _.copy(
              occurrences = entry.occurrences + 1,
              lastOccurrence = Instant.now()
            )
          )
        }
        .getOrElseF {
          service.insert(
            ProjectLogEntry(self.id, tag, message, lastOccurrence = Instant.now())
          )
        }
    }
  }
}
