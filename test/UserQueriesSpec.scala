import play.api.Configuration

import db.impl.access.UserBase.UserOrdering
import db.query.UserQueries
import ore.OreConfig
import ore.project.ProjectSortingStrategy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UserQueriesSpec extends DbSpec {

  implicit val config: OreConfig = new OreConfig(
    Configuration.load(getClass.getClassLoader, System.getProperties, Map.empty, allowMissingApplicationConf = false)
  )

  test("GetProjects") {
    check(UserQueries.getProjects("Foo", ProjectSortingStrategy.Default, 50, 0))
  }

  test("GetAuthors") {
    check(UserQueries.getAuthors(0, UserOrdering.Role))
  }

  test("GetStaff") {
    check(UserQueries.getStaff(0, UserOrdering.Role))
  }

  test("GlobalTrust") {
    check(UserQueries.globalTrust(0))
  }

  test("ProjectTrust") {
    check(UserQueries.projectTrust(0, 0))
  }

  test("OrganizationTrust") {
    check(UserQueries.organizationTrust(0, 0))
  }
}
