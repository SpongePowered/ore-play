import java.time.LocalDate

import scala.concurrent.duration._

import play.api.Configuration

import db.impl.query.AppQueries
import ore.OreConfig

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AppQueriesSpec extends DbSpec {

  implicit val config: OreConfig = new OreConfig(
    Configuration.load(getClass.getClassLoader, System.getProperties, Map.empty, allowMissingApplicationConf = false)
  )

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

  test("GetQueue") {
    check(AppQueries.getQueue)
  }

  test("Flags") {
    check(AppQueries.flags(0))
  }

  test("GetUnhealtyProjects") {
    check(AppQueries.getUnhealtyProjects(30.days))
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
}
