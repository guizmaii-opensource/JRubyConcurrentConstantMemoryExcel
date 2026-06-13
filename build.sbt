import BuildHelper.*

ThisBuild / organization := "com.guizmaii"
ThisBuild / scalaVersion := "3.3.8"

ThisBuild / scalafmtCheck     := true
ThisBuild / scalafmtSbtCheck  := true
ThisBuild / scalafmtOnCompile := !insideCI.value
ThisBuild / semanticdbEnabled := true

ThisBuild / licenses   := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / homepage   := Some(url("https://github.com/guizmaii-opensource/JRubyConcurrentConstantMemoryExcel"))
ThisBuild / developers := List(
  Developer(
    "guizmaii",
    "Jules Ivanic",
    "jules.ivanic@gmail.com",
    url("https://https://ivanic.hashnode.dev/")
  )
)

Global / onChangedBuildSource := ReloadOnSourceChanges

// ### Aliases ###

addCommandAlias("fmt", "scalafmt;scalafixAll")
addCommandAlias("tc", "Test/compile")
addCommandAlias("ctc", "clean; tc")
addCommandAlias("rctc", "reload; ctc")

// ### Dependencies ###

val zioVersion = "2.1.26"

lazy val testKitLibs = Seq(
  "dev.zio" %% "zio-test"     % zioVersion,
  "dev.zio" %% "zio-test-sbt" % zioVersion,
).map(_ % Test)

lazy val poi =
  (
    (version: String) =>
      Seq(
        "org.apache.poi" % "poi"       % version,
        "org.apache.poi" % "poi-ooxml" % version
      )
  )("4.1.0")

// ### Modules ###

lazy val root =
  Project(id = "JRubyConcurrentConstantMemoryExcel", base = file("."))
    .settings(noDoc *)
    .settings(publish / skip := true)
    .settings(crossScalaVersions := Nil) // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project+statefully,
    .aggregate(core)

lazy val core =
  project
    .settings(name := "JRubyConcurrentConstantMemoryExcel")
    .settings(stdSettings *)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio"                %% "zio"          % zioVersion,
        "io.github.kantan-scala" %% "kantan.csv"   % "0.12.0",
        "com.github.pathikrit"   %% "better-files" % "3.9.2",
      ) ++ poi ++ testKitLibs,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    )
