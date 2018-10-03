package controllers.sugar

import scala.language.{higherKinds, implicitConversions}

import scala.concurrent.Future

import play.api.data.{Form, FormError}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Action, ActionBuilder, AnyContent, BodyParser, Call, Flash, Request, Result}

import controllers.sugar
import controllers.sugar.ActionHelpers.LikeFuture
import controllers.sugar.Requests.OreRequest

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.{Functor, Monad}
import com.google.common.base.Preconditions.checkArgument

/**
  * A helper class for some common functions of controllers.
  */
trait ActionHelpers {

  /**
    * Returns a NotFound result with the 404 HTML template.
    *
    * @return NotFound
    */
  def notFound(implicit request: OreRequest[_]): Result

  /**
    * Redirects to the specified call with the errors of the specified
    * Form.
    *
    * @param call Call to redirect to
    * @param form Form with error
    * @return     Redirect to call
    */
  def FormErrorLocalized(call: Call): Form[_] => Result = form => {
    checkArgument(form.errors.nonEmpty, "no errors", "")
    val errors = form.errors.map(e => s"${e.message}.${e.key}")
    Redirect(call).withErrors(errors.toList)
  }

  /**
    * Redirects to the specified call with the errors of the specified
    * Form.
    *
    * @param call Call to redirect to
    * @param form Form with error
    * @return     Redirect to call
    */
  def FormError(call: Call): Form[_] => Result = form => {
    checkArgument(form.errors.nonEmpty, "no errors", "")
    Redirect(call).withFormErrors(form.errors)
  }

  implicit def toOreResultOps(result: Result): ActionHelpers.OreResultOps =
    new ActionHelpers.OreResultOps(result)

  implicit def toFormBindOps[A](form: Form[A]): ActionHelpers.FormBindOps[A] =
    new sugar.ActionHelpers.FormBindOps(form)

  implicit class OreActionBuilderOps[R[_], B](private val action: ActionBuilder[R, B]) {

    def asyncF[F[_]: LikeFuture](fr: => F[Result]): Action[AnyContent] = action.async(LikeFuture.toFuture(fr))

    def asyncF[F[_]: LikeFuture](fr: R[B] => F[Result]): Action[B] = action.async(r => LikeFuture.toFuture(fr(r)))

    def asyncEitherT[F[_]: LikeFuture: Functor](fr: => EitherT[F, Result, Result]): Action[AnyContent] =
      action.async(LikeFuture.toFuture(fr.merge))

    def asyncEitherT[F[_]: LikeFuture: Functor](fr: R[B] => EitherT[F, Result, Result]): Action[B] =
      action.async(r => LikeFuture.toFuture(fr(r).merge))

    def asyncEitherT[F[_]: LikeFuture: Functor, A](
        bodyParser: BodyParser[A]
    )(fr: R[A] => EitherT[F, Result, Result]): Action[A] =
      action.async(bodyParser)(r => LikeFuture.toFuture(fr(r).merge))
  }
}
object ActionHelpers {

  class OreResultOps(private val result: Result) {

    /**
      * Adds an alert message to the result.
      *
      * @param tpe    Alert type
      * @param alert  Alert message
      * @return       Result with error
      */
    def withAlert(tpe: String, alert: String): Result = {
      val flash = result.newFlash.fold(Flash(Map(tpe -> alert)))(f => Flash(f.data + (tpe -> alert)))
      result.flashing(flash)
    }

    /**
      * Adds one or more alerts messages to the result.
      *
      * @param tpe    Alert type
      * @param alerts  Alert messages
      * @return        Result with alerts
      */
    def withAlerts(tpe: String, alerts: List[String]): Result = alerts match {
      case Nil           => result
      case single :: Nil => withAlert(tpe, single)
      case multiple =>
        val numPart   = s"$tpe-num" -> multiple.size.toString
        val newValues = numPart :: multiple.zipWithIndex.map { case (e, i) => s"$tpe-$i" -> e }

        val flash = result.newFlash.fold(Flash(newValues.toMap))(f => Flash(f.data ++ newValues))

        result.flashing(flash)
    }

    /**
      * Adds an error message to the result.
      *
      * @param error  Error message
      * @return       Result with error
      */
    def withError(error: String): Result = withAlert("error", error)

    /**
      * Adds one or more error messages to the result.
      *
      * @param errors  Error messages
      * @return        Result with errors
      */
    def withErrors(errors: List[String]): Result = withAlerts("error", errors)

    /**
      * Adds one or more form error messages to the result.
      *
      * @param errors  Error messages
      * @return        Result with errors
      */
    def withFormErrors(errors: Seq[FormError]): Result = withAlerts("error", errors.flatMap(_.messages).toList)

    /**
      * Adds a success message to the result.
      *
      * @param message  Success message
      * @return         Result with message
      */
    def withSuccess(message: String): Result = withAlert("success", message)

    /**
      * Adds one or more success messages to the result.
      *
      * @param messages  Success messages
      * @return          Result with message
      */
    def withSuccesses(messages: List[String]): Result = withAlerts("success", messages)
  }

  trait LikeFuture[F[_]] {
    def toFuture[A](fa: F[A]): Future[A]
  }
  object LikeFuture {
    def toFuture[F[_], A](fa: F[A])(implicit likeFuture: LikeFuture[F]): Future[A] =
      likeFuture.toFuture(fa)

    implicit val futureLikeFuture: LikeFuture[Future] = new LikeFuture[Future] {
      override def toFuture[A](fa: Future[A]): Future[A] = fa
    }

    implicit val ioLikeFuture: LikeFuture[IO] = new LikeFuture[IO] {
      override def toFuture[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
    }
  }

  class FormBindOps[A](private val form: Form[A]) extends AnyVal {
    def bindEitherT[F[_]] = new BindFormEitherTPartiallyApplied[F, A](form)

    def bindOptionT[F[_]](implicit F: Monad[F], request: Request[_]): OptionT[F, A] =
      form.bindFromRequest().fold(_ => OptionT.none[F, A], OptionT.some[F](_))
  }

  final class BindFormEitherTPartiallyApplied[F[_], B](private val form: Form[B]) extends AnyVal {
    def apply[A](left: Form[B] => A)(implicit F: Monad[F], request: Request[_]): EitherT[F, A, B] =
      form.bindFromRequest().fold(left.andThen(EitherT.leftT[F, B](_)), EitherT.rightT[F, A](_))
  }
}
