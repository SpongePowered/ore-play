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
  def validateKey(): Either[String, PGPPublicKeyInfo] =
    PGPPublicKeyInfo.decode(raw).map { info =>
      this.info = info
      info
    }

}
