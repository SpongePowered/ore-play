package db

import db.impl.query.StatTrackerQueries

import com.github.tminglei.slickpg.InetString
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class StatTrackerQueriesSpec extends DbSpec {

  test("AddVersionDownload") {
    check(StatTrackerQueries.addVersionDownload(0L, 0L, InetString("::1"), "foobar", None))
  }

  test("AddProjectView") {
    check(StatTrackerQueries.addProjectView(0L, InetString("::1"), "foobar", None))
  }

  /* Inspection doesn't work well with VOID type
  test("ProcessVersionDownloads") {
    check(StatTrackerQueries.processVersionDownloads)
  }

  test("ProcessProjectViews") {
    check(StatTrackerQueries.processProjectViews)
  }
 */
}
