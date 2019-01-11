package security.pgp

import java.io.ByteArrayInputStream
import java.util.Date

import scala.collection.JavaConverters._

import util.OreMDCCtx

import cats.effect.{Resource, SyncIO}
import com.typesafe.scalalogging
import com.typesafe.scalalogging.LoggerTakingImplicit
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection
import org.bouncycastle.openpgp.{PGPException, PGPUtil}

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

  val Logger = scalalogging.Logger("PGP")
  val MDCLogger: LoggerTakingImplicit[OreMDCCtx] =
    scalalogging.Logger.takingImplicit[OreMDCCtx](Logger.underlying)

  /**
    * Decodes a raw string into a [[PGPPublicKeyInfo]]. This method looks for
    * a "master" key within the block and returns [[PGPPublicKeyInfo]] regarding
    * that key. The first user in the key will be used.
    *
    * @param raw Raw key string
    * @return [[PGPPublicKeyInfo]] instance
    */
  def decode(raw: String): Either[String, PGPPublicKeyInfo] = {
    import cats.instances.vector._
    import cats.syntax.all._
    Logger.debug(s"Decoding public key:\n$raw\n")

    Resource
      .fromAutoCloseable(SyncIO(PGPUtil.getDecoderStream(new ByteArrayInputStream(raw.getBytes))))
      .use { in =>
        val keyRings    = new JcaPGPPublicKeyRingCollection(in)
        val keyRingIter = keyRings.iterator().asScala.zipWithIndex
        val res = keyRingIter.toVector.flatMap {
          case (keyRing, keyRingNum) =>
            Logger.debug(s"Key ring: $keyRingNum")
            val keyIter = keyRing.iterator().asScala.zipWithIndex
            keyIter.toVector.mapFilter {
              case (key, keyNum) =>
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

                val firstUser = key.getUserIDs.asScala.toStream.headOption
                firstUser.filter(_ => isMaster).map { firstUser =>
                  val emailIndexStart = firstUser.indexOf('<')
                  val emailIndexEnd   = firstUser.indexOf('>')
                  if (emailIndexStart == -1 || emailIndexEnd == -1)
                    Left("invalid user format?")
                  else {
                    val userName = firstUser.substring(0, emailIndexStart).trim()
                    val email    = firstUser.substring(emailIndexStart + 1, emailIndexEnd)

                    Logger.debug("User name: " + userName)
                    Logger.debug("Email: " + email)

                    if (isRevoked)
                      Left("Key is revoked?")
                    else
                      Right(PGPPublicKeyInfo(raw, userName, email, hexId, createdAt, expirationDate))
                  }
                }
            }
        }

        res match {
          case Vector()      => SyncIO.pure(Left("No master key found"))
          case Vector(ret)   => SyncIO.pure(ret)
          case Vector(_ @_*) => SyncIO.pure(Left("Multiple master keys found"))
        }
      }
      .recover {
        case e: PGPException => Left(e.getMessage)
      }
      .unsafeRunSync()
  }

}
