package models.project

import scala.language.higherKinds

import java.sql.Timestamp
import java.time.Instant

import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.twirl.api.Html

import db.impl.OrePostgresDriver.api._
import db.impl.common._
import db.impl.schema._
import db.impl.{ModelCompanionPartial, OrePostgresDriver}
import models.admin.{ProjectLog, ProjectVisibilityChange}
import models.api.ProjectApiKey
import models.querymodels.ProjectNamespace
import models.statistic.ProjectView
import models.user.User
import models.user.role.ProjectUserRole
import ore.db.access._
import ore.db._
import ore.markdown.MarkdownRenderer
import ore.permission.role.Role
import ore.permission.scope.HasScope
import ore.project.{Category, FlagReason, ProjectMember}
import ore.user.MembershipDossier
import ore.{Joinable, JoinableOps, OreConfig, Visitable}
import _root_.util.StringUtils
import _root_.util.syntax._

import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.google.common.base.Preconditions._
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
    lastUpdated: Timestamp = Timestamp.from(Instant.now()),
    notes: JsValue = JsObject.empty
) extends Downloadable
    with Named
    with Describable
    with Hideable
    with Joinable
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
  def decodeNotes: Seq[Note] = (notes \ "messages").asOpt[Seq[Note]].getOrElse(Nil)
}

/**
  * This modal is needed to convert the json
  */
case class Note(message: String, user: DbRef[User], time: Long = System.currentTimeMillis()) {
  def printTime(implicit oreConfig: Messages): String   = StringUtils.prettifyDateAndTime(new Timestamp(time))
  def render(implicit renderer: MarkdownRenderer): Html = renderer.render(message)
}
object Note {
  implicit val noteWrites: Writes[Note] = (note: Note) =>
    Json.obj(
      "message" -> note.message,
      "user"    -> note.user,
      "time"    -> note.time
  )

  implicit val notesRead: Reads[Note] =
    (JsPath \ "message")
      .read[String]
      .and((JsPath \ "user").read[DbRef[User]])
      .and((JsPath \ "time").read[Long])(Note.apply _)
}

object Project extends ModelCompanionPartial[Project, ProjectTableMain](TableQuery[ProjectTableMain]) {

  implicit val query: ModelQuery[Project] = ModelQuery.from(this)

  override def asDbModel(
      model: Project,
      id: ObjId[Project],
      time: ObjTimestamp
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

  implicit class ProjectModelOps(private val self: Model[Project])
      extends AnyVal
      with HideableOps[Project, ProjectVisibilityChange, ProjectVisibilityChangeTable]
      with JoinableOps[Project, ProjectMember] {

    /**
      * Returns ModelAccess to the user's who are watching this project.
      *
      * @return Users watching project
      */
    def watchers(
        implicit service: ModelService
    ): ParentAssociationAccess[ProjectWatchersTable, Project, User, ProjectTableMain, UserTable, IO] =
      new ModelAssociationAccessImpl(OrePostgresDriver)(Project, User).applyParent(self)

    /**
      * Returns [[ore.db.access.ChildAssociationAccess]] to [[User]]s who have starred this
      * project.
      *
      * @return Users who have starred this project
      */
    def stars(
        implicit service: ModelService
    ): ChildAssociationAccess[ProjectStarsTable, User, Project, UserTable, ProjectTableMain, IO] =
      new ModelAssociationAccessImpl[ProjectStarsTable, User, Project, UserTable, ProjectTableMain](OrePostgresDriver)(
        User,
        Project
      ).applyChild(self)

    /**
      * Contains all information for [[User]] memberships.
      */
    override def memberships(
        implicit service: ModelService
    ): MembershipDossier.Aux[IO, Project, ProjectUserRole, ProjectRoleTable, ProjectMember] =
      MembershipDossier[IO, Project]

    def isOwner(user: Model[User]): Boolean = user.id.value == self.ownerId

    /**
      * Returns the owner [[ProjectMember]] of this project.
      *
      * @return Owner Member of project
      */
    override def owner(implicit service: ModelService): ProjectMember = new ProjectMember(self, self.ownerId)

    override def transferOwner(
        member: ProjectMember
    )(implicit service: ModelService, cs: ContextShift[IO]): IO[Model[Project]] = {
      // Down-grade current owner to "Developer"
      import cats.instances.vector._
      for {
        t1 <- (this.owner.user, member.user).parTupled
        (owner, user) = t1
        t2 <- (this.memberships.getRoles(self, owner), this.memberships.getRoles(self, user)).parTupled
        (ownerRoles, userRoles) = t2
        setOwner <- this.setOwner(user)
        _ <- ownerRoles
          .filter(_.role == Role.ProjectOwner)
          .toVector
          .parTraverse(role => service.update(role)(_.copy(role = Role.ProjectDeveloper)))
        _ <- userRoles.toVector.parTraverse(role => service.update(role)(_.copy(role = Role.ProjectOwner)))
      } yield setOwner
    }

    /**
      * Get VisibilityChanges
      */
    override def visibilityChanges[V[_, _]: QueryView](
        view: V[ProjectVisibilityChangeTable, Model[ProjectVisibilityChange]]
    ): V[ProjectVisibilityChangeTable, Model[ProjectVisibilityChange]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Sets whether this project is visible.
      *
      * @param visibility True if visible
      */
    override def setVisibility(visibility: Visibility, comment: String, creator: DbRef[User])(
        implicit service: ModelService,
        cs: ContextShift[IO]
    ): IO[(Model[Project], Model[ProjectVisibilityChange])] = {
      val updateOldChange = lastVisibilityChange(ModelView.now(ProjectVisibilityChange))
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
        ProjectVisibilityChange(
          Some(creator),
          self.id,
          comment,
          None,
          None,
          visibility
        )
      )

      val updateProject = service.update(self)(
        _.copy(
          visibility = visibility
        )
      )

      updateOldChange *> (updateProject, createNewChange).parTupled
    }

    /**
      * Sets the [[User]] that owns this Project.
      *
      * @param user User that owns project
      */
    def setOwner(user: Model[User])(implicit service: ModelService): IO[Model[Project]] = {
      service.update(self)(
        _.copy(
          ownerId = user.id,
          ownerName = user.name
        )
      )
    }

    /**
      * Returns this [[Project]]'s [[ProjectSettings]].
      *
      * @return Project settings
      */
    def settings(implicit service: ModelService): IO[Model[ProjectSettings]] =
      ModelView
        .now(ProjectSettings)
        .find(_.projectId === self.id.value)
        .getOrElseF(IO.raiseError(new NoSuchElementException("Get on None")))

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
      * @param starred True if should star
      */
    def toggleStarredBy(
        user: Model[User]
    )(implicit service: ModelService): IO[Project] =
      for {
        contains <- self.stars.contains(user)
        res <- if (contains)
          self.stars.removeAssoc(user) *> service.update(self)(_.copy(starCount = self.starCount - 1))
        else
          self.stars.addAssoc(user) *> service.update(self)(_.copy(starCount = self.starCount + 1))
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
    def addView(implicit service: ModelService): IO[Model[Project]] =
      service.update(self)(_.copy(viewCount = self.viewCount + 1))

    /**
      * Increments this Project's downloadc count by one.
      *
      * @return IO result
      */
    def addDownload(implicit service: ModelService): IO[Model[Project]] =
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
    def flagFor(user: Model[User], reason: FlagReason, comment: String)(
        implicit service: ModelService
    ): IO[Model[Flag]] = {
      val userId = user.id.value
      checkArgument(userId != self.ownerId, "cannot flag own project", "")
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

    private def getOrInsert(name: String, parentId: Option[DbRef[Page]])(
        page: Page
    )(implicit service: ModelService): IO[Model[Page]] = {
      def like =
        ModelView.now(Page).find { p =>
          p.projectId === self.id.value && p.name.toLowerCase === name.toLowerCase && parentId.fold(
            p.parentId.isEmpty
          )(parentId => (p.parentId === parentId).getOrElse(false: Rep[Boolean]))
        }

      like.value.flatMap {
        case Some(u) => IO.pure(u)
        case None    => service.insert(page)
      }
    }

    /**
      * Returns this Project's home page.
      *
      * @return Project home page
      */
    def homePage(implicit service: ModelService, config: OreConfig): IO[Model[Page]] = {
      val page = Page(self.id, Page.homeName, Page.template(self.name, Page.homeMessage), isDeletable = false, None)
      getOrInsert(Page.homeName, None)(page)
    }

    /**
      * Returns the specified Page or creates it if it doesn't exist.
      *
      * @param name   Page name
      * @return       Page with name or new name if it doesn't exist
      */
    def getOrCreatePage(
        name: String,
        parentId: Option[DbRef[Page]],
        content: Option[String] = None
    )(implicit config: OreConfig, service: ModelService): IO[Model[Page]] = {
      checkNotNull(name, "null name", "")
      val c = content match {
        case None => Page.template(name, Page.homeMessage)
        case Some(text) =>
          checkNotNull(text, "null contents", "")
          checkArgument(text.length <= Page.maxLengthPage, "contents too long", "")
          text
      }
      val page = Page(self.id, name, c, isDeletable = true, parentId)
      getOrInsert(name, parentId)(page)
    }

    /**
      * Returns the parentless, root, pages for this project.
      *
      * @return Root pages of project
      */
    def rootPages[V[_, _]: QueryView](view: V[PageTable, Model[Page]]): V[PageTable, Model[Page]] =
      view.sortView(_.name).filterView(p => p.projectId === self.id.value && p.parentId.isEmpty)

    def logger(implicit service: ModelService): IO[Model[ProjectLog]] =
      ModelView.now(ProjectLog).find(_.projectId === self.id.value).getOrElseF(service.insert(ProjectLog(self.id)))

    def apiKeys[V[_, _]: QueryView](
        view: V[ProjectApiKeyTable, Model[ProjectApiKey]]
    ): V[ProjectApiKeyTable, Model[ProjectApiKey]] =
      view.filterView(_.projectId === self.id.value)

    /**
      * Add new note
      */
    def addNote(message: Note)(implicit service: ModelService): IO[Model[Project]] = {
      val messages = self.decodeNotes :+ message
      service.update(self)(
        _.copy(
          notes = JsObject(
            Seq("messages" -> Json.toJson(messages))
          )
        )
      )
    }
  }
}
