package security.pgp

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.Files._
import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._

import play.api.Logger

import cats.effect.{Resource, SyncIO}
import com.google.common.base.Preconditions._
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
    * @param sigInF Signature input stream
    * @param keyInF Public key input stream
    * @return True if verified, false otherwise
    */
  def verifyDetachedSignature(doc: Array[Byte], sigInF: SyncIO[InputStream], keyInF: SyncIO[InputStream]): Boolean = {
    Logger.debug("Processing signature...")
    import cats.syntax.all._

    val r: Resource[SyncIO, (InputStream, InputStream)] = for {
      sigIn <- Resource.fromAutoCloseable(sigInF)
      keyIn <- Resource.fromAutoCloseable(keyInF)
    } yield (sigIn, keyIn)

    val run = r
      .use {
        case (sigIn, keyIn) =>
          val optSigList = new JcaPGPObjectFactory(PGPUtil.getDecoderStream(sigIn))
            .iterator()
            .asScala
            .collectFirst {
              case signatureList: PGPSignatureList =>
                val empty = signatureList.isEmpty

                if (empty) {
                  Logger.debug("<VERIFICATION FAILED> Empty signature list.")
                  None
                } else {
                  Some(signatureList)
                }
            }
            .flatten

          val ret = optSigList.fold {
            Logger.debug("<VERIFICATION FAILED> No signature found.")
            false
          } { sigList =>
            val sig    = sigList.get(0)
            val pubKey = new JcaPGPPublicKeyRingCollection(keyIn).getPublicKey(sig.getKeyID)
            if (pubKey == null) { // scalafix:ok
              Logger.debug("<VERIFICATION FAILED> Invalid signature for public key.")
              false
            } else {
              sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pubKey)
              sig.update(doc)
              val result = sig.verify()
              Logger.debug(if (result) "<VERIFICATION COMPLETE>" else "<VERIFICATION FAILED>")
              result
            }
          }

          SyncIO.pure(ret)
      }
      .recover {
        case e: Exception =>
          Logger.error("<VERIFICATION FAILED> An error occurred while verifying a signature.", e)
          false
      }

    run.unsafeRunSync()
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
    val keyStream = SyncIO(PGPUtil.getDecoderStream(new ByteArrayInputStream(key.getBytes)))
    verifyDetachedSignature(Files.readAllBytes(docPath), SyncIO(newInputStream(sigPath)), keyStream)
  }
}
