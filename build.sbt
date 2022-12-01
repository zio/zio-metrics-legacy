import Build._

inThisBuild(
  List(
    ThisBuild / scalaVersion := Scala213,
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-metrics/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer("toxicafunk", "Eric Noam", "toxicafunk@gmail.com", url("https://github.com/toxicafunk"))
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio-metrics/"), "scm:git:git@github.com:zio/zio-metrics.git")
    )
  )
)

Test / fork := true
run / fork := true

val http4sVersion     = "0.23.11"
val zioVersion        = "2.0.4"
val interopVersion    = "3.3.0"
val catsEffectVersion = "3.3.11"
//val zioNIOVersion     = "1.0.0-RC11"
val prometheusVersion = "0.12.0"
val dropwizardVersion = "4.2.0"
val circeVersion      = "0.14.1"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = (project in file("."))
  .aggregate(common, dropwizard, prometheus, statsd)
  .settings(settings)

lazy val common = project
  .settings(
    name := "common",
    stdSettings("metrics"),
    libraryDependencies ++= commonDependencies
  )

lazy val dropwizard = project
  .settings(
    name := "dropwizard",
    stdSettings("metrics-dropwizard") ++ settings,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= commonDependencies ++ dropwizardDependencies ++ (CrossVersion
      .partialVersion(scalaBinaryVersion.value) match {
      case Some((2, 11)) => Seq()
      case _             => http4s
    })
  )
  .dependsOn(common)

lazy val prometheus = project
  .settings(
    name := "prometheus",
    stdSettings("metrics-prometheus") ++ settings,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= commonDependencies ++ prometheusDependencies
  )
  .dependsOn(common)

lazy val statsd = project
  .settings(
    name := "statsd",
    crossScalaVersions -= "2.11.12",
    stdSettings("metrics-statsd") ++ settings,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= commonDependencies ++ (CrossVersion.partialVersion(scalaBinaryVersion.value) match {
      case Some((2, 11)) => Seq()
      case _             => statsdDependencies
    })
  )
  .dependsOn(common)

lazy val commonDependencies = Seq(
  "dev.zio" %% "zio"          % zioVersion,
  "dev.zio" %% "zio-streams"  % zioVersion,
  "dev.zio" %% "zio-test"     % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test
)

lazy val prometheusDependencies = Seq(
  "io.prometheus" % "simpleclient"                 % prometheusVersion,
  "io.prometheus" % "simpleclient_hotspot"         % prometheusVersion,
  "io.prometheus" % "simpleclient_common"          % prometheusVersion,
  "io.prometheus" % "simpleclient_httpserver"      % prometheusVersion,
  "io.prometheus" % "simpleclient_pushgateway"     % prometheusVersion,
  "io.prometheus" % "simpleclient_graphite_bridge" % prometheusVersion
)

lazy val dropwizardDependencies = Seq(
  "io.dropwizard.metrics" % "metrics-core"         % dropwizardVersion,
  "io.dropwizard.metrics" % "metrics-healthchecks" % dropwizardVersion,
  "io.dropwizard.metrics" % "metrics-jmx"          % dropwizardVersion,
  "io.dropwizard.metrics" % "metrics-graphite"     % dropwizardVersion,
  "io.circe"              %% "circe-core"          % circeVersion,
  "dev.zio"               %% "zio-interop-cats"    % interopVersion
)

lazy val statsdDependencies = Seq(
  //"dev.zio" %% "zio-nio" % zioNIOVersion
)

lazy val docs = project
  .in(file("zio-metrics-docs"))
  .settings(
    publish / skip := true,
    // skip 2.13 mdoc until mdoc is available for 2.13
    crossScalaVersions -= Scala213,
    crossScalaVersions -= Scala3,
    moduleName := "zio-metrics-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= commonDependencies ++ statsdDependencies ++ dropwizardDependencies ++ prometheusDependencies
  )
  .dependsOn(common, prometheus, dropwizard, statsd)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

lazy val settings = Seq(
  scalacOptions ++=
    (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) =>
        Seq() // Seq("-release:8") has issue running with JDK8 https://github.com/lampepfl/dotty/issues/13810
      case _ =>
        (CrossVersion.partialVersion(scalaBinaryVersion.value) match {
          case Some((2, 11)) => Seq("-Ypartial-unification", "-Ywarn-value-discard", "-target:jvm-1.8")
          case Some((2, 13)) => Seq("-Ywarn-value-discard", "-target:jvm-1.8")
          case _             => Seq("-Ypartial-unification", "-Ywarn-value-discard")
        })
    })
)

lazy val http4s = Seq(
  "org.http4s"    %% "http4s-circe"        % http4sVersion,
  "org.http4s"    %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"    %% "http4s-dsl"          % http4sVersion,
  "org.typelevel" %% "cats-effect"         % catsEffectVersion //% Optional,
)

// TODO: enforce scalazzi dialect through the scalaz-plugin
// addCompilerPlugin("org.scalaz" % "scalaz-plugin_2.12.4" % "0.0.7")
