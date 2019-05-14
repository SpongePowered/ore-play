package ore.db.impl.common

import java.time.Instant

/**
  * Represents something that has an expiration date.
  */
trait Expirable {

  /**
    * Time of expiration.
    *
    * @return Instant of expiration
    */
  def expiration: Instant

  /**
    * True if has expired and should be treated as such.
    *
    * @return True if expired
    */
  def hasExpired: Boolean = expiration.isBefore(Instant.now())

}
