package ore.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    require(secret.nonEmpty, "empty secret")
    require(data.nonEmpty, "nothing to hash!")
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
    bytesToHex(hmac(HmacSha256, secret.getBytes(CharEncoding), data))

  //https://stackoverflow.com/a/9855338
  private val hexArray = "0123456789abcdef".toCharArray
  private def bytesToHex(bytes: Array[Byte]): String = {
    val hexChars = new Array[Char](bytes.length * 2)
    var j        = 0
    while (j < bytes.length) {
      val v = bytes(j) & 0xFF
      hexChars(j * 2) = hexArray(v >>> 4)
      hexChars(j * 2 + 1) = hexArray(v & 0x0F)

      j += 1
    }
    new String(hexChars)
  }
}
