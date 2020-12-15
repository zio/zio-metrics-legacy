package zio.metrics

import zio.console.putStrLn
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.Server._
import zio.metrics.dropwizard.helpers._
import zio.metrics.dropwizard.reporters._
import zio.{ App, RIO, Task }
import java.util.concurrent.TimeUnit
import scala.util.Properties.envOrNone
import zio.interop.catz._
import org.http4s.implicits._
import org.http4s.server.Router
import com.codahale.metrics.MetricRegistry
import zio.ExitCode

object ServerTest extends App {

  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)
  println(s"Starting server on port $port")

  val testServer: RIO[
    Registry with Reporters,
    MetricRegistry
  ] =
    for {
      r   <- getCurrentRegistry()
      _   <- jmx(r)
      _   <- helpers.console(r, 30, TimeUnit.SECONDS)
      c   <- counter.register(Show.fixClassName(DropwizardTest.getClass()), Array("test", "counter"))
      _   <- c.inc()
      _   <- c.inc(2.0)
      t   <- timer.register("DropwizardTimer", Array("test", "timer"))
      ctx <- t.start()
      _ <- RIO.foreach(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(ctx))
    } yield r

  val httpApp = (registry: MetricRegistry) =>
    Router(
      "/metrics" -> Server.serveMetrics(registry)
    ).orNotFound

  override def run(args: List[String]) = {
    println("Starting tests")

    val kApp: Task[KleisliApp] = testServer
      .map(r => httpApp(r))
      .provideLayer(Registry.live ++ Reporters.live)

    val app: RIO[HttpEnvironment, Unit] = kApp >>= builder
    println(s"App: $app")

    app
      .catchAll(t => putStrLn(s"$t"))
      .run
      .map(r => { println(s"Exiting $r"); ExitCode.success })
  }
}
