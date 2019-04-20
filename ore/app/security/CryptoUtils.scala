package security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import ore.util.StringUtils

import com.google.common.base.Preconditions._

/**
  * Handles common cryptography functions within the application.
  */
object CryptoUtils {

  // Hashing
  final val HmacSha256   = "HmacSHA256"
  final val CharEncoding = "UTF-8"

  /**
    * Performs an HMAC hash with the specified algorithm.
    *
    * @param algo   HMAC algorithm
    * @param secret Secret key
    * @param data   Data to encrypt
    * @return
    */
  def hmac(algo: String, secret: Array[Byte], data: Array[Byte]): Array[Byte] = {
    checkNotNull(algo, "null algo", "")
    checkNotNull(secret, "null secret", "")
    checkArgument(secret.nonEmpty, "empty secret", "")
    checkNotNull(data, "null data", "")
    checkArgument(data.nonEmpty, "nothing to hash!", "")
    val hmac    = Mac.getInstance(algo)
    val keySpec = new SecretKeySpec(secret, algo)
    hmac.init(keySpec)
    hmac.doFinal(data)
  }

  /**
    * Performs an HMAC-SHA256 hash on the specified data.
    *
    * @param secret Secret key
    * @param data   Data to encrypt
    * @return
    */
  def hmac_sha256(secret: String, data: Array[Byte]): String =
    StringUtils.bytesToHex(hmac(HmacSha256, secret.getBytes(CharEncoding), data))
}
