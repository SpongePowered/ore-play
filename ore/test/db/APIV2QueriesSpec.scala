package db

import java.time.LocalDate

import db.impl.query.APIV2Queries
import ore.OreConfig
import ore.data.project.Category
import ore.permission.Permission

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import pureconfig.ConfigSource
import pureconfig.generic.auto._

@RunWith(classOf[JUnitRunner])
class APIV2QueriesSpec extends DbSpec {

  implicit val config: OreConfig = ConfigSource.default.loadOrThrow[OreConfig]

  test("GetApiAuthInfo") {
    check(APIV2Queries.getApiAuthInfo(""))
  }

  test("FindApiKey") {
    check(APIV2Queries.findApiKey("Foo", "Bar"))
  }

  test("CreateApiKey") {
    check(APIV2Queries.createApiKey("Foo", 0L, "Bar", "Baz", Permission.None))
  }

  test("DeleteApiKey") {
    check(APIV2Queries.deleteApiKey("Foo", 0L))
  }

  /* Uses views
  test("ProjectQuery") {
    check(
      APIV2Queries.projectQuery(
        Some("foo"),
        List(Category.AdminTools),
        List("Foo:Bar"),
        Some("Foo"),
        Some("Foo"),
        canSeeHidden = false,
        Some(0L),
        ProjectSortingStrategy.MostDownloads,
        orderWithRelevance = true,
        20L,
        0L
      )
    )
  }
   */

  test("ProjectCountQuery") {
    check(
      APIV2Queries.projectCountQuery(
        Some("foo"),
        List(Category.AdminTools),
        List("Foo" -> Some("Bar")),
        Some("Foo"),
        Some("Foo"),
        canSeeHidden = false,
        Some(0L)
      )
    )
  }

  test("ProjectMembers") {
    check(APIV2Queries.projectMembers("Foo", 20L, 0L))
  }

  test("VersionCountQuery") {
    check(APIV2Queries.versionCountQuery("Foo", List("Foo:Bar"), canSeeHidden = false, Some(0L)))
  }

  test("UserQuery") {
    check(APIV2Queries.userQuery("Foo"))
  }

  test("ProjectStats") {
    check(APIV2Queries.projectStats("foo", LocalDate.now().minusDays(30), LocalDate.now()))
  }

  test("VersionStats") {
    check(APIV2Queries.versionStats("foo", "bar", LocalDate.now().minusDays(30), LocalDate.now()))
  }
}
