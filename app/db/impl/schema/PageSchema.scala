package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.PageTable
import db.{ModelSchema, ModelService}
import models.project.Page

import scala.concurrent.Future

/**
  * Page related queries.
  */
class PageSchema(override val service: ModelService)
  extends ModelSchema[Page](service, classOf[Page], TableQuery[PageTable]) {

  override def like(page: Page): Future[Option[Page]] = {
    this.service.find[Page](this.modelClass, p =>
      p.projectId === page.projectId && p.name.toLowerCase === page.name.toLowerCase && p.parentId === page.parentId
    )
  }

}
