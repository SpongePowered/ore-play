logLevel := Level.Warn
evictionWarningOptions in update := EvictionWarningOptions.default.withWarnTransitiveEvictions(false).withWarnDirectEvictions(false).withWarnScalaVersionEviction(false)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.12")
addSbtPlugin("org.irundaia.sbt" % "sbt-sassify" % "1.4.12")

// Used for debugging only
//addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")
//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
