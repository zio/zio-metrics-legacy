package zio.metrics

import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import zio.{ App, RIO, Runtime }
import zio.internal.PlatformLive
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import zio.console._
import zio.duration.Duration

object ReportersTest extends App {

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
      _   <- reporters.console(r, 2, TimeUnit.SECONDS)
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

  override def run(args: List[String]) = {
    println("Starting tests")
    val json = rt.unsafeRun(tests >>= (dwr => DropwizardExtractor.writeJson(dwr)(None)))
    RIO.sleep(Duration.fromScala(30.seconds))
    putStrLn(json.spaces2).map(_ => 0)
  }
}
