package models.querymodels

import java.time.LocalDate

case class Stats(
    reviews: Long,
    uploads: Long,
    totalDownloads: Long,
    unsafeDownloads: Long,
    flagsOpened: Long,
    flagsClosed: Long,
    day: LocalDate
)
