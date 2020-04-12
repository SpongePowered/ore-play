package controllers.apiv2

import java.util.UUID

import play.api.http.HttpErrorHandler
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent, Result}

import controllers.OreControllerComponents
import controllers.apiv2.helpers.{APIScope, ApiError, ApiErrors}
import db.impl.query.APIV2Queries
import models.protocols.APIV2
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.ApiKeyTable
import ore.models.user.SecurityLogEvent
import ore.permission.{NamedPermission, Permission}

import cats.data.NonEmptyList
import cats.syntax.all._
import com.github.tminglei.slickpg.InetString
import io.circe.{Codec, Json}
import io.circe.syntax._
import io.circe.derivation.annotations.SnakeCaseJsonCodec
import zio.interop.catz._
import zio.{IO, ZIO}

class Keys(
    val errorHandler: HttpErrorHandler,
    lifecycle: ApplicationLifecycle
)(
    implicit oreComponents: OreControllerComponents
) extends AbstractApiV2Controller(lifecycle) {
  import Keys._

  def createKey(): Action[KeyToCreate] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope)(parseCirce.decodeJson[KeyToCreate]).asyncF {
      implicit request =>
        val permsVal = NamedPermission.parseNamed(request.body.permissions).toValidNel("Invalid permission name")
        val nameVal = Some(request.body.name)
          .filter(_.nonEmpty)
          .toValidNel("Name was empty")
          .ensure(NonEmptyList.one("Name too long"))(_.length < 255)

        (permsVal, nameVal)
          .mapN { (perms, name) =>
            val perm     = Permission(perms.map(_.permission): _*)
            val isSubKey = request.apiInfo.key.forall(_.isSubKey(perm))

            if (!isSubKey) {
              IO.fail(BadRequest(ApiError("Not enough permissions to create that key")))
            } else {
              val tokenIdentifier = UUID.randomUUID().toString
              val token           = UUID.randomUUID().toString
              val ownerId         = request.user.get.id.value

              val nameTaken =
                TableQuery[ApiKeyTable].filter(t => t.name === name && t.ownerId === ownerId).exists.result

              val ifTaken = IO.fail(Conflict(ApiError("Name already taken")))

              val ifFree = for {
                _ <- service.runDbCon(APIV2Queries.createApiKey(name, ownerId, tokenIdentifier, token, perm).run)
                _ <- service.insert(
                  SecurityLogEvent(
                    ownerId,
                    InetString(request.remoteAddress),
                    request.headers.get("User-Agent"),
                    ???,
                    SecurityLogEvent.EventType.CreateApiKey,
                    Some(
                      Json.obj(
                        "new_key" -> Json.obj(
                          "name" := name,
                          "permissions" := perms.map(_.entryName),
                          "identifier" := tokenIdentifier
                        ),
                        "used_key" := request.apiInfo.key.map { creator =>
                          Json.obj(
                            "name" := creator.name,
                            "permissions" := creator.namedRawPermissions.map(_.entryName),
                            "identifier" := creator.tokenIdentifier
                          )
                        }
                      )
                    )
                  )
                )
              } yield Ok(CreatedApiKey(s"$tokenIdentifier.$token", perm.toNamedSeq))

              (service.runDBIO(nameTaken): IO[Result, Boolean]).ifM(ifTaken, ifFree)
            }
          }
          .leftMap((ApiErrors.apply _).andThen(BadRequest.apply(_)).andThen(IO.fail))
          .merge
    }

  def deleteKey(name: String): Action[AnyContent] =
    ApiAction(Permission.EditApiKeys, APIScope.GlobalScope).asyncF { implicit request =>
      for {
        user <- ZIO
          .fromOption(request.user)
          .asError(BadRequest(ApiError("Public keys can't be used to delete")))
        rowsAffected <- service.runDbCon(APIV2Queries.deleteApiKey(name, user.id.value).run)
        _ <- ZIO.when(rowsAffected != 0)(
          service.insert(
            SecurityLogEvent(
              request.user.get.id.value,
              InetString(request.remoteAddress),
              request.headers.get("User-Agent"),
              ???,
              SecurityLogEvent.EventType.DeleteApiKey,
              Some(
                Json.obj(
                  "deleted_key" -> Json.obj(
                    "name" := name
                  ),
                  "used_key" := request.apiInfo.key.map { creator =>
                    Json.obj(
                      "name" := creator.name,
                      "permissions" := creator.namedRawPermissions.map(_.entryName),
                      "identifier" := creator.tokenIdentifier
                    )
                  }
                )
              )
            )
          )
        )
      } yield if (rowsAffected == 0) NotFound else NoContent
    }
}
object Keys {

  implicit val namedPermissionCodec: Codec[NamedPermission] = APIV2.enumCodec(NamedPermission)(_.entryName)

  @SnakeCaseJsonCodec case class KeyToCreate(name: String, permissions: Seq[String])
  @SnakeCaseJsonCodec case class CreatedApiKey(key: String, perms: Seq[NamedPermission])
}
