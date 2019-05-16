package util

import scala.language.higherKinds

import controllers.sugar.Requests.AuthRequest
import ore.StatTracker
import ore.db.{DbRef, Model, ModelService}
import ore.models.user.{LoggedAction, LoggedActionModel}

import com.github.tminglei.slickpg.InetString

object UserActionLogger {

  def log[Ctx, F[_]](
      request: AuthRequest[_],
      action: LoggedAction[Ctx],
      actionContextId: DbRef[Ctx],
      newState: String,
      oldState: String
  )(implicit service: ModelService[F]): F[Model[LoggedActionModel[Ctx]]] =
    service.insert(
      LoggedActionModel(
        request.user.id,
        InetString(StatTracker.remoteAddress(request)),
        action,
        action.context,
        actionContextId,
        newState,
        oldState
      )
    )
}
