package security.pgp

import java.io.{ByteArrayInputStream, InputStream, OutputStream}
import java.nio.file.Files._
import java.nio.file.{Files, Path}

import com.google.common.base.Preconditions._
import com.google.common.io.ByteStreams
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.jcajce.{JcaPGPObjectFactory, JcaPGPPublicKeyRingCollection}
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider

import scala.util.Try

import play.api.Logger

/**
  * Verifies data within the PGP ecosystem.
  */
class PGPVerifier {

  val Logger: Logger = PGPPublicKeyInfo.Logger

  /**
    * Verifies the specified document [[InputStream]] against the specified
    * signature [[InputStream]] and public key [[InputStream]].
    *
    * @param doc Document bytes
    * @param sigIn Signature input stream
    * @param keyIn Public key input stream
    * @return True if verified, false otherwise
    */
  def verifyDetachedSignature(doc: Array[Byte], sigIn: InputStream, keyIn: InputStream): Boolean = {
    Logger.info("Processing signature...")
    var result = false
    try {
      val in = PGPUtil.getDecoderStream(sigIn)
      val factory = new JcaPGPObjectFactory(in)
      var sigList: PGPSignatureList = null

      def getNextObject = Try(factory.nextObject()).toOption.orNull

      var currentObject = getNextObject
      if (currentObject == null) {
        Logger.info("<VERIFICATION FAILED> No PGP data found.")
        return false
      }

      while (currentObject != null) {
        currentObject match {
          case signatureList: PGPSignatureList =>
            if (signatureList.isEmpty) {
              Logger.info("<VERIFICATION FAILED> Empty signature list.")
              return false
            }
            sigList = signatureList
          case e =>
            Logger.info("Unknown packet : " + e.getClass)
        }
        Logger.info("Processed packet : " + currentObject.toString)
        currentObject = getNextObject
      }

      if (sigList == null) {
        Logger.info("<VERIFICATION FAILED> No signature found.")
        return false
      }

      val sig = sigList.get(0)
      val keyRings = new JcaPGPPublicKeyRingCollection(keyIn)
      val pubKey = keyRings.getPublicKey(sig.getKeyID)
      if (pubKey == null) {
        Logger.info("<VERIFICATION FAILED> Invalid signature for public key.")
        return false
      }

      sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pubKey)
      sig.update(doc)
      result = sig.verify()
    } catch {
      case e: Exception =>
        Logger.error("<VERIFICATION FAILED> An error occurred while verifying a signature.", e)
        result = false
    } finally {
      sigIn.close()
      keyIn.close()
    }

    Logger.info(if (result) "<VERIFICATION COMPLETE>" else "<VERIFICATION FAILED>")

    result
  }

  /**
    * Verifies the specified document [[Path]] against the specified
    * signature [[Path]] and public key string.
    *
    * @param docPath  Document path
    * @param sigPath  Signature path
    * @param key      Public key content
    * @return         True if verified, false otherwise
    */
  def verifyDetachedSignature(docPath: Path, sigPath: Path, key: String): Boolean = {
    checkNotNull(docPath, "docPath is null", "")
    checkNotNull(key, "key is null", "")
    checkArgument(exists(docPath), "doc does not exist", "")
    checkArgument(exists(sigPath), "sig does not exist", "")
    val keyStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(key.getBytes))
    verifyDetachedSignature(Files.readAllBytes(docPath), newInputStream(sigPath), keyStream)
  }

  /**
    * Verifies the specified [[InputStream]] against the specified public key
    * [[InputStream]]. Only the first signature will be checked.
    *
    * When using GPG one should sign uploaded files with the following command:
    *
    * {{{
    *   gpg --output plugin-signed.jar --sign plugin.jar
    * }}}
    *
    * Where "plugin.jar" is your original plugin file and "plugin-signed.jar" is your output file.
    *
    * @see https://www.gnupg.org/gph/en/manual/x135.html
    * @param verifyIn InputStream to verify
    * @param keyIn    Public key InputStream
    * @return         True if verified
    */
  @deprecated("use verifyDetachedSignature() instead", "1.0.2")
  def verifyAndDecrypt(verifyIn: InputStream, out: OutputStream, keyIn: InputStream): Boolean = {
    checkNotNull(verifyIn, "input is null", "")
    checkNotNull(out, "output is null", "")
    checkNotNull(keyIn, "key input is null", "")

    // Retrieve the necessary data
    val in = PGPUtil.getDecoderStream(verifyIn)
    var factory = new JcaPGPObjectFactory(in)
    var sig: PGPOnePassSignature = null
    var data: Array[Byte] = null
    var sigList: PGPSignatureList = null

    def doNextObject() = Try(factory.nextObject()).getOrElse(null)

    var currentObject = doNextObject()
    while ((sig == null || data == null || sigList == null) && currentObject != null) {
      // Normally the packets will be read in this order
      currentObject match {
        case compressedData: PGPCompressedData =>
          // Decompress and continue
          factory = new JcaPGPObjectFactory(compressedData.getDataStream)
        case onePassSigList: PGPOnePassSignatureList =>
          if (onePassSigList.isEmpty) {
            Logger.info("Empty one pass signature list.")
            return false
          }
          sig = onePassSigList.get(0)
        case literalData: PGPLiteralData =>
          val dataIn = literalData.getInputStream
          data = ByteStreams.toByteArray(dataIn)
          dataIn.close()
        case signatureList: PGPSignatureList =>
          if (signatureList.isEmpty) {
            Logger.info("Empty signature list.")
            return false
          }
          sigList = signatureList
        case _ =>
      }
      Logger.info("Processed packet: " + currentObject.toString)
      currentObject = doNextObject()
    }

    in.close()

    if (sig == null || data == null || sigList == null) {
      Logger.info("Incomplete packet data.")
      return false
    }

    Logger.info("Signature Key ID: " + java.lang.Long.toHexString(sig.getKeyID))

    // Verify against public key
    val keyRings = new JcaPGPPublicKeyRingCollection(keyIn)
    val pubKey = keyRings.getPublicKey(sig.getKeyID)
    keyIn.close()
    if (pubKey == null)
      return false

    sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pubKey)
    sig.update(data)
    val result = sig.verify(sigList.get(0))
    Logger.info("Verified: " + result)
    if (result) {
      out.write(data)
      out.close()
    }
    result
  }

  @deprecated("use verifyDetachedSignature() instead", "1.0.2")
  def verifyAndDecrypt(in: Path, out: Path, key: String): Boolean = {
    checkNotNull(in, "input is null", "")
    checkNotNull(out, "output is null", "")
    checkNotNull(key, "key is null", "")
    checkArgument(exists(in), "input does not exist", "")
    if (notExists(out)) {
      createDirectories(out.getParent)
      createFile(out)
    }
    val inStream = newInputStream(in)
    val outStream = newOutputStream(out)
    val keyStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(key.getBytes))
    verifyAndDecrypt(inStream, outStream, keyStream)
  }

}
