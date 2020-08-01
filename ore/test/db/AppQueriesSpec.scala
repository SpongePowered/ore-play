package db

import java.time.LocalDate

import scala.concurrent.duration._

import db.impl.query.{AppQueries, SharedQueries}
import ore.OreConfig

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import pureconfig.ConfigSource
import pureconfig.generic.auto._

@RunWith(classOf[JUnitRunner])
class AppQueriesSpec extends DbSpec {

  implicit val config: OreConfig = ConfigSource.default.loadOrThrow[OreConfig]

  /* Relies on a view and as such can't test NULL stuff reliably
  test("GetHomeProjects") {
    check(
      AppQueries
        .getHomeProjects(
          Some(5),
          canSeeHidden = false,
          List("Sponge"),
          List(Category.Chat),
          Some("foo"),
          ProjectSortingStrategy.Default,
          0,
          50,
          orderWithRelevance = true
        )
    )
  }
   */

  /* Wrong nullness reported
  test("WatcherStartProject") {
    check(SharedQueries.watcherStartProject(0L))
  }
   */

  test("GetQueue") {
    check(AppQueries.getQueue)
  }

  test("Flags") {
    check(AppQueries.flags)
  }

  test("GetUnhealtyProjects") {
    check(AppQueries.getUnhealtyProjects(30.days))
  }

  test("GetErroredJobs") {
    check(AppQueries.erroredJobs)
  }

  test("GetReviewActivity") {
    check(AppQueries.getReviewActivity("Foo"))
  }

  test("GetFlagActivity") {
    check(AppQueries.getFlagActivity("Foo"))
  }

  test("GetStats") {
    check(AppQueries.getStats(LocalDate.now(), LocalDate.now()))
  }

  /* Relies on a view and as such can't test NULL stuff reliably
  test("GetLog") {
    check(AppQueries.getLog(Some(1), Some(0), Some(0), Some(0), Some(0), Some(0), Some(0)))
  }
   */

  test("GetVisibilityNeedsApproval") {
    check(AppQueries.getVisibilityNeedsApproval)
  }

  test("GetVisibilityWaitingProject") {
    check(AppQueries.getVisibilityWaitingProject)
  }

  test("SitemapIndexUsers") {
    check(AppQueries.sitemapIndexUsers)
  }
}
