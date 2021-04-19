package zio.metrics

import org.http4s.server.Router
import zio.clock.Clock
import zio.metrics.dropwizard.Server.builder
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import zio.metrics.dropwizard.reporters._
import zio.{ Managed, Task, ZIO }
import zio.duration._

import scala.util.Properties.envOrNone
import zio.interop.catz._
import org.http4s.implicits._
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._

import scala.io.Source

object ServerTest extends DefaultRunnableSpec {
  private val port: Int      = envOrNone("HTTP_PORT").fold(9090)(_.toInt)
  private val testMetricName = "ServerTest"

  override def spec =
    suite("ServerTest")(
      testM("metrics from registry available at /metrics/ALL") {
        for {
          _    <- testServer.flatMap(t => builder(t)).run.fork
          _    <- ZIO.sleep(5.seconds)
          body <- getURLContent(s"http://localhost:${port}/metrics/ALL")
        } yield {
          assert(body)(containsString("\"counters\":{\"ServerTest.test.counter\":3}")) &&
          assert(body)(containsString("\"timers\":"))
        }
      }
    ).provideCustomLayer(Registry.live ++ Reporters.live ++ Clock.live)

  private def getURLContent(url: String): Task[String] = {
    val managed = Managed.make(ZIO(Source.fromURL(url)))(s => ZIO(s.close()).orDie)

    managed.use(s => ZIO(s.mkString))
  }

  private val testServer =
    for {
      r       <- getCurrentRegistry()
      _       <- jmx(r)
      c       <- counter.register(testMetricName, Array("test", "counter"))
      _       <- c.inc() *> c.inc(2.0)
      t       <- timer.register(testMetricName, Array("test", "timer"))
      ctx     <- t.start()
      _       <- ZIO.foreach_(List(1000, 1400, 1200L))(n => t.stop(ctx).delay(n.millis))
      httpApp <- ZIO(Router("/metrics" -> Server.serveMetrics(r)).orNotFound)
    } yield httpApp

}
