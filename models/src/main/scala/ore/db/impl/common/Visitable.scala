package ore.db.impl.common

/**
  * Represents anything that can be visited.
  */
trait Visitable {

  /**
    * Returns the URL to this.
    *
    * @return URL
    */
  def url: String

  /**
    * Returns this instance's name.
    *
    * @return Instance's name
    */
  def name: String

}
