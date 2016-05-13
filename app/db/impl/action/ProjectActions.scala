package db.impl.action

import db.ModelService
import db.impl.{ProjectTable => _, _}
import db.action.ModelAction.wrapSeq
import db.action.{ModelActions, ModelFilter, StatActions}
import db.impl.OrePostgresDriver.api._
import db.impl._
import forums.DiscourseApi
import models.project._
import models.statistic.ProjectView
import ore.project.Categories.Category
import ore.project.ProjectMember
import ore.project.ProjectSortingStrategies.ProjectSortingStrategy

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * Project related queries
  */
class ProjectActions(override val service: ModelService)
  extends ModelActions[ProjectTable, Project](service, classOf[Project], TableQuery[ProjectTable]) {

  val Flags = service.registrar.register(
    new ModelActions[FlagTable, Flag](this.service, classOf[Flag], TableQuery[FlagTable])
  )

  val Views = service.registrar.register(
    new StatActions[ProjectViewsTable, ProjectView](this.service, TableQuery[ProjectViewsTable], classOf[ProjectView])
  )

  private val stars = TableQuery[ProjectStarsTable]
  implicit private val users: UserBase = this.service.access(classOf[UserBase])

  /**
    * Returns the [[ProjectMember]]s in the specified Project, sorted by role.
    *
    * @param project Project to get Members for
    * @return List of Members
    */
  def getMembers(project: Project)(implicit forums: DiscourseApi): Future[Seq[ProjectMember]] = {
    val distinctUserIds = (for (role <- service.getActions(classOf[UserActions]).ProjectRoles.baseQuery.filter {
      _.projectId === project.id.get
    }) yield role.userId).distinct.result

    service.DB.db.run(distinctUserIds)
      .map(_.map(this.users.get(_).get))
      .map(for (user <- _) yield new ProjectMember(project, user.username))
  }

  /**
    * Returns a ModelFilter to match a string query
    *
    * @param query String query
    * @return Project's matching query
    */
  def searchFilter(query: String): ModelFilter[ProjectTable, Project] = {
    val q = '%' + query.toLowerCase + '%'
    ModelFilter { p =>
      (p.name.toLowerCase like q) || (p.description.toLowerCase like q) || (p.ownerName.toLowerCase like q)
    }
  }

  /**
    * Filters projects based on the given criteria.
    *
    * @param filter Filter to match Projects on
    * @param limit  Amount of Projects to get
    * @param offset Result set offset
    * @return Projects matching criteria
    */
  def collect(filter: Filter, sort: ProjectSortingStrategy,
              limit: Int, offset: Int): Future[Seq[Project]]
  = collect(limit, offset, filter, Option(sort).map(_.fn).orNull)

  /**
    * Filters projects based on the given criteria.
    *
    * @param filter     Model filter
    * @param categories Project categories
    * @param limit      Amount to take
    * @param offset     Amount to drop
    * @param sort       Ordering
    * @return Filtered projects
    */
  def collect(filter: Filter, categories: Array[Category],
              limit: Int, offset: Int, sort: ProjectSortingStrategy): Future[Seq[Project]] = {
    val f: ProjectTable => Rep[Boolean] = if (categories != null) {
      val cf: ProjectTable => Rep[Boolean] = p => p.category inSetBind categories
      if (filter != null) p => cf(p) && filter(p) else cf
    } else filter
    collect(f, sort, limit, offset)
  }

  /**
    * Returns true if the specified Project is starred by the specified User.
    *
    * @param projectId Project to check
    * @param userId    User to look for
    * @return True if Project is starred by user
    */
  def isStarredBy(projectId: Int, userId: Int): Future[Boolean] = service.DB.db.run((this.stars.filter { sp =>
    sp.userId === userId && sp.projectId === projectId
  }.length > 0).result)

  /**
    * Sets the specified Project as starred for the specified user.
    *
    * @param projectId Project to star
    * @param userId    User to star for
    */
  def starFor(projectId: Int, userId: Int): Future[Any] = service.DB.db.run(this.stars +=(userId, projectId))

  /**
    * Sets the specified Project as unstarred for the specified user.
    *
    * @param projectId Project to unstar
    * @param userId    User to unstar for
    */
  def unstarFor(projectId: Int, userId: Int) = service.DB.db.run(this.stars.filter { sp =>
    sp.userId === userId && sp.projectId === projectId
  }.delete)

  /**
    * Returns all the Projects that the specified User has starred.
    *
    * @param userId   User ID to get stars for
    * @param limit    Max amount of stars to retrieve
    * @param offset   Amount of stars to skip
    * @return         Projects starred by user
    */
  def starredBy(userId: Int, limit: Int = -1, offset: Int = -1): Future[Seq[Project]] = {
    val promise = Promise[Seq[Project]]
    val starQuery = this.stars.filter(_.userId === userId)
    service.DB.db.run(starQuery.result).andThen {
      case Failure(thrown) => promise.failure(thrown)
      case Success(userStars) =>
        val projectIds = userStars.map(_._2)
        var projectsQuery = this.baseQuery.filter(_.id inSetBind projectIds)
        if (offset > -1) projectsQuery = projectsQuery.drop(offset)
        if (limit > -1) projectsQuery = projectsQuery.take(limit)
        promise.completeWith(service.doAction(projectsQuery.result))
    }
    promise.future
  }

  override def like(model: Project) = {
    find(p => p.ownerName.toLowerCase === model.ownerName.toLowerCase
      && p.name.toLowerCase === model.name.toLowerCase)
  }

}
