package controllers.sugar

import com.google.common.base.Preconditions.{checkArgument, checkNotNull}

import play.api.data.{Form, FormError}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, Flash, Result}

import cats.data.{NonEmptyList => NEL}

/**
  * A helper class for some common functions of controllers.
  */
trait ActionHelpers {

  /**
    * Redirects to the specified call with the first error of the specified
    * Form.
    *
    * @param call Call to redirect to
    * @param form Form with error
    * @return     Redirect to call
    */
  def FormError(call: Call, form: Form[_]): Result = {
    checkNotNull(call, "null call", "")
    checkNotNull(form, "null form", "")
    checkArgument(form.errors.nonEmpty, "no errors", "")
    val errors = form.errors.map(e => s"${e.message}.${e.key}")
    Redirect(call).withErrors(errors.toList)
  }

  implicit final class SpongeResult(result: Result) {

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
      case multiple      =>
        val numPart = s"$tpe-num" -> multiple.size.toString
        val newValues = (numPart :: multiple.zipWithIndex.map { case (e, i) => s"$tpe-$i" -> e }).toList

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

}
