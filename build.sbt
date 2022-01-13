Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "swoogles",
        "Bill Frasure",
        "bill@billdingsoftware.com",
        url("https://www.billdingsoftware.com")
      )
    )
  )
)

addCommandAlias("build", "; fmt")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias(
  "check",
  "; scalafmtSbtCheck; scalafmtCheckAll; Test/compile; scalafixTests/test"
)


lazy val scalafixSettings = List(
  scalaVersion := "2.13.7",
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions ++= List(
    "-Yrangepos",
    "-P:semanticdb:synthetics:on"
  )
)

lazy val scalafixRules = project
  .in(file("scalafix/rules")) // TODO .in needed when name matches?
  .settings(
    scalafixSettings,
    semanticdbEnabled                      := true, // enable SemanticDB
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % "0.9.32"
  )

val zio1Version = "1.0.12"
val zio2Version = "2.0.0-RC1"

lazy val scalafixInput = project
  .in(file("scalafix/input"))
  .settings(
    scalafixSettings,
    libraryDependencies += "dev.zio" %% "zio"         % zio1Version,
    libraryDependencies += "dev.zio" %% "zio-streams" % zio1Version,
    libraryDependencies += "dev.zio" %% "zio-test"    % zio1Version
  )

lazy val scalafixOutput = project
  .in(file("scalafix/output"))
  .settings(
    scalafixSettings,
    libraryDependencies += "dev.zio" %% "zio"         % zio2Version,
    libraryDependencies += "dev.zio" %% "zio-streams" % zio2Version,
    libraryDependencies += "dev.zio" %% "zio-test"    % zio2Version
  )

lazy val scalafixTests = project
  .in(file("scalafix/tests"))
  .settings(
    scalafixSettings,
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % "0.9.32" % Test cross CrossVersion.full,
    Compile / compile :=
      (Compile / compile).dependsOn(scalafixInput / Compile / compile).value,
    scalafixTestkitOutputSourceDirectories :=
      (scalafixOutput / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (scalafixInput / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (scalafixInput / Compile / fullClasspath).value
  )
  .dependsOn(scalafixRules)
  .enablePlugins(ScalafixTestkitPlugin)

