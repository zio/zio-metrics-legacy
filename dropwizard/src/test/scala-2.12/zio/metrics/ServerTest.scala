package zio.metrics

import zio.metrics.Server._
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import zio.metrics.dropwizard.DropwizardMetricsService.service
import zio.{ App, RIO, Runtime }
import zio.internal.PlatformLive
import java.util.concurrent.TimeUnit
import scala.util.Properties.envOrNone
import zio.system.System
import zio.clock.Clock
import zio.console._
import zio.random.Random
import zio.blocking.Blocking
import zio.interop.catz._
import org.http4s.implicits._
import org.http4s.server.Router

object ServerTest extends App {

  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)
  println(s"Starting server on port $port")

  val rt = Runtime(
    new DropwizardRegistry with DropwizardReporters,
    PlatformLive.Default
  )

  val testServer: RIO[
    DropwizardRegistry with DropwizardReporters,
    DropwizardRegistry
  ] =
    for {
      dwr <- RIO.environment[DropwizardRegistry]
      r   <- dwr.registry.getCurrent()
      _   <- reporters.jmx(r)
      _   <- reporters.console(r, 30, TimeUnit.SECONDS)
      c   <- counter.register(Show.fixClassName(DropwizardTests.getClass()), Array("test", "counter"))
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
    } yield dwr

  val httpApp =
    (registry: DropwizardRegistry) =>
      Router(
        "/metrics" -> service.serveMetrics(registry)
      ).orNotFound

  override def run(args: List[String]) = {
    println("Starting tests")

    val kApp: KleisliApp = rt.unsafeRun(testServer.map(r => httpApp(r)))

    val app: RIO[HttpEnvironment, Unit] = builder(kApp)
    println(s"App: $app")

    app
      .catchAll(t => putStrLn(s"$t"))
      .provideSome[HttpEnvironment] { rt =>
        new Clock with Console with System with Random with Blocking {
          override val clock: Clock.Service[Any]       = rt.clock
          override val console: Console.Service[Any]   = rt.console
          override val system: System.Service[Any]     = rt.system
          override val random: Random.Service[Any]     = rt.random
          override val blocking: Blocking.Service[Any] = rt.blocking
        }
      }
      .run
      .map(r => { println(s"Exiting $r"); 0})
  }
}
