package db.impl.schema

import db._
import db.impl.OrePostgresDriver.api._
import db.impl._
import db.impl.access.UserBase
import models.project._
import models.user.User
import ore.Platforms.Platform
import ore.project.Categories.Category
import ore.project.ProjectSortingStrategies.ProjectSortingStrategy

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Project related queries
  */
class ProjectSchema(override val service: ModelService, implicit val users: UserBase)
  extends ModelSchema[Project](service, classOf[Project], TableQuery[ProjectTableMain]) {

  /**
    * Returns all [[User]]s with at least one [[Project]].
    *
    * @return Project authors
    */
  def distinctAuthors: Future[Seq[User]] = {
    service.DB.db.run {
      (for (project <- this.baseQuery) yield project.userId).distinct.result
    } map { userIds =>
      this.users.in(userIds.toSet).toSeq
    }
  }

  /**
    * Returns a ModelFilter to match a string query
    *
    * @param query String query
    * @return Project's matching query
    */
  def searchFilter(query: String): ModelFilter[Project] = {
    val q = '%' + query.toLowerCase + '%'
    ModelFilter[Project] { p =>
      (p.name.toLowerCase like q) ||
        (p.description.toLowerCase like q) ||
        (p.ownerName.toLowerCase like q) ||
        (p.pluginId.toLowerCase like q)
    }
  }

  /**
    * Filters projects by platform.
    *
    * @param platform Platform to filter
    * @return Model filter
    */
  def platformFilter(platform: Platform): ModelFilter[Project] = ModelFilter[Project] { project =>
    // TODO Filtering based on Tags
    true
  }

  /**
    * Filters projects by category.
    *
    * @param categories Category array
    * @return Filter for projects with at least one of the categories
    */
  def categoryFilter(categories: Array[Category]): ModelFilter[Project] = ModelFilter[Project] { project =>
    project.category inSetBind categories
  }

  /**
    * Filters projects based on the given criteria.
    *
    * @param filter Filter to match Projects on
    * @param limit  Amount of Projects to get
    * @param offset Result set offset
    * @return Projects matching criteria
    */
  def collect(filter: Project#T => Rep[Boolean], sort: ProjectSortingStrategy,
              limit: Int, offset: Int): Future[Seq[Project]]
  = this.service.collect[Project](this.modelClass, filter, Option(sort).map(_.fn).orNull, limit, offset)

  override def like(model: Project): Future[Option[Project]] = {
    this.service.find[Project](this.modelClass, p => p.ownerName.toLowerCase === model.ownerName.toLowerCase
      && p.name.toLowerCase === model.name.toLowerCase)
  }

}
