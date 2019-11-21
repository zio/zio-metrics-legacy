import Build._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-metrics/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer("toxicafunk", "Eric Noam", "toxicafunk@gmail.com", url("https://github.com/toxicafunk"))
    ),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher,
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio-metrics/"), "scm:git:git@github.com:zio/zio-metrics.git")
    )
  )
)

val http4sVersion     = "0.21.0-M5"
val zioVersion        = "1.0.0-RC16"
val interopVersion    = "2.0.0.0-RC7"
val prometheusVersion = "0.7.0"
val dropwizardVersion = "4.0.1"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  (project in file("."))
    .aggregate(common, dropwizard, prometheus)
    .settings(settings)
//.disablePlugins(AssemblyPlugin)

lazy val common = project
  .settings(
    name := "common",
    //settings,
    stdSettings("metrics"),
    libraryDependencies ++= commonDependencies
  )
//.disablePlugins(AssemblyPlugin)

lazy val dropwizard = project
  .settings(
    name := "dropwizard",
    stdSettings("metrics-dropwizard") ++ settings,
    libraryDependencies ++= commonDependencies ++ http4s ++ Seq(
      "io.dropwizard.metrics" % "metrics-core"         % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-healthchecks" % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-jmx"          % dropwizardVersion,
      "io.dropwizard.metrics" % "metrics-graphite"     % dropwizardVersion
    )
  )
  .dependsOn(common)

lazy val prometheus = project
  .settings(
    name := "prometheus",
    stdSettings("metrics-prometheus") ++ settings,
    libraryDependencies ++= commonDependencies ++ Seq(
      "io.prometheus" % "simpleclient"                 % prometheusVersion,
      "io.prometheus" % "simpleclient_hotspot"         % prometheusVersion,
      "io.prometheus" % "simpleclient_common"          % prometheusVersion,
      "io.prometheus" % "simpleclient_httpserver"      % prometheusVersion,
      "io.prometheus" % "simpleclient_pushgateway"     % prometheusVersion,
      "io.prometheus" % "simpleclient_graphite_bridge" % prometheusVersion
    )
  )
  .dependsOn(common)

lazy val commonDependencies = Seq(
  "dev.zio"    %% "zio"              % zioVersion,
  "dev.zio"    %% "zio-interop-cats" % interopVersion,
  "org.scalaz" % "testz-core_2.12"   % "0.0.5",
  "org.scalaz" % "testz-stdlib_2.12" % "0.0.5"
)

lazy val settings = Seq(
  scalacOptions ++= (CrossVersion.partialVersion(scalaBinaryVersion.value) match {
    case Some((2, 11)) => Seq("-Ypartial-unification", "-Ywarn-value-discard", "-target:jvm-1.8")
    case _             => Seq("-Ypartial-unification", "-Ywarn-value-discard")
  })
)

lazy val http4s = Seq(
  //"org.http4s" %% "http4s-blaze-client" % http4sVersion,
  //"org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s"    %% "http4s-argonaut"     % http4sVersion,
  "org.http4s"    %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"    %% "http4s-dsl"          % http4sVersion,
  "org.typelevel" %% "cats-effect"         % "2.0.0", // % Optional,
  "io.argonaut"   %% "argonaut"            % "6.2.2",
  "io.argonaut"   %% "argonaut-cats"       % "6.2.2"
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")

// TODO: enforce scalazzi dialect through the scalaz-plugin
// addCompilerPlugin("org.scalaz" % "scalaz-plugin_2.12.4" % "0.0.7")
