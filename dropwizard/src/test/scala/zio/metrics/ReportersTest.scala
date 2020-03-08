package zio.metrics

import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import zio.metrics.dropwizard.reporters._
import zio.{ App, RIO, Runtime }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import zio.console._
import zio.duration.Duration
import com.codahale.metrics.MetricRegistry

object ReportersTest extends App {

  val rt = Runtime.unsafeFromLayer(Registry.live ++ Reporters.live)

  val tests: RIO[
    Registry with Reporters,
    MetricRegistry
  ] =
    for {
      r   <- getCurrentRegistry()
      _   <- jmx(r)
      _   <- console(r, 2, TimeUnit.SECONDS)
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

  override def run(args: List[String]) = {
    println("Starting tests")
    val json = rt.unsafeRun(tests >>= (r => DropwizardExtractor.writeJson(r)(None)))
    RIO.sleep(Duration.fromScala(30.seconds))
    putStrLn(json.spaces2).map(_ => 0)
  }
}
