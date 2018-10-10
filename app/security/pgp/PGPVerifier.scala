package security.pgp

import java.io.{ByteArrayInputStream, InputStream, OutputStream}
import java.nio.file.Files._
import java.nio.file.{Files, Path}

import scala.util.Try

import play.api.Logger

import com.google.common.base.Preconditions._
import com.google.common.io.ByteStreams
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.jcajce.{JcaPGPObjectFactory, JcaPGPPublicKeyRingCollection}
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider

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
    Logger.debug("Processing signature...")
    var result = false
    try {
      val in                        = PGPUtil.getDecoderStream(sigIn)
      val factory                   = new JcaPGPObjectFactory(in)
      var sigList: PGPSignatureList = null

      def getNextObject = Try(factory.nextObject()).toOption.orNull

      var currentObject = getNextObject
      if (currentObject == null) {
        Logger.debug("<VERIFICATION FAILED> No PGP data found.")
        return false
      }

      while (currentObject != null) {
        currentObject match {
          case signatureList: PGPSignatureList =>
            if (signatureList.isEmpty) {
              Logger.debug("<VERIFICATION FAILED> Empty signature list.")
              return false
            }
            sigList = signatureList
          case e =>
            Logger.debug("Unknown packet : " + e.getClass)
        }
        Logger.debug("Processed packet : " + currentObject.toString)
        currentObject = getNextObject
      }

      if (sigList == null) {
        Logger.debug("<VERIFICATION FAILED> No signature found.")
        return false
      }

      val sig      = sigList.get(0)
      val keyRings = new JcaPGPPublicKeyRingCollection(keyIn)
      val pubKey   = keyRings.getPublicKey(sig.getKeyID)
      if (pubKey == null) {
        Logger.debug("<VERIFICATION FAILED> Invalid signature for public key.")
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

    Logger.debug(if (result) "<VERIFICATION COMPLETE>" else "<VERIFICATION FAILED>")

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
}
