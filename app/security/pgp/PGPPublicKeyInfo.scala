package security.pgp

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.util.Date

import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection
import org.bouncycastle.openpgp.{PGPException, PGPSignature, PGPUtil}

/**
  * Represents data that is decoded from a submitted PGP Public Key and is to
  * be displayed to the end user.
  *
  * @param raw        Raw key
  * @param userName   Name of individual associated with the key
  * @param email      Email associated with key
  * @param id         Key ID
  * @param createdAt  Date of creation
  */
case class PGPPublicKeyInfo(
    raw: String,
    userName: String,
    email: String,
    id: String,
    createdAt: Date,
    expirationDate: Option[Date]
)

object PGPPublicKeyInfo {

  val Logger = play.api.Logger("PGP")

  /**
    * Decodes a raw string into a [[PGPPublicKeyInfo]]. This method looks for
    * a "master" key within the block and returns [[PGPPublicKeyInfo]] regarding
    * that key. The first user in the key will be used.
    *
    * @param raw Raw key string
    * @return [[PGPPublicKeyInfo]] instance
    */
  def decode(raw: String): Option[PGPPublicKeyInfo] = {
    Logger.debug(s"Decoding public key:\n$raw\n")
    var in: InputStream = null
    try {
      in = PGPUtil.getDecoderStream(new ByteArrayInputStream(raw.getBytes))
      val keyRings                    = new JcaPGPPublicKeyRingCollection(in)
      val keyRingIter                 = keyRings.iterator()
      var keyRingNum                  = 0
      var masterKey: PGPPublicKeyInfo = null
      while (keyRingIter.hasNext) {
        keyRingNum += 1
        Logger.debug("Key ring: " + keyRingNum)
        val keyRing = keyRingIter.next()
        val keyIter = keyRing.iterator()
        var keyNum  = 0
        while (keyIter.hasNext) {
          keyNum += 1
          val key          = keyIter.next()
          val hexId        = java.lang.Long.toHexString(key.getKeyID)
          val createdAt    = key.getCreationTime
          val isRevoked    = key.hasRevocation
          val isEncryption = key.isEncryptionKey
          val isMaster     = key.isMasterKey
          val validSeconds = key.getValidSeconds
          val expirationDate =
            if (validSeconds != 0)
              Some(new Date(createdAt.getTime + validSeconds * 1000))
            else
              None

          Logger.debug("Key: " + keyNum)
          Logger.debug("ID: " + hexId)
          Logger.debug("Created at: " + createdAt)
          Logger.debug("Revoked: " + isRevoked)
          Logger.debug("Encryption: " + isEncryption)
          Logger.debug("Master: " + isMaster)
          Logger.debug("Expiration: " + expirationDate.getOrElse("None"))

          Logger.debug("Users:")
          val userIter          = key.getUserIDs
          var firstUser: String = null
          var userNum           = 0
          while (userIter.hasNext) {
            val user = userIter.next()
            Logger.debug("\t" + user)
            if (userNum == 0)
              firstUser = user.toString
            userNum += 1
          }

          Logger.debug("Signatures:")
          val sigIter = key.getSignatures
          while (sigIter.hasNext) {
            val sig: PGPSignature = sigIter.next().asInstanceOf[PGPSignature]
            Logger.debug("\tCreated at: " + sig.getCreationTime)
            Logger.debug("\tCertification: " + sig.isCertification)
          }

          if (isMaster) {
            val emailIndexStart = firstUser.indexOf('<')
            val emailIndexEnd   = firstUser.indexOf('>')
            if (emailIndexStart == -1 || emailIndexEnd == -1)
              throw new IllegalStateException("invalid user format?")
            val userName = firstUser.substring(0, emailIndexStart).trim()
            val email    = firstUser.substring(emailIndexStart + 1, emailIndexEnd)

            Logger.debug("User name: " + userName)
            Logger.debug("Email: " + email)

            if (isRevoked)
              throw new IllegalStateException("Key is revoked?")

            masterKey = PGPPublicKeyInfo(raw, userName, email, hexId, createdAt, expirationDate)
          }
        }
      }

      if (masterKey == null)
        throw new IllegalStateException("No master key found")
      Some(masterKey)
    } catch {
      case _: IOException | _: PGPException =>
        None
    } finally {
      if (in != null)
        in.close()
    }
  }

}
