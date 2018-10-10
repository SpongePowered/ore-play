package form

import security.pgp.PGPPublicKeyInfo

/**
  * Represents a submission of a PGP Public Key to a [[models.user.User]]
  * profile.
  *
  * @param raw Raw key string
  */
case class PGPPublicKeySubmission(raw: String) {

  /** The decoded information */
  var info: PGPPublicKeyInfo = _

  /**
    * Validates this submission.
    *
    * @return True if validated
    */
  def validateKey(): Boolean =
    try {
      val key = PGPPublicKeyInfo.decode(raw)
      key.foreach(this.info = _)
      key.isDefined
    } catch {
      case _: IllegalStateException =>
        false
    }

}
