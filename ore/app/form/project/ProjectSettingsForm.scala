package form.project

import java.nio.file.Files
import java.nio.file.Files.{createDirectories, delete, list, move, notExists}

import db.impl.access.CompetitionBase
import ore.data.project.Category
import ore.data.user.notification.NotificationType
import ore.db.access.ModelView
import ore.models.user.{Notification, User}
import ore.db.{DbRef, Model, ModelService}
import ore.db.impl.schema.{ProjectRoleTable, UserTable}
import ore.db.impl.OrePostgresDriver.api._
import ore.models.competition.Competition
import ore.models.project.{Project, ProjectSettings}
import ore.models.project.factory.PendingProject
import ore.models.project.io.ProjectFiles
import ore.permission.role.Role
import ore.util.OreMDC
import ore.util.StringUtils.noneIfEmpty
import util.syntax._

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import com.typesafe.scalalogging.LoggerTakingImplicit
import slick.lifted.TableQuery

/**
  * Represents the configurable Project settings that can be submitted via a
  * form.
  */
case class ProjectSettingsForm(
    categoryName: String,
    issues: String,
    source: String,
    licenseName: String,
    licenseUrl: String,
    description: String,
    users: List[DbRef[User]],
    roles: List[String],
    userUps: List[String],
    roleUps: List[String],
    updateIcon: Boolean,
    ownerId: Option[DbRef[User]],
    competitionId: Option[DbRef[Competition]],
    forumSync: Boolean
) extends TProjectRoleSetBuilder {

  def savePending(settings: ProjectSettings, project: PendingProject)(
      implicit fileManager: ProjectFiles,
      mdc: OreMDC,
      service: ModelService[IO]
  ): IO[(PendingProject, ProjectSettings)] = {
    val queryOwnerName = for {
      u <- TableQuery[UserTable] if this.ownerId.getOrElse(project.ownerId).bind === u.id
    } yield u.name

    val updateProject = service.runDBIO(queryOwnerName.result).map { ownerName =>
      val newProj = project.copy(
        category = Category.values.find(_.title == this.categoryName).get,
        description = noneIfEmpty(this.description),
        ownerId = this.ownerId.getOrElse(project.ownerId),
        ownerName = ownerName.head
      )(project.config)

      newProj.pendingVersion = newProj.pendingVersion.copy(projectUrl = newProj.key)

      newProj
    }

    val updatedSettings = settings.copy(
      issues = noneIfEmpty(this.issues),
      source = noneIfEmpty(this.source),
      licenseUrl = noneIfEmpty(this.licenseUrl),
      licenseName = if (this.licenseUrl.nonEmpty) Some(this.licenseName) else settings.licenseName,
      forumSync = this.forumSync
    )

    updateProject.map { project =>
      // Update icon
      if (this.updateIcon) {
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

  def save(settings: Model[ProjectSettings], project: Model[Project], logger: LoggerTakingImplicit[OreMDC])(
      implicit fileManager: ProjectFiles,
      mdc: OreMDC,
      service: ModelService[IO],
      cs: ContextShift[IO]
  ): EitherT[IO, NonEmptyList[String], (Model[Project], Model[ProjectSettings])] = EitherT {
    import cats.instances.vector._
    logger.debug("Saving project settings")
    logger.debug(this.toString)
    val newOwnerId = this.ownerId.getOrElse(project.ownerId)

    val queryOwnerName = TableQuery[UserTable].filter(_.id === newOwnerId).map(_.name)

    val updateProject = service.runDBIO(queryOwnerName.result.head).flatMap { ownerName =>
      service.update(project)(
        _.copy(
          category = Category.values.find(_.title == this.categoryName).get,
          description = noneIfEmpty(this.description),
          ownerId = newOwnerId,
          ownerName = ownerName
        )
      )
    }

    val updateSettings = service.update(settings)(
      _.copy(
        issues = noneIfEmpty(this.issues),
        source = noneIfEmpty(this.source),
        licenseUrl = noneIfEmpty(this.licenseUrl),
        licenseName = if (this.licenseUrl.nonEmpty) Some(this.licenseName) else settings.licenseName,
        forumSync = this.forumSync
      )
    )

    val modelUpdates = (updateProject, updateSettings).parTupled

    modelUpdates.flatMap {
      case t @ (newProject, newProjectSettings) =>
        // Update icon
        if (this.updateIcon) {
          fileManager.getPendingIconPath(newProject).foreach { pendingPath =>
            val iconDir = fileManager.getIconDir(newProject.ownerName, newProject.name)
            if (notExists(iconDir))
              createDirectories(iconDir)
            list(iconDir).forEach(Files.delete(_))
            move(pendingPath, iconDir.resolve(pendingPath.getFileName))
          }
        }

        // Add new roles
        val dossier = newProject.memberships
        this
          .build()
          .toVector
          .parTraverse { role =>
            dossier.addRole(newProject)(role.userId, role.copy(projectId = newProject.id))
          }
          .flatMap { roles =>
            val notifications = roles.map { role =>
              Notification(
                userId = role.userId,
                originId = Some(newProject.ownerId),
                notificationType = NotificationType.ProjectInvite,
                messageArgs = NonEmptyList.of("notification.project.invite", role.role.title, newProject.name)
              )
            }

            service.bulkInsert(notifications)
          }
          .productR {
            // Update existing roles
            val usersTable = TableQuery[UserTable]
            // Select member userIds
            service
              .runDBIO(usersTable.filter(_.name.inSetBind(this.userUps)).map(_.id).result)
              .flatMap { userIds =>
                import cats.instances.list._
                val roles = this.roleUps.traverse { role =>
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
          .productR {
            OptionT
              .fromOption[IO](competitionId)
              .flatMap(ModelView.now(Competition).get(_))
              .toRight(NonEmptyList.one("error.competition.submit.invalidProject"))
              .flatMap(comp => CompetitionBase().submitProject(newProject, newProjectSettings, comp))
              .as(t)
              .value
          }
    }
  }

  private def memberShipUpdate(userId: Rep[DbRef[User]]) =
    TableQuery[ProjectRoleTable].filter(_.userId === userId).map(_.roleType)

  private lazy val updateMemberShip = Compiled(memberShipUpdate _)
}
