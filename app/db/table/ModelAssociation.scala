package db.table

import scala.concurrent.Future

import com.google.common.base.Preconditions._
import db.impl.OrePostgresDriver.api._
import db.{Model, ModelService}

/**
  * Represents an association between two models handled by an associative table.
  *
  * @param service      ModelService
  * @param tableClass   AssociativeTable class
  * @param assocTable   AssociativeTable TableQuery instance
  * @tparam AssocTable  AssociativeTable type
  */
class ModelAssociation[AssocTable <: AssociativeTable]
                      (service: ModelService,
                       ref1: AssocTable => Rep[Int],
                       ref2: AssocTable => Rep[Int],
                       val tableClass: Class[AssocTable],
                       val assocTable: TableQuery[AssocTable]) {

  checkNotNull(this.service, "service null", "")
  checkNotNull(this.ref1, "ref1 null", "")
  checkNotNull(this.ref2, "ref2 null", "")
  checkNotNull(this.tableClass, "table class null", "")
  checkNotNull(this.assocTable, "table null", "")

  /**
    * Associates the two models.
    *
    * @param model1 First model
    * @param model2 Second model
    * @return Future results
    */
  def assoc(model1: Model, model2: Model): Future[Int] = {
    val modelPair = orderModels(model1, model2)
    this.service.DB.db.run(this.assocTable += (modelPair._1.id.value, modelPair._2.id.value))
  }

  /**
    * Disassociates the two models.
    *
    * @param model1 First model
    * @param model2 Second model
    */
  def disassoc(model1: Model, model2: Model): Future[Int] = {
    val modelPair = orderModels(model1, model2)
    this.service.DB.db.run {
      this.assocTable.filter(t => ref1(t) === modelPair._1.id.value && ref2(t) === modelPair._2.id.value).delete
    }
  }

  private def orderModels(model1: Model, model2: Model): (Model, Model) = {
    checkNotNull(model1, "model1 null", "")
    checkNotNull(model2, "model2 null", "")
    val firstModel = this.assocTable.baseTableRow.firstClass
    val secondModel = this.assocTable.baseTableRow.secondClass
    val clazz1 = model1.getClass
    val clazz2 = model2.getClass
    if (clazz1.equals(firstModel)) {
      if (clazz2.equals(secondModel)) {
        (model1, model2)
      } else {
        throw ModelPairException
      }
    } else if (clazz1.equals(secondModel)) {
      if (clazz2.equals(firstModel)) {
        (model2, model1)
      } else {
        throw ModelPairException
      }
    } else
      throw ModelPairException
  }

  private def ModelPairException = new RuntimeException("invalid model pair for association")

}
