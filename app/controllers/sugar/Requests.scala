package controllers.sugar

import play.api.mvc.{Request, WrappedRequest}

import models.project.Project
import models.user.{Organization, User}
import models.viewhelper._
import ore.permission.scope.ScopeSubject

/**
  * Contains the custom WrappedRequests used by Ore.
  */
object Requests {

  /**
    * Base Request for Ore that holds all data needed for rendering the header
    */
  trait OreRequest[A] extends WrappedRequest[A] {
    def headerData: HeaderData
    def currentUser: Option[User] = headerData.currentUser
    def hasUser: Boolean          = headerData.currentUser.isDefined
  }

  class SimpleOreRequest[A](val headerData: HeaderData, val request: Request[A])
      extends WrappedRequest[A](request)
      with OreRequest[A]

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
  class AuthRequest[A](val user: User, val headerData: HeaderData, request: Request[A])
      extends WrappedRequest[A](request)
      with OreRequest[A]
      with ScopedRequest[A]

  /**
    * A request that holds a [[Project]].
    *
    * @param data Project data to hold
    * @param scoped scoped Project data to hold
    * @param request Request to wrap
    */
  class ProjectRequest[A](
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
  case class AuthedProjectRequest[A](
      override val data: ProjectData,
      override val scoped: ScopedProjectData,
      override val headerData: HeaderData,
      override val request: AuthRequest[A]
  ) extends ProjectRequest[A](data, scoped, headerData, request)
      with ScopedRequest[A]
      with OreRequest[A] {

    override def user: User            = request.user
    override val subject: ScopeSubject = this.data.project
  }

  /**
    * A request that holds an [[Organization]].
    *
    * @param data Organization data to hold
    * @param scoped scoped Organization data to hold
    * @param request      Request to wrap
    */
  class OrganizationRequest[A](
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
  case class AuthedOrganizationRequest[A](
      override val data: OrganizationData,
      override val scoped: ScopedOrganizationData,
      override val headerData: HeaderData,
      override val request: AuthRequest[A]
  ) extends OrganizationRequest[A](data, scoped, headerData, request)
      with ScopedRequest[A]
      with OreRequest[A] {
    override def user: User            = request.user
    override val subject: ScopeSubject = this.data.orga
  }
}
