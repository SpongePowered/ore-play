package models.querymodels

import java.sql.Date

case class Stats(
    reviews: Long,
    uploads: Long,
    totalDownloads: Long,
    unsafeDownloads: Long,
    flagsOpened: Long,
    flagsClosed: Long,
    day: Date
)
