package ore.permission.role

import db.impl.OrePostgresDriver
import db.table.MappedType
import models.user.role.{OrganizationRole, ProjectRole}
import ore.Colors._
import slick.jdbc.JdbcType

import scala.language.implicitConversions

/**
  * Represents a collection of roles a User may have.
  */
object RoleTypes extends Enumeration {

  // Global

  val Admin           = new  RoleType( 0, 61, classOf[GlobalRole], Absolute, "Ore Admin",          Red)
  val Mod             = new  RoleType( 1, 62, classOf[GlobalRole], Lifted,   "Ore Moderator",      Aqua)
  val SpongeLeader    = new  RoleType( 2, 44, classOf[GlobalRole], Default,  "Sponge Leader",      Amber)
  val TeamLeader      = new  RoleType( 3, 58, classOf[GlobalRole], Default,  "Team Leader",        Amber)
  val CommunityLeader = new  RoleType( 4, 59, classOf[GlobalRole], Default,  "Community Leader",   Amber)
  val Staff           = new  RoleType( 5, 3,  classOf[GlobalRole], Default,  "Sponge Staff",       Amber)
  val SpongeDev       = new  RoleType( 6, 41, classOf[GlobalRole], Default,  "Sponge Developer",   Green)
  val OreDev          = new  RoleType(27, 66, classOf[GlobalRole], Default,  "Ore Developer",      Orange)
  val WebDev          = new  RoleType( 7, 45, classOf[GlobalRole], Default,  "Web Developer",      Blue)
  val Scribe          = new  RoleType( 8, 51, classOf[GlobalRole], Default,  "Sponge Documenter",  Aqua)
  val Support         = new  RoleType( 9, 43, classOf[GlobalRole], Default,  "Sponge Support",     Aqua)
  val Contributor     = new  RoleType(10, 49, classOf[GlobalRole], Default,  "Sponge Contributor", Green)
  val Adviser         = new  RoleType(11, 48, classOf[GlobalRole], Default,  "Sponge Adviser",     Aqua)

  val StoneDonor   = new DonorType(12, 57, "Stone Donor",   Gray)
  val QuartzDonor  = new DonorType(13, 54, "Quartz Donor",  Quartz)
  val IronDonor    = new DonorType(14, 56, "Iron Donor",    Silver)
  val GoldDonor    = new DonorType(15, 53, "Gold Donor",    Gold)
  val DiamondDonor = new DonorType(16, 52, "Diamond Donor", LightBlue)

  // Project

  val ProjectOwner   = new RoleType(17, -1, classOf[ProjectRole], Absolute, "Owner",    Transparent,
                                    isAssignable = false)
  val ProjectDev     = new RoleType(18, -2, classOf[ProjectRole], Standard, "Developer",Transparent)
  val ProjectEditor  = new RoleType(19, -3, classOf[ProjectRole], Limited,  "Editor",   Transparent)
  val ProjectSupport = new RoleType(20, -4, classOf[ProjectRole], Default,  "Support",  Transparent)

  // Organization

  val Organization        = new RoleType(21, 64, classOf[OrganizationRole], Absolute, "Organization", Purple,
                                         isAssignable = false)
  val OrganizationOwner   = new RoleType(22, -5, classOf[OrganizationRole], Absolute, "Owner",        Purple,
                                         isAssignable = false)
  val OrganizationAdmin   = new RoleType(26, -9, classOf[OrganizationRole], Lifted,   "Admin",        Purple)
  val OrganizationDev     = new RoleType(23, -6, classOf[OrganizationRole], Standard, "Developer",    Transparent)
  val OrganizationEditor  = new RoleType(24, -7, classOf[OrganizationRole], Limited,  "Editor",       Transparent)
  val OrganizationSupport = new RoleType(25, -8, classOf[OrganizationRole], Default,  "Support",      Transparent)

  /**
    * Returns the role with the specified external ID.
    *
    * @param id Sponge ID
    * @return   UserRole with ID
    */
  def withId(id: Int): RoleType = this.values.find(_.roleId == id).getOrElse {
    // Throw an exception instead of returning an Option to match Enumeration
    // behavior
    throw new NoSuchElementException
  }

  /**
    * Represents a User role.
    *
    * @param i      Index
    * @param roleId ID of role
    * @param trust  Level of trust that this user has
    * @param title  Title to display
    * @param color  Color to display
    */
  class RoleType(val i: Int,
                 val roleId: Int,
                 val roleClass: Class[_ <: Role],
                 val trust: Trust,
                 val title: String,
                 val color: Color,
                 val isAssignable: Boolean = true)
                 extends super.Val(i, title) with MappedType[RoleType] {
    implicit val mapper: JdbcType[RoleType] = OrePostgresDriver.api.roleTypeTypeMapper
  }

  /**
    * Represents a type of Donor.
    *
    * @param i      Index
    * @param roleId ID of role
    * @param title  Title to display
    * @param color  Color to display
    */
  class DonorType(override val i: Int,
                  override val roleId: Int,
                  override val title: String,
                  override val color: Color)
                  extends RoleType(i, roleId, classOf[GlobalRole], Default, title, color)

  implicit def convert(value: Value): RoleType = value.asInstanceOf[RoleType]

}
