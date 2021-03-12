ThisBuild / scalaVersion := "3.0.0-RC1"
ThisBuild / organization := "com.propensive"
ThisBuild / organizationName := "Propensive OÜ"
ThisBuild / organizationHomepage := Some(url("https://propensive.com/"))
ThisBuild / version := "0.4.0"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/propensive/escritoire"),
    "scm:git@github.com:propensive/escritoire.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "propensive",
    name  = "Jon Pretty",
    email = "jon.pretty@propensive.com",
    url   = url("https://twitter.com/propensive")
  )
)

ThisBuild / description := "A library for writing tables"
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/propensive/escritoire"))

ThisBuild / pomIncludeRepository := { _ => false }

ThisBuild / publishMavenStyle := true

lazy val core = (project in file(".core"))
  .settings(
    name := "escritoire-core",
    crossScalaVersions := Seq("3.0.0-RC1", "2.13.5", "2.12.13"),
    Compile / scalaSource := baseDirectory.value / ".." / "src" / "core",
  )