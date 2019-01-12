package util

import scala.language.implicitConversions

import controllers.sugar.Requests._
import models.project.Project
import models.user.{Organization, User}

import com.typesafe.scalalogging.CanLog
import org.slf4j.MDC

sealed trait OreMDC
object OreMDC {
  case object NoMDC                             extends OreMDC
  case class RequestMDC(request: OreRequest[_]) extends OreMDC

  object Implicits {
    implicit val noCtx: OreMDC = NoMDC
  }

  implicit def oreRequestToCtx[A <: OreRequest[_]](implicit request: A): OreMDC = RequestMDC(request)

  implicit val canLogOreMDCCtx: CanLog[OreMDC] = new CanLog[OreMDC] {

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

    override def logMessage(originalMsg: String, a: OreMDC): String = {
      a match {
        case RequestMDC(req) =>
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

        case NoMDC =>
      }

      originalMsg
    }

    override def afterLog(a: OreMDC): Unit = {
      MDC.remove("currentUserId")
      MDC.remove("currentUserName")
      MDC.remove("currentProjectId")
      MDC.remove("currentProjectSlug")
      MDC.remove("currentOrgaId")
      MDC.remove("currentOrgaName")
    }
  }
}
