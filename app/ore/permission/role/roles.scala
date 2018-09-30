package ore.permission.role

import scala.collection.immutable

import db.ObjectId
import models.user.role.DbRole
import ore.Color
import ore.Color._
import ore.permission.role.Trust._

import enumeratum.values.{StringEnum, StringEnumEntry}

sealed abstract case class Role(
    value: String,
    forumRoleId: Int,
    category: RoleCategory,
    trust: Trust,
    title: String,
    color: Color,
    isAssignable: Boolean = true
) extends StringEnumEntry {

  def toDbRole: DbRole = DbRole(
    id = ObjectId(forumRoleId.toLong),
    name = value,
    category = category,
    trust = trust,
    title = title,
    color = color.hex,
    isAssignable = isAssignable,
    rank = rankOpt
  )

  def rankOpt: Option[Int] = None
}

sealed abstract class DonorRole(
    override val value: String,
    override val forumRoleId: Int,
    override val title: String,
    override val color: Color,
    val rank: Int
) extends Role(value, forumRoleId, RoleCategory.Global, Default, title, color) {

  override def rankOpt: Option[Int] = Some(rank)
}

object Role extends StringEnum[Role] {
  lazy val byIds: Map[Int, Role] = values.map(r => r.forumRoleId -> r).toMap

  object OreAdmin        extends Role("Ore_Admin", 61, RoleCategory.Global, Absolute, "Ore Admin", Red)
  object OreMod          extends Role("Ore_Mod", 62, RoleCategory.Global, Moderation, "Ore Moderator", Aqua)
  object SpongeLeader    extends Role("Sponge_Leader", 44, RoleCategory.Global, Default, "Sponge Leader", Amber)
  object TeamLeader      extends Role("Team_Leader", 58, RoleCategory.Global, Default, "Team Leader", Amber)
  object CommunityLeader extends Role("Community_Leader", 59, RoleCategory.Global, Default, "Community Leader", Amber)
  object SpongeStaff     extends Role("Sponge_Staff", 3, RoleCategory.Global, Default, "Sponge Staff", Amber)
  object SpongeDev       extends Role("Sponge_Developer", 41, RoleCategory.Global, Default, "Sponge Developer", Green)
  object OreDev          extends Role("Ore_Dev", 66, RoleCategory.Global, Default, "Ore Developer", Orange)
  object WebDev          extends Role("Web_dev", 45, RoleCategory.Global, Default, "Web Developer", Blue)
  object Documenter      extends Role("Documenter", 51, RoleCategory.Global, Default, "Documenter", Aqua)
  object Support         extends Role("Support", 43, RoleCategory.Global, Default, "Support", Aqua)
  object Contributor     extends Role("Contributor", 49, RoleCategory.Global, Default, "Contributor", Green)
  object Advisor         extends Role("Advisor", 48, RoleCategory.Global, Default, "Advisor", Aqua)

  object StoneDonor   extends DonorRole("Stone_Donor", 57, "Stone Donor", Gray, 5)
  object QuartzDonor  extends DonorRole("Quartz_Donor", 54, "Quartz Donor", Quartz, 4)
  object IronDonor    extends DonorRole("Iron_Donor", 56, "Iron Donor", Silver, 3)
  object GoldDonor    extends DonorRole("Gold_Donor", 53, "Gold Donor", Gold, 2)
  object DiamondDonor extends DonorRole("Diamond_Donor", 52, "Diamond Donor", LightBlue, 1)

  object ProjectOwner
      extends Role("Project_Owner", -1, RoleCategory.Project, Absolute, "Owner", Transparent, isAssignable = false)
  object ProjectDeveloper extends Role("Project_Developer", -2, RoleCategory.Project, Publish, "Developer", Transparent)
  object ProjectEditor    extends Role("Project_Editor", -3, RoleCategory.Project, Limited, "Editor", Transparent)
  object ProjectSupport   extends Role("Project_Support", -4, RoleCategory.Project, Default, "Support", Transparent)

  object Organization
      extends Role(
        "Organization",
        64,
        RoleCategory.Organization,
        Absolute,
        "Organization",
        Purple,
        isAssignable = false
      )
  object OrganizationOwner
      extends Role(
        "Organization_Owner",
        -5,
        RoleCategory.Organization,
        Absolute,
        "Owner",
        Purple,
        isAssignable = false
      )
  object OrganizationAdmin extends Role("Organization_Admin", -9, RoleCategory.Organization, Lifted, "Admin", Purple)
  object OrganizationDev
      extends Role("Organization_Developer", -6, RoleCategory.Organization, Publish, "Developer", Transparent)
  object OrganizationEditor
      extends Role("Organization_Editor", -7, RoleCategory.Organization, Limited, "Editor", Transparent)
  object OrganizationSupport
      extends Role("Organization_Support", -8, RoleCategory.Organization, Default, "Support", Transparent)

  lazy val values: immutable.IndexedSeq[Role] = findValues

  val projectRoles: immutable.IndexedSeq[Role] = values.filter(_.category == RoleCategory.Project)

  val organizationRoles: immutable.IndexedSeq[Role] = values.filter(_.category == RoleCategory.Organization)
}

sealed trait RoleCategory
object RoleCategory {
  case object Global       extends RoleCategory
  case object Project      extends RoleCategory
  case object Organization extends RoleCategory
}
