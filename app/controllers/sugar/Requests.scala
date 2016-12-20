package controllers.sugar

import play.api.mvc.{Request, WrappedRequest}

import models.competition.Competition
import models.project.Project
import models.user.{Organization, User}
import models.viewhelper._
import ore.permission.scope.{GlobalScope, HasScope}

/**
  * Contains the custom WrappedRequests used by Ore.
  */
object Requests {

  /**
    * Base Request for Ore that holds all data needed for rendering the header
    */
  sealed trait OreRequest[A] extends WrappedRequest[A] {
    def headerData: HeaderData
    def currentUser: Option[User] = headerData.currentUser
    def hasUser: Boolean          = headerData.currentUser.isDefined
  }

  final class SimpleOreRequest[A](val headerData: HeaderData, val request: Request[A])
      extends WrappedRequest[A](request)
      with OreRequest[A]

  /** Represents a Request with a [[User]] and subject */
  sealed trait ScopedRequest[A] extends OreRequest[A] {
    type Subject
    def user: User
    def subject: Subject
  }
  object ScopedRequest {
    type Aux[A, Subject0] = ScopedRequest[A] { type Subject = Subject0 }
  }

  sealed trait UserScopedRequest[A] extends ScopedRequest[A] {
    type Subject = User
    def subject: User = user
  }
  object UserScopedRequest {
    implicit def hasScope: HasScope[UserScopedRequest[_]] = (_: UserScopedRequest[_]) => GlobalScope
  }

  /**
    * A request that hold the currently authenticated [[User]].
    *
    * @param user     Authenticated user
    * @param request  Request to wrap
    */
  final class AuthRequest[A](val user: User, val headerData: HeaderData, request: Request[A])
      extends WrappedRequest[A](request)
      with OreRequest[A]
      with UserScopedRequest[A]

  /**
    * A request that holds a [[Project]].
    *
    * @param data Project data to hold
    * @param scoped scoped Project data to hold
    * @param request Request to wrap
    */
  sealed class ProjectRequest[A](
      val data: ProjectData,
      val scoped: ScopedProjectData,
      val headerData: HeaderData,
      val request: Request[A]
  ) extends WrappedRequest[A](request)
      with OreRequest[A] {

    def project: Project = data.project
  }

  /**
    * A request that holds a Project and a [[AuthRequest]].
    *
    * @param data Project data to hold
    * @param scoped scoped Project data to hold
    * @param request An [[AuthRequest]]
    */
  final case class AuthedProjectRequest[A](
      override val data: ProjectData,
      override val scoped: ScopedProjectData,
      override val headerData: HeaderData,
      override val request: AuthRequest[A]
  ) extends ProjectRequest[A](data, scoped, headerData, request)
      with ScopedRequest[A]
      with OreRequest[A] {

    type Subject = Project
    override def user: User       = request.user
    override val subject: Project = this.data.project
  }
  object AuthedProjectRequest {
    implicit def hasScope: HasScope[AuthedProjectRequest[_]] = HasScope.projectScope(_.subject.id.value)
  }

  /**
    * A request that holds an [[Organization]].
    *
    * @param data Organization data to hold
    * @param scoped scoped Organization data to hold
    * @param request      Request to wrap
    */
  sealed class OrganizationRequest[A](
      val data: OrganizationData,
      val scoped: ScopedOrganizationData,
      val headerData: HeaderData,
      val request: Request[A]
  ) extends WrappedRequest[A](request)
      with OreRequest[A]

  /**
    * A request that holds an [[Organization]] and an [[AuthRequest]].
    *
    * @param data Organization data to hold
    * @param scoped scoped Organization data to hold
    * @param request      Request to wrap
    */
  final case class AuthedOrganizationRequest[A](
      override val data: OrganizationData,
      override val scoped: ScopedOrganizationData,
      override val headerData: HeaderData,
      override val request: AuthRequest[A]
  ) extends OrganizationRequest[A](data, scoped, headerData, request)
      with ScopedRequest[A]
      with OreRequest[A] {
    type Subject = Organization
    override def user: User            = request.user
    override val subject: Organization = this.data.orga
  }
  object AuthedOrganizationRequest {
    implicit def hasScope: HasScope[AuthedOrganizationRequest[_]] = HasScope.orgScope(_.subject.id.value)
  }

  /**
    * A request with a [[Competition]].
    *
    * @param competition  Competition
    * @param request      Wrapped request
    */
  sealed class CompetitionRequest[A](val competition: Competition, val headerData: HeaderData, val request: Request[A])
      extends WrappedRequest[A](request)
      with OreRequest[A]

  /**
    * An authenticated request with a [[Competition]].
    *
    * @param competition  Competition
    * @param request      Wrapped request
    */
  final case class AuthedCompetitionRequest[A](
      override val competition: Competition,
      override val headerData: HeaderData,
      override val request: AuthRequest[A]
  ) extends CompetitionRequest(competition, headerData, request)
      with ScopedRequest[A]
      with OreRequest[A] {
    type Subject = Competition
    override def user: User           = request.user
    override def subject: Competition = competition
  }
}
