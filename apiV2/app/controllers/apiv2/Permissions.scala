package controllers.apiv2

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, APIScopeType}
import models.protocols.APIV2
import ore.permission.{NamedPermission, Permission}

import io.circe._
import io.circe.generic.extras.ConfiguredJsonCodec

class Permissions(
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {
  import Permissions._

  def showPermissions(pluginId: Option[String], organizationName: Option[String]): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF("showPermissions")(pluginId, organizationName) {
        permissionsInCreatedApiScope(pluginId, organizationName).map {
          case (scope, perms) =>
            Ok(
              KeyPermissions(
                scope.tpe,
                perms.toNamedSeq.toList
              )
            )
        }
      }
    }

  def has(
      cacheKey: String,
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  )(
      check: (Seq[NamedPermission], Permission) => Boolean
  ): Action[AnyContent] =
    ApiAction(Permission.None, APIScope.GlobalScope).asyncF { implicit request =>
      cachingF(cacheKey)(permissions, pluginId, organizationName) {
        permissionsInCreatedApiScope(pluginId, organizationName).map {
          case (scope, perms) =>
            Ok(PermissionCheck(scope.tpe, check(permissions, perms)))
        }
      }
    }

  def hasAll(
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has("hasAll", permissions, pluginId, organizationName)((seq, perm) => seq.forall(p => perm.has(p.permission)))

  def hasAny(
      permissions: Seq[NamedPermission],
      pluginId: Option[String],
      organizationName: Option[String]
  ): Action[AnyContent] =
    has("hasAny", permissions, pluginId, organizationName)((seq, perm) => seq.exists(p => perm.has(p.permission)))
}
object Permissions {

  import APIV2.circeConfig

  implicit val namedPermissionCodec: Codec[NamedPermission] = APIV2.enumCodec(NamedPermission)(_.entryName)

  @ConfiguredJsonCodec case class KeyPermissions(
      `type`: APIScopeType,
      permissions: List[NamedPermission]
  )

  @ConfiguredJsonCodec case class PermissionCheck(
      `type`: APIScopeType,
      result: Boolean
  )
}
