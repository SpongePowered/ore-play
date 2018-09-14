package models.project

import java.nio.file.Files._
import java.sql.Timestamp
import java.time.Instant

import db.impl.OrePostgresDriver.api._
import db.impl._
import cats.instances.future._
import form.project.ProjectSettingsForm
import models.user.Notification
import models.user.role.ProjectRole
import ore.permission.role.RoleType
import ore.project.io.ProjectFiles
import ore.project.{Categories, ProjectMember, ProjectOwned}
import ore.user.notification.NotificationTypes
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, MessagesApi}
import slick.lifted.TableQuery
import util.StringUtils._

import scala.concurrent.{ExecutionContext, Future}

import db.{Model, ModelService}
import ore.user.MembershipDossier
import db.{ObjectId, ObjectReference, ObjectTimestamp}

/**
  * Represents a [[Project]]'s settings.
  *
  * @param id           Unique ID
  * @param createdAt    Instant of creation
  * @param projectId    ID of project settings belong to
  * @param homepage     Project homepage
  * @param issues      Project issues URL
  * @param source      Project source URL
  * @param licenseName Project license name
  * @param licenseUrl  Project license URL
  */
case class ProjectSettings(id: ObjectId = ObjectId.Uninitialized,
                           createdAt: ObjectTimestamp = ObjectTimestamp.Uninitialized,
                           projectId: ObjectReference = -1,
                           homepage: Option[String] = None,
                           issues: Option[String] = None,
                           source: Option[String] = None,
                           licenseName: Option[String] = None,
                           licenseUrl: Option[String] = None,
                           forumSync: Boolean = true)
                           extends Model with ProjectOwned {

  override type M = ProjectSettings
  override type T = ProjectSettingsTable

  /**
    * Saves a submitted [[ProjectSettingsForm]] to the [[Project]].
    *
    * @param formData Submitted settings
    * @param messages MessagesApi instance
    */
  //noinspection ComparingUnrelatedTypes
  def save(project: Project, formData: ProjectSettingsForm)(implicit cache: AsyncCacheApi, messages: MessagesApi, fileManager: ProjectFiles, ec: ExecutionContext, service: ModelService): Future[(Project, ProjectSettings)] = {
    Logger.debug("Saving project settings")
    Logger.debug(formData.toString)

    def updateIfDefined[A <: Model](a: A) = if(a.isDefined) service.update(a) else Future.successful(a)

    val updateProject = updateIfDefined(
      project.copy(
        category = Categories.withName(formData.categoryName),
        description = Option(nullIfEmpty(formData.description)),
        ownerId = formData.ownerId.getOrElse(project.ownerId)
      )
    )

    val updateSettings = updateIfDefined(
      copy(
        issues = Option(nullIfEmpty(formData.issues)),
        source = Option(nullIfEmpty(formData.source)),
        licenseUrl = Option(nullIfEmpty(formData.licenseUrl)),
        licenseName = if(formData.licenseUrl.nonEmpty) Some(formData.licenseName) else licenseName,
        forumSync = formData.forumSync
      )
    )

    val modelUpdates = updateProject.zip(updateSettings)

    modelUpdates.flatMap { t =>
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
        val dossier: MembershipDossier {
          type MembersTable = ProjectMembersTable

          type MemberType = ProjectMember

          type RoleTable = ProjectRoleTable

          type ModelType = Project

          type RoleType = ProjectRole
        } = project.memberships
        Future.sequence(formData.build().map { role =>
          dossier.addRole(role.copy(projectId = project.id.value))
        }).flatMap { roles =>
          val notifications = roles.map { role =>
            Notification(
              createdAt = ObjectTimestamp(Timestamp.from(Instant.now())),
              userId = role.userId,
              originId = project.ownerId,
              notificationType = NotificationTypes.ProjectInvite,
              messageArgs = List("notification.project.invite", role.roleType.title, project.name))
          }

          service.DB.db.run(TableQuery[NotificationTable] ++= notifications) // Bulk insert Notifications
        } flatMap { _ =>
          // Update existing roles
          val projectRoleTypes = RoleType.values.filter(_.roleClass.equals(classOf[ProjectRole]))

          val usersTable = TableQuery[UserTable]
          // Select member userIds
          service.DB.db.run(usersTable.filter(_.name inSetBind formData.userUps).map(_.id).result).map { userIds =>
            userIds zip formData.roleUps.map(role => projectRoleTypes.find(_.title.equals(role)).getOrElse(throw new RuntimeException("supplied invalid role type")))
          } map { _.map {

              case (userId, role) => updateMemberShip(userId).update(role)
            }
          } flatMap { updates =>
            service.DB.db.run(DBIO.sequence(updates)).map(_ => t)
          }
        }
      } else Future.successful(t)
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

  override def copyWith(id: ObjectId, theTime: ObjectTimestamp): ProjectSettings = this.copy(id = id, createdAt = theTime)
}
