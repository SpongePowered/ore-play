package security

import java.security.SecureRandom
import java.util.Base64

import com.google.common.base.Preconditions._
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, Mac}
import org.apache.commons.codec.binary.Hex

/**
  * Handles common cryptography functions within the application.
  */
object CryptoUtils {

  // Hashing
  final val HmacSha256 = "HmacSHA256"
  final val HmacSha1 = "HmacSHA1"
  final val CharEncoding = "UTF-8"

  // Two way encryption
  final val Random = new SecureRandom()
  final val Algo = "DESede"
  final val KeyLength = 24

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
    val hmac = Mac.getInstance(algo)
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
  def hmac_sha256(secret: String, data: Array[Byte]): String
  = Hex.encodeHexString(hmac(HmacSha256, secret.getBytes(CharEncoding), data))

  /**
    * Performs a two-way encryption of the specified string using the DESede
    * algorithm.
    *
    * @param str    String to encrypt
    * @param secret Secret key
    * @return       Encrypted string
    */
  def encrypt(str: String, secret: String): String = {
    checkNotNull(str, "null string", "")
    checkArgument(str.nonEmpty, "nothing to encrypt!", "")
    checkNotNull(secret, "null secret", "")
    checkArgument(secret.nonEmpty, "empty secret", "")
    val cipher = Cipher.getInstance(Algo)
    cipher.init(Cipher.ENCRYPT_MODE, generateKey(secret))
    Base64.getEncoder.encodeToString(cipher.doFinal(str.getBytes))
  }

  /**
    * Performs a decryption of the specified string using the DESede
    * algorithm.
    *
    * @param str    String to decrypt
    * @param secret Secret key
    * @return       Decrypted string
    */
  def decrypt(str: String, secret: String): String = {
    checkNotNull(str, "null string", "")
    checkArgument(str.nonEmpty, "nothing to decrypt!", "")
    checkNotNull(secret, "null secret", "")
    checkArgument(secret.nonEmpty, "empty secret", "")
    val cipher = Cipher.getInstance(Algo)
    cipher.init(Cipher.DECRYPT_MODE, generateKey(secret))
    new String(cipher.doFinal(Base64.getDecoder.decode(str.getBytes)))
  }

  private def generateKey(secret: String) = new SecretKeySpec(secret.getBytes.slice(0, KeyLength), Algo)

}
