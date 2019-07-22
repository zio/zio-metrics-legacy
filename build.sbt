import Build._

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

pgpPublicRing := file("/tmp/public.asc")
pgpSecretRing := file("/tmp/secret.asc")
releaseEarlyWith := SonatypePublisher
scmInfo := Some(
  ScmInfo(url("https://github.com/zio/zio-metrics/"), "scm:git:git@github.com:zio/zio-metrics.git")
)

organization in ThisBuild := "dev.zio"

version in ThisBuild := "0.1.0-SNAPSHOT"

val http4sVersion  = "0.20.0-M5"
//val zioVersion     = "1.0.0-RC10-1"
val zioVersion     = "1.0.0-RC9"
val interopVersion = "1.0.0-RC8-10"

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots".at(nexus + "content/repositories/snapshots"))
  else
    Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
}

dynverSonatypeSnapshots in ThisBuild := true

lazy val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

credentials in ThisBuild ++= sonataCredentials.toSeq

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  (project in file("."))
    .settings(
      stdSettings("metrics")
    )

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core"                 % "7.2.27",
  "dev.zio" %% "zio"                  % zioVersion,
  "dev.zio" %% "zio-interop-cats"     % interopVersion,
  "dev.zio" %% "zio-interop-scalaz7x" % interopVersion,
  "org.scalaz" %% "testz-core"                  % "0.0.5",
  "org.scalaz" %% "testz-stdlib"                % "0.0.5"
)

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core"         % "4.0.1",
  "io.dropwizard.metrics" % "metrics-healthchecks" % "4.0.1",
  "io.dropwizard.metrics" % "metrics-jmx"          % "4.0.1"
)

libraryDependencies += "io.prometheus" % "simpleclient" % "0.6.0"

libraryDependencies ++= Seq(
  //"org.http4s" %% "http4s-blaze-client" % http4sVersion,
  //"org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-argonaut"     % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl"          % http4sVersion
)

libraryDependencies ++= Seq(
  "io.argonaut" %% "argonaut"        % "6.2.2",
  "io.argonaut" %% "argonaut-scalaz" % "6.2.2"
)

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += Resolver.sonatypeRepo("releases")

scalacOptions ++= Seq("-Ypartial-unification", "-Ywarn-value-discard")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")

// TODO: enforce scalazzi dialect through the scalaz-plugin
// addCompilerPlugin("org.scalaz" % "scalaz-plugin_2.12.4" % "0.0.7")
