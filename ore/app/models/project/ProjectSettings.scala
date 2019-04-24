package models.project

import java.nio.file.Files
import java.nio.file.Files._

import db.impl.DefaultModelCompanion
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectRoleTable, ProjectSettingsTable, UserTable}
import form.project.ProjectSettingsForm
import models.user.{Notification, User}
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import ore.permission.role.Role
import ore.project.factory.PendingProject
import ore.project.io.ProjectFiles
import ore.project.{Category, ProjectOwned}
import ore.user.notification.NotificationType
import util.OreMDC
import util.StringUtils._

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.typesafe.scalalogging
import slick.lifted.TableQuery

/**
  * Represents a [[Project]]'s settings.
  *
  * @param projectId    ID of project settings belong to
  * @param homepage     Project homepage
  * @param issues      Project issues URL
  * @param source      Project source URL
  * @param licenseName Project license name
  * @param licenseUrl  Project license URL
  */
case class ProjectSettings(
    projectId: DbRef[Project],
    homepage: Option[String] = None,
    issues: Option[String] = None,
    source: Option[String] = None,
    licenseName: Option[String] = None,
    licenseUrl: Option[String] = None,
    forumSync: Boolean = true
) {

  /**
    * Saves a submitted [[ProjectSettingsForm]] to the [[PendingProject]].
    *
    * @param formData Submitted settings
    * @param messages MessagesApi instance
    */
  //noinspection ComparingUnrelatedTypes
  def save(project: PendingProject, formData: ProjectSettingsForm)(
      implicit fileManager: ProjectFiles,
      mdc: OreMDC,
      service: ModelService
  ): IO[(PendingProject, ProjectSettings)] = {
    val queryOwnerName = for {
      u <- TableQuery[UserTable] if formData.ownerId.getOrElse(project.ownerId).bind === u.id
    } yield u.name

    val updateProject = service.runDBIO(queryOwnerName.result).map { ownerName =>
      val newProj = project.copy(
        category = Category.values.find(_.title == formData.categoryName).get,
        description = noneIfEmpty(formData.description),
        ownerId = formData.ownerId.getOrElse(project.ownerId),
        ownerName = ownerName.head
      )(project.config)

      newProj.pendingVersion = newProj.pendingVersion.copy(projectUrl = newProj.key)

      newProj
    }

    val updatedSettings = copy(
      issues = noneIfEmpty(formData.issues),
      source = noneIfEmpty(formData.source),
      licenseUrl = noneIfEmpty(formData.licenseUrl),
      licenseName = if (formData.licenseUrl.nonEmpty) Some(formData.licenseName) else licenseName,
      forumSync = formData.forumSync
    )

    updateProject.map { project =>
      // Update icon
      if (formData.updateIcon) {
        fileManager.getPendingIconPath(project.ownerName, project.name).foreach { pendingPath =>
          val iconDir = fileManager.getIconDir(project.ownerName, project.name)
          if (notExists(iconDir))
            createDirectories(iconDir)
          list(iconDir).forEach(delete(_))
          move(pendingPath, iconDir.resolve(pendingPath.getFileName))
        }
      }

      (project, updatedSettings)
    }
  }
}
object ProjectSettings
    extends DefaultModelCompanion[ProjectSettings, ProjectSettingsTable](TableQuery[ProjectSettingsTable]) {

  implicit val query: ModelQuery[ProjectSettings] = ModelQuery.from(this)

  implicit val isProjectOwned: ProjectOwned[ProjectSettings] = (a: ProjectSettings) => a.projectId

  private val Logger    = scalalogging.Logger("ProjectSettings")
  private val MDCLogger = scalalogging.Logger.takingImplicit[OreMDC](Logger.underlying)

  implicit class ProjectSettingsModelOps(private val self: Model[ProjectSettings]) extends AnyVal {

    /**
      * Saves a submitted [[ProjectSettingsForm]] to the [[Project]].
      *
      * @param formData Submitted settings
      * @param messages MessagesApi instance
      */
    def save(project: Model[Project], formData: ProjectSettingsForm)(
        implicit fileManager: ProjectFiles,
        mdc: OreMDC,
        service: ModelService,
        cs: ContextShift[IO]
    ): IO[(Model[Project], Model[ProjectSettings])] = {
      import cats.instances.vector._
      MDCLogger.debug("Saving project settings")
      MDCLogger.debug(formData.toString)
      val newOwnerId = formData.ownerId.getOrElse(project.ownerId)

      val queryOwnerName = TableQuery[UserTable].filter(_.id === newOwnerId).map(_.name)

      val updateProject = service.runDBIO(queryOwnerName.result.head).flatMap { ownerName =>
        service.update(project)(
          _.copy(
            category = Category.values.find(_.title == formData.categoryName).get,
            description = noneIfEmpty(formData.description),
            ownerId = newOwnerId,
            ownerName = ownerName
          )
        )
      }

      val updateSettings = service.update(self)(
        _.copy(
          issues = noneIfEmpty(formData.issues),
          source = noneIfEmpty(formData.source),
          licenseUrl = noneIfEmpty(formData.licenseUrl),
          licenseName = if (formData.licenseUrl.nonEmpty) Some(formData.licenseName) else self.licenseName,
          forumSync = formData.forumSync
        )
      )

      val modelUpdates = (updateProject, updateSettings).parTupled

      modelUpdates.flatMap { t =>
        // Update icon
        if (formData.updateIcon) {
          fileManager.getPendingIconPath(project).foreach { pendingPath =>
            val iconDir = fileManager.getIconDir(project.ownerName, project.name)
            if (notExists(iconDir))
              createDirectories(iconDir)
            list(iconDir).forEach(Files.delete(_))
            move(pendingPath, iconDir.resolve(pendingPath.getFileName))
          }
        }

        // Add new roles
        val dossier = project.memberships
        formData
          .build()
          .toVector
          .parTraverse { role =>
            dossier.addRole(project, role.userId, role.copy(projectId = project.id))
          }
          .flatMap { roles =>
            val notifications = roles.map { role =>
              Notification(
                userId = role.userId,
                originId = Some(project.ownerId),
                notificationType = NotificationType.ProjectInvite,
                messageArgs = NonEmptyList.of("notification.project.invite", role.role.title, project.name)
              )
            }

            service.bulkInsert(notifications)
          }
          .productR {
            // Update existing roles
            val usersTable = TableQuery[UserTable]
            // Select member userIds
            service
              .runDBIO(usersTable.filter(_.name.inSetBind(formData.userUps)).map(_.id).result)
              .flatMap { userIds =>
                import cats.instances.list._
                val roles = formData.roleUps.traverse { role =>
                  Role.projectRoles
                    .find(_.value == role)
                    .fold(IO.raiseError[Role](new RuntimeException("supplied invalid role type")))(IO.pure)
                }

                roles.map(xs => userIds.zip(xs))
              }
              .map {
                _.map {
                  case (userId, role) => updateMemberShip(userId).update(role)
                }
              }
              .flatMap(updates => service.runDBIO(DBIO.sequence(updates)).as(t))
          }
      }
    }
  }

  private def memberShipUpdate(userId: Rep[DbRef[User]]) =
    TableQuery[ProjectRoleTable].filter(_.userId === userId).map(_.roleType)

  private lazy val updateMemberShip = Compiled(memberShipUpdate _)
}
