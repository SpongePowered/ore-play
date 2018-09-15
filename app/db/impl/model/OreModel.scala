package db.impl.model

import db.{Model, ObjectId, ObjectTimestamp}
import db.impl.access.{OrganizationBase, ProjectBase, UserBase}
import discourse.OreDiscourseApi
import ore.OreConfig
import security.spauth.SpongeAuthApi

/** An Ore Model */
abstract class OreModel(override val id: ObjectId,
                        override val createdAt: ObjectTimestamp)
                       (implicit var userBase: UserBase = null,
                        var projectBase: ProjectBase = null,
                        var organizationBase: OrganizationBase = null,
                        var config: OreConfig = null,
                        var forums: OreDiscourseApi = null,
                        var auth: SpongeAuthApi = null)
                        extends Model(id, createdAt)
