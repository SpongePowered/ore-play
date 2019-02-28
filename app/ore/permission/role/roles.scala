package ore.permission.role

import java.sql.Timestamp
import java.time.Instant

import scala.collection.immutable

import db.{Model, ObjId, ObjTimestamp}
import models.user.role.DbRole
import ore.Color
import ore.Color._
import ore.permission.{Permission => Perm}

import enumeratum.values.{StringEnum, StringEnumEntry}

sealed abstract case class Role(
    value: String,
    roleId: Int,
    permissions: Perm,
    title: String,
    color: Color,
    isAssignable: Boolean = true
) extends StringEnumEntry {

  def toDbRole: Model[DbRole] =
    DbRole.asDbModel(
      DbRole(
        name = value,
        permissions = permissions,
        title = title,
        color = color.hex,
        isAssignable = isAssignable,
        rank = rankOpt
      ),
      ObjId(roleId.toLong),
      ObjTimestamp(Timestamp.from(Instant.EPOCH))
    )

  def rankOpt: Option[Int] = None
}

sealed abstract class DonorRole(
    override val value: String,
    override val roleId: Int,
    override val title: String,
    override val color: Color,
    val rank: Int
) extends Role(value, roleId, Perm.None, title, color) {

  override def rankOpt: Option[Int] = Some(rank)
}

object Role extends StringEnum[Role] {
  lazy val byIds: Map[Int, Role] = values.map(r => r.roleId -> r).toMap

  object OreAdmin extends Role("Ore_Admin", 1, Perm.All, "Ore Admin", Red)
  object OreMod
      extends Role(
        "Ore_Mod",
        2,
        Perm(
          Perm.Reviewer,
          Perm.ModNotesAndFlags
        ),
        "Ore Moderator",
        Aqua
      )
  object SpongeLeader    extends Role("Sponge_Leader", 3, Perm.None, "Sponge Leader", Amber)
  object TeamLeader      extends Role("Team_Leader", 4, Perm.None, "Team Leader", Amber)
  object CommunityLeader extends Role("Community_Leader", 5, Perm.None, "Community Leader", Amber)
  object SpongeStaff
      extends Role(
        "Sponge_Staff",
        6,
        Perm(Perm.IsStaff, Perm.SeeHidden),
        "Sponge Staff",
        Amber
      )
  object SpongeDev extends Role("Sponge_Developer", 7, Perm.None, "Sponge Developer", Green)
  object OreDev
      extends Role(
        "Ore_Dev",
        8,
        Perm(Perm.ViewStats, Perm.ViewLogs, Perm.ViewHealth, Perm.ChangeRawVisibility),
        "Ore Developer",
        Orange
      )
  object WebDev      extends Role("Web_Dev", 9, Perm.None, "Web Developer", Blue)
  object Documenter  extends Role("Documenter", 10, Perm.None, "Documenter", Aqua)
  object Support     extends Role("Support", 11, Perm.None, "Support", Aqua)
  object Contributor extends Role("Contributor", 12, Perm.None, "Contributor", Green)
  object Advisor     extends Role("Advisor", 13, Perm.None, "Advisor", Aqua)

  object StoneDonor   extends DonorRole("Stone_Donor", 14, "Stone Donor", Gray, 5)
  object QuartzDonor  extends DonorRole("Quartz_Donor", 15, "Quartz Donor", Quartz, 4)
  object IronDonor    extends DonorRole("Iron_Donor", 16, "Iron Donor", Silver, 3)
  object GoldDonor    extends DonorRole("Gold_Donor", 17, "Gold Donor", Gold, 2)
  object DiamondDonor extends DonorRole("Diamond_Donor", 18, "Diamond Donor", LightBlue, 1)

  object ProjectOwner
      extends Role(
        "Project_Owner",
        19,
        Perm(
          Perm.IsSubjectOwner,
          Perm.EditSubjectSettings,
          Perm.ManageSubjectMembers,
          Perm.CreateProject,
          Perm.EditChannel,
          Perm.CreateVersion,
          Perm.EditVersion,
          Perm.DeleteVersion,
          Perm.EditPage
        ),
        "Owner",
        Transparent,
        isAssignable = false
      )

  object Organization
      extends Role(
        "Organization",
        23,
        Perm.None,
        "Organization",
        Purple,
        isAssignable = false
      )
  object OrganizationOwner
      extends Role(
        "Organization_Owner",
        24,
        Perm(
          Perm.IsSubjectOwner,
          Perm.EditSubjectSettings,
          Perm.ManageSubjectMembers,
          Perm.CreateProject,
          Perm.EditChannel,
          Perm.CreateVersion,
          Perm.EditVersion,
          Perm.DeleteVersion,
          Perm.EditPage,
          Perm.PostAsOrganization
        ),
        "Owner",
        Purple,
        isAssignable = false
      )

  lazy val values: immutable.IndexedSeq[Role] = findValues
}
