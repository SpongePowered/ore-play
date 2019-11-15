package controllers.apiv2.helpers

import io.circe.generic.extras.ConfiguredJsonCodec
import models.protocols.APIV2.circeConfig

@ConfiguredJsonCodec case class Pagination(
    limit: Long,
    offset: Long,
    count: Long
)
