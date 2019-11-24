package ore

import ore.db.Model
import ore.db.impl.query.DoobieOreProtocol._
import ore.models.Job

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object JobsQueries {

  val tryGetjob: Query0[Model[Job]] =
    sql"""|UPDATE jobs
          |SET state = 'started', last_updated = now()
          |    WHERE id = (
          |        SELECT id
          |            FROM jobs
          |            WHERE state = 'not_started' AND (retry_at IS NULL OR retry_at < now()) 
          |            ORDER BY id 
          |            FOR UPDATE SKIP LOCKED
          |            LIMIT 1)
          |    RETURNING *;""".stripMargin.query[Model[Job]]

}
