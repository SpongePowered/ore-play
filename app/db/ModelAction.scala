package db

import slick.dbio.{DBIOAction, NoStream}

object ModelAction {

  /**
    * Represents a DBIOAction that returns one or multiple Models. ModelActions
    * are implicitly wrapped and unwrapped.
    *
    * @param action DBIOAction to wrap
    * @tparam R     Return type
    */
  abstract class AbstractModelAction[R](val action: DBIOAction[R, NoStream, Nothing]) {
    def processResult(service: ModelService, result: R): R
  }

  /**
    * Represents a ModelAction that returns a single model.
    *
    * @param action DBIOAction to wrap
    * @tparam M     Model type
    */
  case class ModelAction[M <: Model](override val action: DBIOAction[M, NoStream, Nothing])
    extends AbstractModelAction(action) {
    def processResult(service: ModelService, result: M): M = process(service, result)
  }

  /**
    * Represents a ModelAction that returns multiple models.
    *
    * @param action DBIOAction to wrap
    * @tparam M     Model type
    */
  case class ModelSeqAction[M <: Model](override val action: DBIOAction[Seq[M], NoStream, Nothing])
    extends AbstractModelAction(action) {
    def processResult(service: ModelService, result: Seq[M]): Seq[M] = for (model <- result) yield {
      process(service, model)
    }
  }

  implicit def unwrap[M](action: AbstractModelAction[M]): DBIOAction[M, NoStream, Nothing] = action.action
  implicit def wrapSingle[M <: Model](action: DBIOAction[M, NoStream, Nothing]): ModelAction[M]
  = ModelAction(action)
  implicit def unwrapSingle[M <: Model](action: ModelAction[M]): DBIOAction[M, NoStream, Nothing]
  = action.action
  implicit def wrapSeq[M <: Model](action: DBIOAction[Seq[M], NoStream, Nothing]): ModelSeqAction[M]
  = ModelSeqAction(action)
  implicit def unwrapSeq[M <: Model](action: ModelSeqAction[M]): DBIOAction[Seq[M], NoStream, Nothing]
  = action.action

  private def process[M <: Model](service: ModelService, model: M): M = {
    if (!model.isProcessed)
      service.processor.process(model)
    model
  }

}
