package controllers.sugar

import models.project.Project
import models.user.{Organization, User}
import models.viewhelper._
import ore.permission.Permission
import ore.permission.scope.ScopeSubject
import play.api.mvc.{AnyContent, Request, WrappedRequest}

/**
  * Contains the custom WrappedRequests used by Ore.
  */
object Requests {

  /**
    * Base Request for Ore that holds all data needed for rendering the header
    *
    * @param data the HeaderData
    * @param request the request to wrap
    */
  class OreRequest[A](val data: HeaderData, val request: Request[A]) extends WrappedRequest[A](request) {
    def currentUser = data.currentUser
    def hasUser = data.currentUser.isDefined
  }

  /** Represents a Request with a [[User]] and [[ScopeSubject]] */
  trait ScopedRequest[A] extends WrappedRequest[A] {
    def user: User
    def subject: ScopeSubject = this.user
  }

  /**
    * A request that hold the currently authenticated [[User]].
    *
    * @param user     Authenticated user
    * @param request  Request to wrap
    */
  class AuthRequest[A](override val user: User, data: HeaderData, request: Request[A])
    extends OreRequest[A](data, request) with ScopedRequest[A]

  /**
    * A request that holds a [[Project]].
    *
    * @param project Project to hold
    * @param request Request to wrap
    */
  class ProjectRequest[A](val data: ProjectData, val scoped: ScopedProjectData, val request: OreRequest[A]) extends WrappedRequest[A](request)

  /**
    * A request that holds a Project and a [[AuthRequest]].
    *
    * @param project Project to hold
    * @param request An [[AuthRequest]]
    */
  case class AuthedProjectRequest[A](override val data: ProjectData, override val scoped: ScopedProjectData, override val request: AuthRequest[A])
    extends ProjectRequest[A](data, scoped, request)
      with ScopedRequest[A] {
    override def user: User = request.user
    override val subject: ScopeSubject = this.data.project
  }

  /**
    * A request that holds an [[Organization]].
    *
    * @param organization Organization to hold
    * @param request      Request to wrap
    */
  class OrganizationRequest[A](val data: OrganizationData, val scoped: ScopedOrganizationData, val request: OreRequest[A]) extends WrappedRequest[A](request)

  /**
    * A request that holds an [[Organization]] and an [[AuthRequest]].
    *
    * @param organization Organization to hold
    * @param request      Request to wrap
    */
  case class AuthedOrganizationRequest[A](override val data: OrganizationData, override val scoped: ScopedOrganizationData, override val request: AuthRequest[A])
    extends OrganizationRequest[A](data, scoped, request)
      with ScopedRequest[A] {
    override def user: User = request.user
    override val subject: ScopeSubject = this.data.orga
  }
}
