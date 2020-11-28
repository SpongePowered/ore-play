package controllers.apiv2

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, APIScopeType}
import ore.permission.{NamedPermission, Permission}

import io.circe.derivation.annotations.SnakeCaseJsonCodec

class Permissions(
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {
  import Permissions._

  def showPermissions(
      projectOwner: Option[String],
      projectSlug: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    CachingApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
      permissionsInApiScope(projectOwner, projectSlug, organizationName).map {
        case (scope, perms) =>
          Ok(
            KeyPermissions(
              scope.tpe,
              perms.toNamedSeq.toList
            )
          )
      }
    }

  def has(
      checkPermissions: Seq[NamedPermission],
      projectOwner: Option[String],
      projectSlug: Option[String],
      organizationName: Option[String]
  )(
      check: (Seq[Permission], Permission) => Boolean
  ): Action[AnyContent] =
    CachingApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
      permissionsInApiScope(projectOwner, projectSlug, organizationName).map {
        case (scope, perms) =>
          Ok(PermissionCheck(scope.tpe, check(checkPermissions.map(_.permission), perms)))
      }
    }

  def hasAll(
      permissions: Seq[NamedPermission],
      projectOwner: Option[String],
      projectSlug: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has(permissions, projectOwner, projectSlug, organizationName)((seq, perm) => seq.forall(perm.has(_)))

  def hasAny(
      permissions: Seq[NamedPermission],
      projectOwner: Option[String],
      projectSlug: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has(permissions, projectOwner, projectSlug, organizationName)((seq, perm) => seq.exists(perm.has(_)))
}
object Permissions {
  import models.protocols.APIV2.namedPermissionCodec

  @SnakeCaseJsonCodec case class KeyPermissions(
      `type`: APIScopeType,
      permissions: List[NamedPermission]
  )

  @SnakeCaseJsonCodec case class PermissionCheck(
      `type`: APIScopeType,
      result: Boolean
  )
}
