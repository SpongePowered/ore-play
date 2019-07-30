package db

import play.api.Configuration

import db.impl.access.UserBase.UserOrdering
import db.impl.query.UserPagesQueries
import ore.OreConfig
import ore.db.impl.query.UserQueries

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UserQueriesSpec extends DbSpec {

  implicit val config: OreConfig = new OreConfig(
    Configuration.load(getClass.getClassLoader, System.getProperties, Map.empty, allowMissingApplicationConf = false)
  )

  /* Relies on a view and as such can't test NULL stuff reliably
  test("GetProjects") {
    check(UserQueries.getProjects("Foo", Some(0L), ProjectSortingStrategy.Default, 50, 0))
  }
   */

  test("GetAuthors") {
    check(UserPagesQueries.getAuthors(0, UserOrdering.Role))
  }

  test("GetStaff") {
    check(UserPagesQueries.getStaff(0, UserOrdering.Role))
  }

  /* Relies on a view and as such can't test NULL stuff reliably
  test("GlobalTrust") {
    check(UserQueries.globalTrust(0L))
  }

  test("ProjectTrust") {
    check(UserQueries.projectTrust(0L, 0L))
  }

  test("OrganizationTrust") {
    check(UserQueries.organizationTrust(0L, 0L))
  }
   */

  test("AllPossibleProjectPermissions") {
    check(UserQueries.allPossibleProjectPermissions(0L))
  }

  test("AllPossibleOrgPermissions") {
    check(UserQueries.allPossibleOrgPermissions(0L))
  }
}
