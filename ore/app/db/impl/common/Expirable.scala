package db.impl.common
import java.sql.Timestamp
import java.util.Date

/**
  * Represents something that has an expiration date.
  */
trait Expirable {

  /**
    * Time of expiration.
    *
    * @return Instant of expiration
    */
  def expiration: Timestamp

  /**
    * True if has expired and should be treated as such.
    *
    * @return True if expired
    */
  def hasExpired: Boolean = expiration.before(new Date)

}
