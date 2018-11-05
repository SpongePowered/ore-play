import play.api.Configuration

import db.impl.access.UserBase.UserOrdering
import db.query.UserQueries
import ore.OreConfig

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UserQueriesSpec extends DbSpec {

  implicit val config: OreConfig = new OreConfig(
    Configuration.load(getClass.getClassLoader, System.getProperties, Map.empty, allowMissingApplicationConf = false)
  )

  test("GetAuthors") {
    check(UserQueries.getAuthors(0, UserOrdering.Role))
  }

  test("GetStaff") {
    check(UserQueries.getStaff(0, UserOrdering.Role))
  }
}
