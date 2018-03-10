package ore

import scala.concurrent.ExecutionContext

/**
  * Represents anything that can be visited.
  */
trait Visitable {

  /**
    * Returns the URL to this.
    *
    * @return URL
    */
  def url(implicit ec: ExecutionContext): String

  /**
    * Returns this instance's name.
    *
    * @return Instance's name
    */
  def name: String

}
