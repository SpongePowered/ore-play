package ore.db.impl.common

import java.time.OffsetDateTime

/**
  * Represents something that has an expiration date.
  */
trait Expirable {

  /**
    * Time of expiration.
    *
    * @return Instant of expiration
    */
  def expiration: OffsetDateTime

  /**
    * True if has expired and should be treated as such.
    *
    * @return True if expired
    */
  def hasExpired: Boolean = expiration.isBefore(OffsetDateTime.now())

}
