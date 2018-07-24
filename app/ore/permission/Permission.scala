package ore.permission

import ore.permission.role._

/**
  * Represents a permission for a user to do something in the application.
  */
sealed trait Permission { def trust: Trust }
case object ResetOre            extends Permission { val trust = Absolute   }
case object SeedOre             extends Permission { val trust = Absolute   }
case object MigrateOre          extends Permission { val trust = Absolute   }
case object HardRemoveProject   extends Permission { val trust = Absolute   }
case object CreateProject       extends Permission { val trust = Absolute   }
case object ViewIp              extends Permission { val trust = Absolute   }
case object EditSettings        extends Permission { val trust = Lifted     }
case object ViewLogs            extends Permission { val trust = Lifted     }
case object UserAdmin           extends Permission { val trust = Lifted     }
case object EditApiKeys         extends Permission { val trust = Lifted     }
case object UploadVersions      extends Permission { val trust = Publish    }
case object ReviewFlags         extends Permission { val trust = Moderation }
case object ReviewProjects      extends Permission { val trust = Moderation }
case object HideProjects        extends Permission { val trust = Moderation }
case object EditChannels        extends Permission { val trust = Moderation }
case object ReviewVisibility    extends Permission { val trust = Moderation }
case object ViewHealth          extends Permission { val trust = Moderation }
case object PostAsOrganization  extends Permission { val trust = Moderation }
case object ViewActivity        extends Permission { val trust = Moderation }
case object ViewStats           extends Permission { val trust = Moderation }
case object EditPages           extends Permission { val trust = Limited    }
case object EditVersions        extends Permission { val trust = Limited    }
