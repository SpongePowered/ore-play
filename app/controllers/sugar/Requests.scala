package controllers.sugar

import play.api.mvc.{Request, WrappedRequest}

import db.DbModel
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
    def currentUser: Option[DbModel[User]] = headerData.currentUser
    def hasUser: Boolean                   = headerData.currentUser.isDefined
  }

  final class SimpleOreRequest[A](val headerData: HeaderData, val request: Request[A])
      extends WrappedRequest[A](request)
      with OreRequest[A]

  /** Represents a Request with a [[User]] and subject */
  sealed trait ScopedRequest[A] extends OreRequest[A] {
    type Subject
    def user: DbModel[User]
    def subject: Subject
  }
  object ScopedRequest {
    type Aux[A, Subject0] = ScopedRequest[A] { type Subject = Subject0 }
  }

  sealed trait UserScopedRequest[A] extends ScopedRequest[A] {
    type Subject = DbModel[User]
    def subject: DbModel[User] = user
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
  final class AuthRequest[A](val user: DbModel[User], val headerData: HeaderData, request: Request[A])
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

    def project: DbModel[Project] = data.project
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

    type Subject = DbModel[Project]
    override def user: DbModel[User]       = request.user
    override val subject: DbModel[Project] = this.data.project
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
    type Subject = DbModel[Organization]
    override def user: DbModel[User]            = request.user
    override val subject: DbModel[Organization] = this.data.orga
  }
  object AuthedOrganizationRequest {
    implicit def hasScope: HasScope[AuthedOrganizationRequest[_]] = HasScope.orgScope(_.subject.id.value)
  }
}
