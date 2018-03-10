package util

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents an action pending completion.
  */
trait PendingAction[R] {

  /**
    * Completes the action.
    */
  def complete(implicit ec: ExecutionContext): Future[R]

  /**
    * Cancels the action.
    */
  def cancel(implicit ec: ExecutionContext)

}
