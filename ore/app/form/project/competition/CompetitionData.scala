package form.project.competition

import java.time.{LocalDateTime, ZoneId}

trait CompetitionData {

  def startDate: LocalDateTime
  def endDate: LocalDateTime
  def timeZoneId: String
  def isVotingEnabled: Boolean
  def isStaffVotingOnly: Boolean
  def shouldShowVoteCount: Boolean
  def isSourceRequired: Boolean
  def defaultVotes: Int
  def staffVotes: Int
  def allowedEntries: Int
  def maxEntryTotal: Int
  def timeZone: ZoneId = ZoneId.of(this.timeZoneId)

  def checkDates(checkStart: Boolean): Boolean =
    (startDate.isAfter(LocalDateTime.now(this.timeZone)) || !checkStart) && startDate.isBefore(this.endDate)

}
