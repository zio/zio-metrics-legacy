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
import zio.console.Console
import zio.random.Random
import zio.blocking.Blocking
import zio.interop.catz._
import org.http4s.implicits._
import org.http4s.server.Router

object ReportersTest extends App {

  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)
  println(s"Starting server on port $port")

  val rt = Runtime(
    new DropwizardRegistry with DropwizardReporters,
    PlatformLive.Default
  )

  val tests: RIO[
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
      l <- RIO.foreach(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(ctx))
    } yield { println(l); dwr }

  val httpApp =
    (registry: DropwizardRegistry) =>
      Router(
        "/metrics" -> service.serveMetrics(registry)
      ).orNotFound

  override def run(args: List[String]) = {
    println("Starting tests")
    val r = rt.unsafeRun(tests)
    builder(httpApp(r))
      .provideSome[HttpEnvironment] { rt =>
        new Clock with Console with System with Random with Blocking {
          override val clock: Clock.Service[Any] = rt.clock
          //override val scheduler: Scheduler.Service[Any] = rt.scheduler
          override val console: Console.Service[Any]   = rt.console
          override val random: Random.Service[Any]     = rt.random
          override val system: System.Service[Any]     = rt.system
          override val blocking: Blocking.Service[Any] = rt.blocking
        }
      }
      .run
      .map(_ => 0)
  }
}
