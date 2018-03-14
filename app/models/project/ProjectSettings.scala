package models.project

import java.nio.file.Files._
import java.sql.Timestamp

import db.impl._
import db.impl.model.OreModel
import db.impl.table.ModelKeys._
import form.project.ProjectSettingsForm
import models.user.Notification
import models.user.role.ProjectRole
import ore.permission.role.RoleTypes
import ore.project.io.ProjectFiles
import ore.project.{Categories, ProjectOwned}
import ore.user.notification.NotificationTypes
import play.api.Logger
import play.api.i18n.{Lang, MessagesApi}
import slick.lifted.TableQuery
import util.StringUtils._
import db.impl.OrePostgresDriver.api._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a [[Project]]'s settings.
  *
  * @param id           Unique ID
  * @param createdAt    Instant of creation
  * @param projectId    ID of project settings belong to
  * @param homepage     Project homepage
  * @param _issues      Project issues URL
  * @param _source      Project source URL
  * @param _licenseName Project license name
  * @param _licenseUrl  Project license URL
  */
case class ProjectSettings(override val id: Option[Int] = None,
                           override val createdAt: Option[Timestamp] = None,
                           override val projectId: Int = -1,
                           homepage: Option[String] = None,
                           private var _issues: Option[String] = None,
                           private var _source: Option[String] = None,
                           private var _licenseName: Option[String] = None,
                           private var _licenseUrl: Option[String] = None,
                           private var _forumSync: Boolean = true)
                           extends OreModel(id, createdAt) with ProjectOwned {

  implicit val lang = Lang.defaultLang
  override type M = ProjectSettings
  override type T = ProjectSettingsTable

  /**
    * Returns the [[Project]]'s issues URL.
    *
    * @return Issues URL
    */
  def issues: Option[String] = this._issues

  /**
    * Sets the [[Project]]'s issues URL.
    *
    * @param issues Issues URL
    */
  def issues_=(issues: String) = {
    this._issues = Option(issues)
    if (isDefined) update(Issues)
  }

  /**
    * Returns the [[Project]]'s source code URL.
    *
    * @return Source code URL
    */
  def source: Option[String] = this._source

  /**
    * Sets the [[Project]]'s source code URL.
    *
    * @param source Source code URL
    */
  def source_=(source: String) = {
    this._source = Option(source)
    if (isDefined) update(Source)
  }

  /**
    * Returns the name of the [[Project]]'s license.
    *
    * @return Name of license
    */
  def licenseName: Option[String] = this._licenseName

  /**
    * Sets the name of the [[Project]]'s license.
    *
    * @param licenseName Name of license
    */
  def licenseName_=(licenseName: String) = {
    this._licenseName = Option(licenseName)
    if (isDefined) update(LicenseName)
  }

  /**
    * Returns the URL to the [[Project]]'s license.
    *
    * @return URL to project license
    */
  def licenseUrl: Option[String] = this._licenseUrl

  /**
    * Sets the URL to the [[Project]]'s license.
    *
    * @param licenseUrl URL to project license
    */
  def licenseUrl_=(licenseUrl: String) = {
    this._licenseUrl = Option(licenseUrl)
    if (isDefined) update(LicenseUrl)
  }

  /**
    * Returns if this [[Project]] should create new posts on the forums for noteworthy events.
    *
    * @return If posts should be created on the forum
    */
  def forumSync: Boolean = this._forumSync

  /**
    * Sets if this project should create post on the forums.
    *
    * @param shouldPost If posts should be created on the forum
    */
  def forumSync_=(shouldPost: Boolean): Unit = {
    this._forumSync = shouldPost
    if (isDefined) update(ForumSync)
  }

  /**
    * Saves a submitted [[ProjectSettingsForm]] to the [[Project]].
    *
    * @param formData Submitted settings
    * @param messages MessagesApi instance
    */
  //noinspection ComparingUnrelatedTypes
  def save(project: Project, formData: ProjectSettingsForm)(implicit messages: MessagesApi, fileManager: ProjectFiles, ec: ExecutionContext): Future[_] = {
    Logger.info("Saving project settings")
    Logger.info(formData.toString)

    project.setCategory(Categories.withName(formData.categoryName))
    project.setDescription(nullIfEmpty(formData.description))

    this.issues = nullIfEmpty(formData.issues)
    this.source = nullIfEmpty(formData.source)
    this.licenseUrl = nullIfEmpty(formData.licenseUrl)
    this.licenseUrl.foreach(url => this.licenseName = formData.licenseName)
    this.forumSync = formData.forumSync

    // Update the owner if needed
    val ownerSet = formData.ownerId.find(_ != project.ownerId) match {
      case None => Future.successful()
      case Some(ownerId) => this.userBase.get(ownerId).flatMap(user => project.setOwner(user.get))}
    ownerSet.flatMap { _ =>
      // Update icon
      if (formData.updateIcon) {
        fileManager.getPendingIconPath(project).foreach { pendingPath =>
          val iconDir = fileManager.getIconDir(project.ownerName, project.name)
          if (notExists(iconDir))
            createDirectories(iconDir)
          list(iconDir).forEach(delete(_))
          move(pendingPath, iconDir.resolve(pendingPath.getFileName))
        }
      }

      // Handle member changes
      if (project.isDefined) {
        // Add new roles
        val dossier = project.memberships
        Future.sequence(formData.build().map { role =>
          dossier.addRole(role.copy(projectId = project.id.get))
        }).flatMap { roles =>
          val notifications = roles.map { role =>
            Notification(
              userId = role.userId,
              originId = project.ownerId,
              notificationType = NotificationTypes.ProjectInvite,
              message = messages("notification.project.invite", role.roleType.title, project.name))
          }

          service.DB.db.run(TableQuery[NotificationTable] ++= notifications) // Bulk insert Notifications
        } flatMap { _ =>
          // Update existing roles
          val projectRoleTypes = RoleTypes.values.filter(_.roleClass.equals(classOf[ProjectRole]))

          val usersTable = TableQuery[UserTable]
          // Select member userIds
          service.DB.db.run(usersTable.filter(_.name inSetBind formData.userUps).map(_.id).result).map { userIds =>
            userIds zip formData.roleUps.map(role => projectRoleTypes.find(_.title.equals(role)).getOrElse(throw new RuntimeException("supplied invalid role type")))
          } map { _.map {
              case (userId, role) => updateMemberShip(userId).update(role)
            }
          } flatMap { updates =>
            service.DB.db.run(DBIO.sequence(updates))
          }
        }
      } else Future.successful()
    }
  }

  private def memberShipUpdate(userId: Rep[Int]) = {
    val rolesTable = TableQuery[ProjectRoleTable]

    for {
      m <- rolesTable if m.userId === userId
    } yield {
      m.roleType
    }
  }
  private lazy val updateMemberShip = Compiled(memberShipUpdate _)

  override def copyWith(id: Option[Int], theTime: Option[Timestamp]) = this.copy(id = id, createdAt = theTime)

}
