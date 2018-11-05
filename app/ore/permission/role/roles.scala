package ore.permission.role

import scala.collection.immutable

import db.ObjId
import models.user.role.DbRole
import ore.Color
import ore.Color._
import ore.permission.role.Trust._

import enumeratum.values.{StringEnum, StringEnumEntry}

sealed abstract case class Role(
    value: String,
    roleId: Int,
    category: RoleCategory,
    trust: Trust,
    title: String,
    color: Color,
    isAssignable: Boolean = true
) extends StringEnumEntry {

  def toDbRole: DbRole = DbRole(
    id = ObjId(roleId.toLong),
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
    override val roleId: Int,
    override val title: String,
    override val color: Color,
    val rank: Int
) extends Role(value, roleId, RoleCategory.Global, Default, title, color) {

  override def rankOpt: Option[Int] = Some(rank)
}

object Role extends StringEnum[Role] {
  lazy val byIds: Map[Int, Role] = values.map(r => r.roleId -> r).toMap

  object OreAdmin        extends Role("Ore_Admin", 1, RoleCategory.Global, Absolute, "Ore Admin", Red)
  object OreMod          extends Role("Ore_Mod", 2, RoleCategory.Global, Moderation, "Ore Moderator", Aqua)
  object SpongeLeader    extends Role("Sponge_Leader", 3, RoleCategory.Global, Default, "Sponge Leader", Amber)
  object TeamLeader      extends Role("Team_Leader", 4, RoleCategory.Global, Default, "Team Leader", Amber)
  object CommunityLeader extends Role("Community_Leader", 5, RoleCategory.Global, Default, "Community Leader", Amber)
  object SpongeStaff     extends Role("Sponge_Staff", 6, RoleCategory.Global, Default, "Sponge Staff", Amber)
  object SpongeDev       extends Role("Sponge_Developer", 7, RoleCategory.Global, Default, "Sponge Developer", Green)
  object OreDev          extends Role("Ore_Dev", 8, RoleCategory.Global, Default, "Ore Developer", Orange)
  object WebDev          extends Role("Web_dev", 9, RoleCategory.Global, Default, "Web Developer", Blue)
  object Documenter      extends Role("Documenter", 10, RoleCategory.Global, Default, "Documenter", Aqua)
  object Support         extends Role("Support", 11, RoleCategory.Global, Default, "Support", Aqua)
  object Contributor     extends Role("Contributor", 12, RoleCategory.Global, Default, "Contributor", Green)
  object Advisor         extends Role("Advisor", 13, RoleCategory.Global, Default, "Advisor", Aqua)

  object StoneDonor   extends DonorRole("Stone_Donor", 14, "Stone Donor", Gray, 5)
  object QuartzDonor  extends DonorRole("Quartz_Donor", 15, "Quartz Donor", Quartz, 4)
  object IronDonor    extends DonorRole("Iron_Donor", 16, "Iron Donor", Silver, 3)
  object GoldDonor    extends DonorRole("Gold_Donor", 17, "Gold Donor", Gold, 2)
  object DiamondDonor extends DonorRole("Diamond_Donor", 18, "Diamond Donor", LightBlue, 1)

  object ProjectOwner
      extends Role("Project_Owner", 19, RoleCategory.Project, Absolute, "Owner", Transparent, isAssignable = false)
  object ProjectDeveloper extends Role("Project_Developer", 20, RoleCategory.Project, Publish, "Developer", Transparent)
  object ProjectEditor    extends Role("Project_Editor", 21, RoleCategory.Project, Limited, "Editor", Transparent)
  object ProjectSupport   extends Role("Project_Support", 22, RoleCategory.Project, Default, "Support", Transparent)

  object Organization
      extends Role(
        "Organization",
        23,
        RoleCategory.Organization,
        Absolute,
        "Organization",
        Purple,
        isAssignable = false
      )
  object OrganizationOwner
      extends Role(
        "Organization_Owner",
        24,
        RoleCategory.Organization,
        Absolute,
        "Owner",
        Purple,
        isAssignable = false
      )
  object OrganizationAdmin extends Role("Organization_Admin", 25, RoleCategory.Organization, Lifted, "Admin", Purple)
  object OrganizationDev
      extends Role("Organization_Developer", 26, RoleCategory.Organization, Publish, "Developer", Transparent)
  object OrganizationEditor
      extends Role("Organization_Editor", 27, RoleCategory.Organization, Limited, "Editor", Transparent)
  object OrganizationSupport
      extends Role("Organization_Support", 28, RoleCategory.Organization, Default, "Support", Transparent)

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
