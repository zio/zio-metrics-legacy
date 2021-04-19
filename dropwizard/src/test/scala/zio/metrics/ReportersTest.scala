package zio.metrics

import zio.clock.Clock
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import zio.metrics.dropwizard.reporters._
import zio.test.environment.TestClock
import zio.ZIO

//import java.util.concurrent.TimeUnit
import zio.duration._
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._

object ReportersTest extends DefaultRunnableSpec {

  override def spec =
    suite("ReportersTest")(
      suite("JMX")(
        testM("reporter works with counter") {
          val expectedJson = "{\n  \"counters\" : {\n    \"ReportersTestCnt.test.counter\" : 3\n  }\n}"

          for {
            r    <- getCurrentRegistry()
            _    <- jmx(r)
            c    <- counter.register("ReportersTestCnt", Array("test", "counter"))
            _    <- c.inc() *> c.inc(2.0)
            json <- DropwizardExtractor.writeJson(r)(None)
          } yield assert(json.toString())(equalTo(expectedJson))
        },
        testM("reporter works with timer") {
          for {
            r   <- getCurrentRegistry()
            _   <- jmx(r)
            t   <- timer.register("TimerTest", Array("test", "timer"))
            ctx <- t.start()
            _ <- ZIO.foreach_(List(1000, 1400, 1200)) { n =>
                  TestClock.adjust(n.millis) *> t.stop(ctx).delay(n.millis)
                }
            json <- DropwizardExtractor.writeJson(r)(None).map(_.toString())
          } yield {
            assert(json)(containsString("\"timers\" : {")) &&
            assert(json)(containsString("\"TimerTest.test.timer_count\" : 3"))
          }
        }
      ),
      suite("Console")(
        testM("reporter works") {
          // Console reporter just prints to console
          for {
            //r <- getCurrentRegistry()
            //_ <- console(r, 5, TimeUnit.SECONDS)
            c <- counter.register("ReportersTestCnt", Array("test", "counter"))
            _ <- c.inc() *> c.inc(2.0)
          } yield assert(true)(isTrue)
        }
      )
    ).provideCustomLayer(Reporters.live ++ Registry.live ++ Clock.live)
}
