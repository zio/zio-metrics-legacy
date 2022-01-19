import sbt._
import sbt.Keys._

object Build {
  def stdSettings(prjName: String) = Seq(
    name := s"zio-$prjName",
    crossScalaVersions := Seq(Scala212, Scala213, Scala3),
    ThisBuild / scalaVersion := Scala213,
    scalacOptions := stdOptions(scalaVersion.value) ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq()
      case _            => Seq(compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"))
    }),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )

  val Scala212 = "2.12.15"
  val Scala213 = "2.13.8"
  val Scala3   = "3.1.0"

  private def stdOptions(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _)) =>
      Seq(
        "-encoding",
        "utf8",
        "-feature",
        "-deprecation",
        "-unchecked",
        "-language:experimental.macros",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xfatal-warnings"
      )
    case _ =>
      Seq(
        "-encoding",
        "UTF-8",
        "-explaintypes",
        "-Yrangepos",
        "-feature",
        "-language:higherKinds",
        "-language:existentials",
        "-Xlint:_,-type-parameter-shadow",
        "-Xsource:2.13",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ywarn-value-discard",
        "-unchecked",
        "-deprecation",
        "-Xfatal-warnings"
      )
  }

  private val stdOpts213 = Seq(
    "-Wunused:imports",
    "-Wvalue-discard",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:params",
    "-Wvalue-discard",
    "-Wdead-code",
    "-Xsource:3",
    "-Wconf:cat=unused-nowarn:s"
  )

  private val stdOptsUpto212 = Seq(
    "-Xfuture",
    "-Ypartial-unification",
    "-Ywarn-nullary-override",
    "-Yno-adapted-args",
    "-Ywarn-infer-any",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused-import"
  )

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        Seq()
      case Some((2, 13)) =>
        stdOpts213
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        ) ++ stdOptsUpto212
      case _ =>
        Seq("-Xexperimental") ++ stdOptsUpto212
    }
}
