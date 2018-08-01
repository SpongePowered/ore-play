package db.impl.service

import db.ModelService
import db.impl.access._
import discourse.OreDiscourseApi
import ore.{OreConfig, OreEnv}
import play.api.i18n.MessagesApi
import security.spauth.SpongeAuthApi

trait OreDBOs extends ModelService {

  val env: OreEnv
  val config: OreConfig
  val messages: MessagesApi

  val Users = new UserBase()(this, this.config)
  val Projects = new ProjectBase()(this, this.env, this.config)
  val Organizations = new OrganizationBase()(this, this.config, this.messages)

}
