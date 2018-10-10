package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.{ModelSchema, ModelService}
import models.admin.Review

/**
  * Version related queries.
  */
class ReviewSchema(override val service: ModelService)
    extends ModelSchema[Review](service, classOf[Review], TableQuery[ReviewTable]) {}
