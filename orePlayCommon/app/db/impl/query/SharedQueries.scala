package db.impl.query

import doobie.implicits._

object SharedQueries {

  val refreshHomeView: doobie.Update0 = sql"REFRESH MATERIALIZED VIEW home_projects".update
}
