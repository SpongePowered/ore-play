package util.uiowrappers

import javax.inject.Inject

import ore.auth.{AuthUser, ChangeAvatarToken, SpongeAuthApi}

import akka.http.scaladsl.model.Uri
import cats.data.EitherT
import scalaz.zio.{Task, UIO}

class UIOSpongeAuthApi @Inject()(underlying: SpongeAuthApi[Task]) extends SpongeAuthApi[UIO] {
  override def createDummyUser(username: String, email: String): EitherT[UIO, List[String], AuthUser] =
    EitherT(underlying.createDummyUser(username, email).value.orDie)

  override def getUser(username: String): EitherT[UIO, List[String], AuthUser] =
    EitherT(underlying.getUser(username).value.orDie)

  override def getChangeAvatarToken(
      requester: String,
      organization: String
  ): EitherT[UIO, List[String], ChangeAvatarToken] =
    EitherT(underlying.getChangeAvatarToken(requester, organization).value.orDie)

  override def changeAvatarUri(organization: String, token: ChangeAvatarToken): Uri =
    underlying.changeAvatarUri(organization, token)
}
