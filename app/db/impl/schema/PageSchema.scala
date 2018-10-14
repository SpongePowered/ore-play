package db.impl.schema

import scala.concurrent.{ExecutionContext, Future}

import db.impl.OrePostgresDriver.api._
import db.{ModelSchema, ModelService}
import models.project.Page

import cats.data.OptionT

/**
  * Page related queries.
  */
class PageSchema(override val service: ModelService)
    extends ModelSchema[Page](service, classOf[Page], TableQuery[PageTable]) {

  override def like(page: Page)(implicit ec: ExecutionContext): OptionT[Future, Page] =
    this.service.find[Page](
      this.modelClass,
      p =>
        p.projectId === page.projectId && p.name.toLowerCase === page.name.toLowerCase && page.parentId.fold(
          true: Rep[Boolean]
        )(p.parentId.get === _)
    )

}
