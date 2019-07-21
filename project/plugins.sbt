logLevel := Level.Warn
evictionWarningOptions in update := EvictionWarningOptions.default
  .withWarnTransitiveEvictions(false)
  .withWarnDirectEvictions(false)
  .withWarnScalaVersionEviction(false)

resolvers += "Typesafe repository".at("http://repo.typesafe.com/typesafe/releases/")

addSbtPlugin("com.typesafe.play"  % "sbt-plugin"              % "2.7.3")
addSbtPlugin("org.irundaia.sbt"   % "sbt-sassify"             % "1.4.13")
addSbtPlugin("com.typesafe.sbt"   % "sbt-digest"              % "1.1.4")
addSbtPlugin("com.typesafe.sbt"   % "sbt-gzip"                % "1.0.2")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"            % "0.9.0")
addSbtPlugin("com.iheart"         %% "sbt-play-swagger"       % "0.7.6-PLAY2.7")
addSbtPlugin("com.github.gpgekko" % "sbt-autoprefixer"        % "1.2.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"             % "0.6.28")
addSbtPlugin("ch.epfl.scala"      % "sbt-web-scalajs-bundler" % "0.15.0-0.6")
