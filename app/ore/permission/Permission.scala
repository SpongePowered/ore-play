package ore.permission

import ore.permission.role._

/**
  * Represents a permission for a user to do something in the application.
  */
sealed abstract case class Permission(trust: Trust)
case object ResetOre            extends Permission(Absolute)
case object SeedOre             extends Permission(Absolute)
case object MigrateOre          extends Permission(Absolute)
case object HardRemoveProject   extends Permission(Absolute)
case object HardRemoveVersion   extends Permission(Absolute)
case object CreateProject       extends Permission(Absolute)
case object ViewIp              extends Permission(Absolute)
case object EditSettings        extends Permission(Lifted)
case object ViewLogs            extends Permission(Lifted)
case object UserAdmin           extends Permission(Lifted)
case object EditApiKeys         extends Permission(Lifted)
case object UploadVersions      extends Permission(Publish)
case object ReviewFlags         extends Permission(Moderation)
case object ReviewProjects      extends Permission(Moderation)
case object HideProjects        extends Permission(Moderation)
case object EditChannels        extends Permission(Moderation)
case object ReviewVisibility    extends Permission(Moderation)
case object ViewHealth          extends Permission(Moderation)
case object PostAsOrganization  extends Permission(Moderation)
case object ViewActivity        extends Permission(Moderation)
case object ViewStats           extends Permission(Moderation)
case object EditPages           extends Permission(Limited)
case object EditVersions        extends Permission(Limited)
