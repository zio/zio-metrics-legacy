package zio.metrics

import zio.{ RIO, Runtime, Task }
import zio.console._
import zio.internal.PlatformLive
import testz.{ assert, Harness, PureHarness }
import com.codahale.metrics.{ MetricRegistry }
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import com.codahale.metrics.UniformReservoir
import com.codahale.metrics.ExponentiallyDecayingReservoir
import com.codahale.metrics.SlidingTimeWindowArrayReservoir
import java.util.concurrent.TimeUnit

object DropwizardTest {

  val rt = Runtime(
    new DropwizardRegistry with Console.Live,
    PlatformLive.Default
  )

  val tester: () => Long = () => System.nanoTime()

  val testCounter: RIO[DropwizardRegistry, MetricRegistry] = for {
    dwr <- RIO.environment[DropwizardRegistry]
    dwc <- dwr.registry.registerCounter(Label(DropwizardTest.getClass(), Array("test", "counter")))
    c   <- Task(new Counter(dwc))
    _   <- c.inc()
    _   <- c.inc(2.0)
    r   <- dwr.registry.getCurrent()
  } yield r

  val testCounterHelper: RIO[DropwizardRegistry, MetricRegistry] = for {
    c <- counter.register("DropwizardCounterHelper", Array("test", "counter"))
    _ <- c.inc()
    _ <- c.inc(2.0)
    r <- registry.getCurrent()
  } yield r

  val testGauge: RIO[DropwizardRegistry, (MetricRegistry, Long)] = for {
    g <- gauge.register("DropwizardGauge", Array("test", "gauge"), tester)
    r <- registry.getCurrent()
    l <- g.getValue[Long]()
  } yield (r, l)

  val testHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register("DropwizardHistogram", Array("test", "histogram"))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r

  val testUniformHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register("DropwizardUniformHistogram", Array("uniform", "histogram"), new UniformReservoir(512))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r

  val testExponentialHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register(
          "DropwizardExponentialHistogram",
          Array("exponential", "histogram"),
          new ExponentiallyDecayingReservoir
        )
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r

  val testSlidingTimeWindowHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register(
          "DropwizardSlidingHistogram",
          Array("sliding", "histogram"),
          new SlidingTimeWindowArrayReservoir(30, TimeUnit.SECONDS)
        )
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r

  val testMeter: RIO[DropwizardRegistry, MetricRegistry] = for {
    m <- meter.register("DropwizardMeter", Array("test", "meter"))
    _ <- RIO.foreach(Seq(1L, 2L, 3L, 4L, 5L))(m.mark(_))
    r <- registry.getCurrent()
  } yield r

  val testTimer: RIO[DropwizardRegistry, (MetricRegistry, List[Long])] = for {
    r   <- registry.getCurrent()
    t   <- timer.register("DropwizardTimer", Array("test", "timer"))
    ctx <- t.start()
    l <- RIO.foreach(
          List(
            Thread.sleep(1000L),
            Thread.sleep(1400L),
            Thread.sleep(1200L)
          )
        )(_ => t.stop(ctx))
  } yield (r, l)

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("counter increases by `inc` amount") { () =>
        val name = MetricRegistry.name(Show.fixClassName(DropwizardTest.getClass()), Array("test", "counter"): _*)
        val r    = rt.unsafeRun(testCounter)
        val cs   = r.getCounters()
        val c    = if (cs.get(name) == null) 0 else cs.get(name).getCount
        assert(c == 3d)
      },
      test("gauge increases in time") { () =>
        val name = MetricRegistry.name("DropwizardGauge", Array("test", "gauge"): _*)
        val r    = rt.unsafeRun(testGauge)
        val gs   = r._1.getGauges()
        val g    = if (gs.get(name) == null) Long.MaxValue else gs.get(name).getValue().asInstanceOf[Long]
        assert(r._2 < g && g < tester())
      },
      test("histogram increases in time") { () =>
        val name = MetricRegistry.name("DropwizardHistogram", Array("test", "histogram"): _*)
        val r    = rt.unsafeRun(testHistogram)
        val perc75th = r
          .getHistograms()
          .get(name)
          .getSnapshot
          .get75thPercentile

        assert(perc75th == 53.5)
      },
      test("customized uniform histogram increases in time") { () =>
        val name = MetricRegistry.name("DropwizardUniformHistogram", Array("uniform", "histogram"): _*)
        val r    = rt.unsafeRun(testUniformHistogram)
        val perc75th = r
          .getHistograms()
          .get(name)
          .getSnapshot
          .get75thPercentile

        assert(perc75th == 53.5)
      },
      test("exponential histogram increases in time") { () =>
        val name = MetricRegistry.name("DropwizardExponentialHistogram", Array("exponential", "histogram"): _*)
        val r    = rt.unsafeRun(testExponentialHistogram)
        val perc75th = r
          .getHistograms()
          .get(name)
          .getSnapshot
          .get75thPercentile

        assert(perc75th == 50.0)
      },
      test("sliding time window histogram increases in time") { () =>
        val name = MetricRegistry.name("DropwizardSlidingHistogram", Array("sliding", "histogram"): _*)
        val r    = rt.unsafeRun(testSlidingTimeWindowHistogram)
        val perc75th = r
          .getHistograms()
          .get(name)
          .getSnapshot
          .get75thPercentile

        assert(perc75th == 53.5)
      },
      test("Meter count and mean rate are within bounds") { () =>
        val name = MetricRegistry.name("DropwizardMeter", Array("test", "meter"): _*)
        val r    = rt.unsafeRun(testMeter)
        val count = r
          .getMeters()
          .get(name)
          .getCount

        val meanRate = r
          .getMeters()
          .get(name)
          .getMeanRate

        println(s"count: $count, meanRate: $meanRate")
        assert(count == 15 && meanRate > 300 && meanRate < 2000)
      },
      test("Timer called 3 times") { () =>
        val name = MetricRegistry.name("DropwizardTimer", Array("test", "timer"): _*)
        val r    = rt.unsafeRun(testTimer)
        val count = r._1
          .getTimers()
          .get(name)
          .getCount

        println(r._2)

        assert(count == r._2.size && count == 3)
      },
      test("Timer mean rate for 6 calls within bounds") { () =>
        val name = MetricRegistry.name("DropwizardTimer", Array("test", "timer"): _*)
        val r    = rt.unsafeRun(testTimer)
        val meanRate = r._1
          .getTimers()
          .get(name)
          .getMeanRate

        assert(meanRate > 0.78 && meanRate < 0.84)
      },
      test("Report printer is consistent") { () =>
        val str = for {
          dwr <- RIO.environment[DropwizardRegistry]
          j   <- DropwizardExtractor.writeJson(dwr)(None)
        } yield j.spaces2

        rt.unsafeRun(str >>= putStrLn)

        assert(true)

      }
    )
  }

  val harness: Harness[PureHarness.Uses[Unit]] =
    PureHarness.makeFromPrinter((result, name) => {
      println(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result")
    })

  def main(args: Array[String]): Unit =
    tests(harness)((), Nil).print()
}
