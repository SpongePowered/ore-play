Ore
===

Repository software for Sponge plugins and Forge mods https://ore.spongepowered.org/
 
Ore is written in Scala using the [Play](https://www.playframework.com/) framework.

## Running

Running Ore is relatively simple.

**With Activator**
* Download and install the latest [Activator](https://www.lightbend.com/activator/download) distribution.
* Execute `activator run` in the project root.

**With SBT**
* Download and install the latest [SBT](http://www.scala-sbt.org/download.html) version.
* Execute `sbt run` in the project root.

**With IntelliJ Community Edition**
* Install the Scala plugin.
* Import the `build.sbt` file.
* Create a new SBT Task run configuration. Enter `run` in the Tasks field.
* Run it.

**With IntelliJ Ultimate Edition:**
* Install the Scala plugin.
* Import the `build.sbt` file.
* Create a new Play 2 App run configuration.
* Run it.

## Contributing

Ore's code-style is not unlike the rest of the Sponge projects code-style with some leniency for readability.
Namely, the differences include:
* Non-uniform whitespace (see [schema.sql](app/models/db/schema.scala))
* Inline conditionals / statements
* Public fields permitted on database properties in models

### Setup

After cloning Ore, the first thing you will want to do is create a new PostgreSQL database for the application to use.
This is required in order for Ore to run. Learn more about PostgreSQL [here](https://www.postgresql.org/).

After setting up a database, create a copy of `conf/application.conf.template` named `conf/application.conf` and 
configure the application. This file is in the `.gitignore` so it will not appear in your commits. In a typical 
development environment, most of the defaults will do except you must set `application.fakeUser` to `true` to disable
authentication to the Sponge forums. In addition, the SSL certification authority of https://forums.spongepowered.org is
not typically recognized by the JVM so you will either have to manually add the cert to your JVM or set 
`discourse.api.enabled` to `false` in the configuration file.
