name := "cats-circuitbreaker"

version := "0.0.1"

scalaVersion := "2.13.1"

lazy val scalatestVersion = "3.1.1"
lazy val scalaLoggingVersion = "3.9.2"
lazy val logbackClassicVersion = "1.2.3"
lazy val catsCoreVersion = "2.1.1"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8", // source files are in UTF-8
  "-deprecation", // warn about use of deprecated APIs
  "-unchecked", // warn about unchecked type parameters
  "-feature", // warn about misused language features
  "-language:higherKinds", // allow higher kinded types without `import scala.language.higherKinds`
  "-Xlint", // enable handy linter warnings
  "-Xfatal-warnings", // turn compiler warnings into errors
  //"-Ypartial-unification", // allow the compiler to unify type constructors of different arities
  "-target:jvm-1.8"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsCoreVersion,
  "org.typelevel" %% "cats-effect" % catsCoreVersion,
  "com.github.valskalla" %% "odin-core" % "0.7.0",
  "org.scalatest" %% "scalatest" % scalatestVersion % "test"
)
