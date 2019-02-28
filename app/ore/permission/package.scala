package ore

import shapeless.tag._

package object permission {

  private object SharedTagger extends Tagger[Nothing]
  private def tagS[U]: Tagger[U] = SharedTagger.asInstanceOf[Tagger[U]]

  type Permission = Long @@ Permission.type
  //noinspection TypeAnnotation
  object Permission {

    private[permission] def apply(long: Long): Long @@ Permission.type = tagS[Permission.type](long)

    /**
      * Create a permission that has all the permissions passed in.
      */
    def apply(permissions: Permission*): Permission = permissions.fold(None)(_ ++ _)

    /**
      * Create a permission from an int.
      */
    def fromLong(long: Long): Long @@ Permission.type = apply(long)

    val None = Permission(0)
    val All  = Permission(0xFFFFFFFFFFFFFFFFL)

    val ViewPublicInfo   = Permission(1L << 0)
    val EditUserSettings = Permission(1L << 1)
    val EditApiKeys      = Permission(1L << 2)

    private val EditSubjectSettings  = Permission(1L << 4)
    private val ManageSubjectMembers = Permission(1L << 5)
    private val IsSubjectOwner       = Permission(1L << 6)

    val CreateProject        = Permission(1L << 8)
    val EditPage             = Permission(1L << 9)
    val EditProjectSettings  = EditSubjectSettings
    val ManageProjectMembers = ManageSubjectMembers
    val IsProjectOwner       = IsSubjectOwner ++ EditPage ++ EditProjectSettings ++ ManageProjectMembers

    val CreateVersion = Permission(1L << 12)
    val EditVersion   = Permission(1L << 13)
    val DeleteVersion = Permission(1L << 14)
    val EditChannel   = Permission(1L << 15) //To become edit tags later

    val CreateOrganization        = Permission(1L << 20)
    val PostAsOrganization        = Permission(1L << 21)
    val EditOrganizationSettings  = EditSubjectSettings
    val ManageOrganizationMembers = ManageSubjectMembers
    val IsOrganizationOwner       = IsProjectOwner ++ PostAsOrganization

    val ModNotesAndFlags = Permission(1L << 24)
    val SeeHidden        = Permission(1L << 25)
    val IsStaff          = Permission(1L << 26)
    val Reviewer         = Permission(1L << 27)

    val ViewHealth                       = Permission(1L << 32)
    val ViewIp                           = Permission(1L << 33)
    val ViewStats                        = Permission(1L << 34)
    val ViewLogs                         = Permission(1L << 35)
    private val ViewSubjectVisibilityLog = Permission(1L << 36)
    val ViewProjectVisibilityLog         = ViewSubjectVisibilityLog
    val ViewVersionVisibilityLog         = ViewSubjectVisibilityLog

    val ChangeRawVisibility = Permission(1L << 40)
    val HardDeleteProject   = Permission(1L << 41)
    val HardDeleteVersion   = Permission(1L << 42)
  }

  implicit class PermissionSyntax(private val permission: Permission) extends AnyVal {

    /**
      * Add a permission to this permission.
      * @param other The other permission.
      */
    def addPermissions(other: Permission): Permission = Permission(permission | other)

    /**
      * Add a permission to this permission.
      * @param other The other permission.
      */
    def ++(other: Permission): Permission = addPermissions(other)

    /**
      * Remove a permission from this permission.
      * @param other The permission to remove.
      */
    def removePermissions(other: Permission): Permission = Permission(permission & ~other)

    /**
      * Remove a permission from this permission.
      * @param other The permission to remove.
      */
    def --(other: Permission): Permission = removePermissions(other)

    /**
      * Toggle a permission in this permission.
      * @param other The permission to toggle.
      */
    def togglePermissions(other: Permission): Permission = Permission(permission ^ other)

    /**
      * Check if this permission has a permission.
      * @param other The permission to check against.
      */
    def hasPermissions(other: Permission): Boolean = (permission & other) == other

    /**
      * Check if this permission grants any permissions.
      */
    def isNone: Boolean = permission == 0
  }
}
