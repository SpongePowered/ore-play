package ore.permission

import ore.permission.role._

/**
  * Represents a permission for a user to do something in the application.
  */
sealed abstract case class Permission(trust: Trust)
object ResetOre            extends Permission(Absolute)
object SeedOre             extends Permission(Absolute)
object MigrateOre          extends Permission(Absolute)
object HardRemoveProject   extends Permission(Absolute)
object HardRemoveVersion   extends Permission(Absolute)
object CreateProject       extends Permission(Absolute)
object ViewIp              extends Permission(Absolute)
object EditSettings        extends Permission(Lifted)
object ViewLogs            extends Permission(Lifted)
object UserAdmin           extends Permission(Lifted)
object EditApiKeys         extends Permission(Lifted)
object UploadVersions      extends Permission(Publish)
object ReviewFlags         extends Permission(Moderation)
object ReviewProjects      extends Permission(Moderation)
object HideProjects        extends Permission(Moderation)
object EditChannels        extends Permission(Moderation)
object ReviewVisibility    extends Permission(Moderation)
object ViewHealth          extends Permission(Moderation)
object PostAsOrganization  extends Permission(Moderation)
object ViewActivity        extends Permission(Moderation)
object ViewStats           extends Permission(Moderation)
object EditPages           extends Permission(Limited)
object EditVersions        extends Permission(Limited)
