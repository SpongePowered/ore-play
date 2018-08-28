package db.impl

import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import db.impl.model.OreModel
import db.{Model, ModelProcessor, ModelService}
import discourse.OreDiscourseApi
import ore.OreConfig
import security.spauth.SpongeAuthApi

/**
  * A ModelProcessor that performs Ore specific model processing.
  *
  * @param service  ModelService
  * @param config   Ore config
  */
class OreModelProcessor(service: ModelService,
                        users: UserBase,
                        projects: ProjectBase,
                        organizations: OrganizationBase,
                        config: OreConfig,
                        forums: OreDiscourseApi,
                        auth: SpongeAuthApi)
                        extends ModelProcessor(service) {

  override def process[M <: Model](model: M): M = {
    super.process(model) match {
      case oreModel: OreModel =>
        oreModel.userBase = this.users
        oreModel.projectBase = this.projects
        oreModel.organizationBase = this.organizations
        oreModel.config = this.config
        oreModel.forums = this.forums
        oreModel.auth = this.auth
      case _ =>
    }
    model
  }

}
