# Ore
[![Build Status](https://travis-ci.com/SpongePowered/Ore.svg?branch=master)](https://travis-ci.com/SpongePowered/Ore)
[![Hydra](https://img.shields.io/badge/%22%22%22%7CHydra-4%20cpus-brightgreen.svg)](https://www.triplequote.com/hydra)

Repository software for Sponge plugins and Forge mods https://ore.spongepowered.org/
 
Ore is written in Scala using the [Play](https://www.playframework.com/) framework.

### Clone
The following steps will ensure your project is cloned properly.

1. `git clone https://github.com/SpongePowered/Ore.git`   
2. `cp scripts/pre-commit .git/hooks`

### Setup

After cloning Ore, the first thing you will want to do is create a new PostgreSQL 11 database for the application to use.
This is required in order for Ore to run. Learn more about PostgreSQL [here](https://www.postgresql.org/).

You will also need to enable a few extensions for Ore. These are:
* [pgcrypto](https://www.postgresql.org/docs/11/pgcrypto.html)

After setting up a database, create a copy of `conf/application.conf.template` named `conf/application.conf` and 
configure the application. This file is in the `.gitignore` so it will not appear in your commits. In a typical 
development environment, most of the defaults will do except you must set `application.fakeUser` to `true` to disable
authentication to the Sponge forums. In addition, the SSL certification authority of `https://forums.spongepowered.org` is
not typically recognized by the JVM so you will either have to manually add the cert to your JVM or set 
`discourse.api.enabled` to `false` in the configuration file.

## Running

Running Ore is relatively simple.

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

### Using Hydra

Hydra is the world’s only parallel compiler for the Scala language.
Its design goal is to take advantage of the many cores available in modern hardware to parallelize compilation of Scala sources.
This gives us the possibility to achieve a much faster compile time.
[Triplequote](https://triplequote.com/) has kindly provided us with some licenses.
If you have a license and want to use Hydra, follow these steps:

1. Create the file `project/hydra.sbt`
2. Put in this content into the newly created file:
   ```
   credentials += Credentials("Artifactory Realm",
       "repo.triplequote.com",
       "<username>",
       "<password>")
   resolvers += Resolver.url("Triplequote Plugins Releases", url("https://repo.triplequote.com/artifactory/sbt-plugins-release/"))(Resolver.ivyStylePatterns)
   addSbtPlugin("com.triplequote" % "sbt-hydra" % "<version>")
   ```
   - The `<username>` and `<password>` placeholders have to be replaced with your credentials.
   - The `<version>` placeholder has to be replaced with the lastest version of `sbt-hydra` which can be obtained from the [offical changelog](https://docs.triplequote.com/changelog/).

3. Open the sbt console and make use of the following command where `<license key>` is your personal hydra license key:

   ```
   hydraActivateLicense <license key>
   ```

4. Go and start compiling!

Further instructions can be found at the [official Hydra documentation](https://docs.triplequote.com/).
