package form.project

import scala.language.higherKinds

import java.nio.file.Files
import java.nio.file.Files.{createDirectories, list, move, notExists}

import ore.data.project.Category
import ore.data.user.notification.NotificationType
import ore.models.user.{Notification, User}
import ore.db.{DbRef, Model, ModelService}
import ore.db.impl.schema.{ProjectRoleTable, UserTable}
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Project, ProjectSettings}
import ore.models.project.io.ProjectFiles
import ore.permission.role.Role
import ore.util.OreMDC
import ore.util.StringUtils.noneIfEmpty
import util.syntax._

import cats.{MonadError, Parallel}
import cats.data.{EitherT, NonEmptyList}
import cats.syntax.all._
import cats.instances.either._
import com.typesafe.scalalogging.LoggerTakingImplicit
import slick.lifted.TableQuery

/**
  * Represents the configurable Project settings that can be submitted via a
  * form.
  */
case class ProjectSettingsForm(
    categoryName: String,
    homepage: String,
    issues: String,
    source: String,
    support: String,
    licenseName: String,
    licenseUrl: String,
    description: String,
    users: List[DbRef[User]],
    roles: List[String],
    userUps: List[String],
    roleUps: List[String],
    updateIcon: Boolean,
    ownerId: Option[DbRef[User]],
    forumSync: Boolean,
    keywordsRaw: String
) extends TProjectRoleSetBuilder {

  def save[F[_], G[_]](settings: Model[ProjectSettings], project: Model[Project], logger: LoggerTakingImplicit[OreMDC])(
      implicit fileManager: ProjectFiles,
      mdc: OreMDC,
      service: ModelService[F],
      F: MonadError[F, Throwable],
      par: Parallel[F, G]
  ): EitherT[F, String, (Model[Project], Model[ProjectSettings])] = {
    import cats.instances.vector._
    logger.debug("Saving project settings")
    logger.debug(this.toString)
    val newOwnerId = this.ownerId.getOrElse(project.ownerId)

    val queryOwnerName = TableQuery[UserTable].filter(_.id === newOwnerId).map(_.name)

    val keywords = keywordsRaw.split(" ").iterator.map(_.trim).filter(_.nonEmpty).toList

    val checkedKeywordsF = EitherT.fromEither[F] {
      if (keywords.length > 5)
        Left("error.project.tooManyKeywords")
      else if (keywords.exists(_.length > 32))
        Left("error.maxLength")
      else
        Right(keywords)
    }

    val updateProject = checkedKeywordsF.semiflatMap { checkedKeywords =>
      service.runDBIO(queryOwnerName.result.head).flatMap { ownerName =>
        service.update(project)(
          _.copy(
            category = Category.values.find(_.title == this.categoryName).get,
            description = noneIfEmpty(this.description),
            ownerId = newOwnerId,
            ownerName = ownerName,
            keywords = checkedKeywords
          )
        )
      }
    }

    val updateSettings = service.update(settings)(
      _.copy(
        homepage = noneIfEmpty(this.homepage),
        issues = noneIfEmpty(this.issues),
        source = noneIfEmpty(this.source),
        support = noneIfEmpty(this.support),
        licenseUrl = noneIfEmpty(this.licenseUrl),
        licenseName = if (this.licenseUrl.nonEmpty) Some(this.licenseName) else settings.licenseName,
        forumSync = this.forumSync
      )
    )

    val modelUpdates = EitherT((updateProject.value, updateSettings.map(_.asRight[String])).parTupled.map(_.tupled))

    modelUpdates.semiflatMap { t =>
      // Update icon
      if (this.updateIcon) {
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
      this
        .build()
        .toVector
        .parTraverse { role =>
          dossier.addRole(project)(role.userId, role.copy(projectId = project.id))
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
            .runDBIO(usersTable.filter(_.name.inSetBind(this.userUps)).map(_.id).result)
            .flatMap { userIds =>
              import cats.instances.list._
              val roles = this.roleUps.traverse { role =>
                Role.projectRoles
                  .find(_.value == role)
                  .fold(F.raiseError[Role](new RuntimeException("supplied invalid role type")))(F.pure)
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

  private def memberShipUpdate(userId: Rep[DbRef[User]]) =
    TableQuery[ProjectRoleTable].filter(_.userId === userId).map(_.roleType)

  private lazy val updateMemberShip = Compiled(memberShipUpdate _)
}
