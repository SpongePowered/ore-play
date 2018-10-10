# Contribution Guidelines
We will always have a need for developers to help us improve Ore. There is no such thing as a perfect project and things can always be improved. If you are a developer and are interested in helping then please do not hesitate. Just make sure you follow our guidelines.

Please check our [Contribution Guidelines in the Sponge Documentation](https://docs.spongepowered.org/en/contributing/guidelines.html) for general guidelines for contributing to Ore.

## Style Guide
Also make sure you are familiar with the [Scala style guide](https://docs.scala-lang.org/style/). TL;DR For the most part stuff is similar to java. Indent with 2 spaces. Don't use getters and setters, and instead don't mark the value as private or protected. Constants have PascalCase instead of UPPER_CASE. Non side effecting methods should not be defined with parenthesis, side effecting methods should be defined with them. Type arguments start at A. Prefer val instead of var.

Some places under you may find some paragraphs that begins with **(FUTURE)**. This means that while this is not currently being enforced, but will be enforced once some other piece falls in place, for example some code that's needed is implemented.

## Scalafmt
Scalafmt determines the final formatting. Make sure to run it before creating a pull request. The recommended way to run scalafmt is through the CLI. Scalafmt can be invoked by running `scalafmt` in the root directory. To format the files, run `scalafmt -i`. IntelliJ can also format using scalafmt from version 2018.2 onwards.

## Linters
Ore uses the linter built into scalac. 

**(FUTURE)** Ore also use scalafix to lint the codebase for Ore. 

**(FUTURE)** The warnings from scalafix are always emitted when compiling code. To run Scalafix, run the `scalafixCli` task in SBT. If you need to suppress a scalafix rule, use the `@SuppressWarnings` annotation with the values specified like `@SuppressWarnings("scalafix:<rule here>"")`. Alternatively you can also use the comments `//scalafix:off` and `//scalafix:on` for for a region, and `//scalafix:ok` for a single statement.

## Rules not covered by linters
Note that Ore also has other rules which are not covered by either linter yet listed bellow.
* All public members should have an explicit type.
* All implicit values should have an explicit type.
* When using Kind projector, `λ` should be used over `Lambda`. IntelliJ has an inspection + quick fix that can do this job for you automatically when you use kind projector.
* All models that are stored in the database should also have a doobie test which tests statements to create, select, update, and delete the model.

## Import formatting
Imports should be formatted like so.
```
scala.language
BLANK_LINE
java
javax
BLANK_LINE
scala
BLANK_LINE
play
BLANK_LINE
controllers
db
discourse
filters
form
mail
models
ore
security
util
views
BLANK_LINE
<other stuff>
BLANK_LINE
```

## Common methods you should use to simplify your code
* Prefer fold on Option instead of map followed by getOrElse, if both bodies are short.
* Prefer sortBy instead of passing in an ordering if possible.
* Prefer slice instead of take and drop together
* Prefer withFilter if more monadic operations follow.
* Prefer foreach instead of map if the result type is Unit.
* isDefinedAt for Seq instead of checking length.
