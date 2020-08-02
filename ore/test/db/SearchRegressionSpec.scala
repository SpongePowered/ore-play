package db

import scala.concurrent.Future

import ore.OreConfig

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import doobie._
import doobie.implicits._
import org.scalatest.Assertion
import pureconfig.ConfigSource
import pureconfig.generic.auto._

@RunWith(classOf[JUnitRunner])
class SearchRegressionSpec extends AsyncDbSpec {

  implicit val config: OreConfig = ConfigSource.default.loadOrThrow[OreConfig]

  def sqlTest(frag: Fragment): Future[Assertion] =
    runtime.unsafeRunToFuture(frag.query[Boolean].unique.transact(transactor)).map(assert(_))

  test("Basic singleword") {
    sqlTest(sql"""SELECT websearch_to_tsquery_postfix('english', 'foobar') @@ to_tsvector('english', 'foobar')""")
  }

  test("Basic multiword") {
    sqlTest(sql"""SELECT websearch_to_tsquery_postfix('english', 'foo bar') @@ to_tsvector('english', 'foo bar')""")
  }

  test("Short") {
    sqlTest(sql"""SELECT websearch_to_tsquery_postfix('english', 's') @@ to_tsvector('english', 'soon')""")
  }

  test("LuckPerms") {
    sqlTest(sql"""SELECT websearch_to_tsquery_postfix('english', 'LuckPerms') @@ to_tsvector('english', 'LuckPerms')""")
  }

}
