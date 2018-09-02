package ore.permission.role

import db.impl.OrePostgresDriver
import db.table.MappedType
import enumeratum.values.{StringEnum, StringEnumEntry}
import models.user.role.{OrganizationRole, ProjectRole}
import ore.Colors._
import slick.jdbc.JdbcType


sealed abstract class RoleType(val value: String ,val forumRoleId: Int, val roleClass: Class[_ <: Role], val trust: Trust,
                               val title: String, val color: Color, val isAssignable: Boolean = true) extends StringEnumEntry with MappedType[RoleType] {
  implicit val mapper: JdbcType[RoleType] = OrePostgresDriver.api.roleTypeTypeMapper
}

sealed abstract class DonorType(override val value: String, override val forumRoleId: Int, override val title: String,
                                override val color: Color, val rank: Int) extends RoleType(value, forumRoleId, classOf[GlobalRole], Default, title, color)

case object RoleType extends StringEnum[RoleType] {

  case object OreAdmin        extends RoleType("Ore_Admin",        61, classOf[GlobalRole], Absolute,   "Ore Admin",          Red)
  case object OreMod          extends RoleType("Ore_Mod",          62, classOf[GlobalRole], Moderation, "Ore Moderator",      Aqua)
  case object SpongeLeader    extends RoleType("Sponge_Leader",    44, classOf[GlobalRole], Default,    "Sponge Leader",      Amber)
  case object TeamLeader      extends RoleType("Team_Leader",      58, classOf[GlobalRole], Default,    "Team Leader",        Amber)
  case object CommunityLeader extends RoleType("Community_Leader", 59, classOf[GlobalRole], Default,    "Community Leader",   Amber)
  case object SpongeStaff     extends RoleType("Sponge_Staff",     3,  classOf[GlobalRole], Default,    "Sponge Staff",       Amber)
  case object SpongeDev       extends RoleType("Sponge_Developer", 41, classOf[GlobalRole], Default,    "Sponge Developer",   Green)
  case object OreDev          extends RoleType("Ore_Dev",          66, classOf[GlobalRole], Default,    "Ore Developer",      Orange)
  case object WebDev          extends RoleType("Web_dev",          45, classOf[GlobalRole], Default,    "Web Developer",      Blue)
  case object Documenter      extends RoleType("Documenter",       51, classOf[GlobalRole], Default,    "Documenter",         Aqua)
  case object Support         extends RoleType("Support",          43, classOf[GlobalRole], Default,    "Support",            Aqua)
  case object Contributor     extends RoleType("Contributor",      49, classOf[GlobalRole], Default,    "Contributor",        Green)
  case object Advisor         extends RoleType("Advisor",          48, classOf[GlobalRole], Default,    "Advisor",            Aqua)

  case object StoneDonor   extends DonorType("Stone_Donor",  57, "Stone Donor",   Gray     , 5)
  case object QuartzDonor  extends DonorType("Quartz_Donor", 54, "Quartz Donor",  Quartz   , 4)
  case object IronDonor    extends DonorType("Iron_Donor",   56, "Iron Donor",    Silver   , 3)
  case object GoldDonor    extends DonorType("Gold_Donor",   53, "Gold Donor",    Gold     , 2)
  case object DiamondDonor extends DonorType("Diamond_Donor",52, "Diamond Donor", LightBlue, 1)

  case object ProjectOwner      extends RoleType("Project_Owner",    -1, classOf[ProjectRole], Absolute, "Owner",      Transparent, isAssignable = false)
  case object ProjectDeveloper  extends RoleType("Project_Developer",-2, classOf[ProjectRole], Publish,  "Developer", Transparent)
  case object ProjectEditor     extends RoleType("Project_Editor",   -3, classOf[ProjectRole], Limited,  "Editor",     Transparent)
  case object ProjectSupport    extends RoleType("Project_Support",  -4, classOf[ProjectRole], Default,  "Support",    Transparent)

  case object Organization        extends RoleType("Organization",           64, classOf[OrganizationRole], Absolute, "Organization", Purple, isAssignable = false)
  case object OrganizationOwner   extends RoleType("Organization_Owner",     -5, classOf[OrganizationRole], Absolute, "Owner",        Purple, isAssignable = false)
  case object OrganizationAdmin   extends RoleType("Organization_Admin",     -9, classOf[OrganizationRole], Lifted,   "Admin",        Purple)
  case object OrganizationDev     extends RoleType("Organization_Developer", -6, classOf[OrganizationRole], Publish,  "Developer",    Transparent)
  case object OrganizationEditor  extends RoleType("Organization_Editor",    -7, classOf[OrganizationRole], Limited,  "Editor",       Transparent)
  case object OrganizationSupport extends RoleType("Organization_Support",   -8, classOf[OrganizationRole], Default,  "Support",      Transparent)

  lazy val values = findValues

}
