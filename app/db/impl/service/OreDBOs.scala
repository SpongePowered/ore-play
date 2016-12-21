package db.impl.service

import db.ModelService
import db.impl.access._
import ore.{OreConfig, OreEnv}

import slick.jdbc.JdbcProfile

abstract class OreDBOs(driver: JdbcProfile, env: OreEnv, config: OreConfig) extends ModelService(driver) {

  val userBase         = new UserBase()(this, this.config)
  val projectBase      = new ProjectBase()(this, this.env, this.config)
  val organizationBase = new OrganizationBase()(this, this.config)
  val competitionBase  = new CompetitionBase()(this, this.config)
}
