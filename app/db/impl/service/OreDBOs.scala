package db.impl.service

import db.ModelService
import db.impl.access._
import ore.{OreConfig, OreEnv}

import slick.jdbc.JdbcProfile

abstract class OreDBOs(driver: JdbcProfile, env: OreEnv, config: OreConfig) extends ModelService(driver) {

  val Users         = new UserBase()(this, this.config)
  val Projects      = new ProjectBase()(this, this.env, this.config)
  val Organizations = new OrganizationBase()(this, this.config)
}
