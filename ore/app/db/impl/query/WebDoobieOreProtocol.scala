package db.impl.query

import models.querymodels.ViewTag
import ore.db.impl.query.DoobieOreProtocol
import ore.models.project.TagColor

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

trait WebDoobieOreProtocol extends DoobieOreProtocol {

  implicit val viewTagListRead: Read[List[ViewTag]] = Read[(List[String], List[String], List[TagColor])].map {
    case (name, data, color) => name.zip(data).zip(color).map(t => ViewTag(t._1._1, t._1._2, t._2))
  }

  implicit val viewTagListWrite: Write[List[ViewTag]] =
    Write[(List[String], List[String], List[TagColor])].contramap(_.flatMap(ViewTag.unapply).unzip3)
}
