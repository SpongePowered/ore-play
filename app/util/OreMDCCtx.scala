package util

import scala.language.implicitConversions

import controllers.sugar.Requests._
import models.project.Project
import models.user.{Organization, User}

import com.typesafe.scalalogging.CanLog
import org.slf4j.MDC

sealed trait OreMDCCtx
object OreMDCCtx {
  case object NoMDCCtx                             extends OreMDCCtx
  case class RequestMDCCtx(request: OreRequest[_]) extends OreMDCCtx

  object Implicits {
    implicit val noCtx: OreMDCCtx = NoMDCCtx
  }

  implicit def oreRequestToCtx[A <: OreRequest[_]](implicit request: A): OreMDCCtx = RequestMDCCtx(request)

  implicit val canLogOreMDCCtx: CanLog[OreMDCCtx] = new CanLog[OreMDCCtx] {

    def putUser(user: User): Unit = {
      MDC.put("currentUserId", user.id.value.toString)
      MDC.put("currentUserName", user.name)
    }

    def putProject(project: Project): Unit = {
      MDC.put("currentProjectId", project.id.value.toString)
      MDC.put("currentProjectSlug", project.slug)
    }

    def putOrg(orga: Organization): Unit = {
      MDC.put("currentOrgaId", orga.id.value.toString)
      MDC.put("currentOrgaName", orga.name)
    }

    override def logMessage(originalMsg: String, a: OreMDCCtx): String = {
      a match {
        case RequestMDCCtx(req) =>
          //I'd prefer to do these with one match, but for some reason Scala doesn't let me
          req match {
            case req: ScopedRequest[_] => putUser(req.user)
            case _                     => req.currentUser.foreach(putUser)
          }

          req match {
            case req: ProjectRequest[_] => putProject(req.project)
            case _                      =>
          }

          req match {
            case req: OrganizationRequest[_] => putOrg(req.data.orga)
            case _                           =>
          }

        case NoMDCCtx =>
      }

      originalMsg
    }

    override def afterLog(a: OreMDCCtx): Unit = {
      MDC.remove("currentUserId")
      MDC.remove("currentUserName")
      MDC.remove("currentProjectId")
      MDC.remove("currentProjectSlug")
      MDC.remove("currentOrgaId")
      MDC.remove("currentOrgaName")
    }
  }
}
