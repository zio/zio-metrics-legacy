package zio.metrics

import zio.{ RIO, Task, UIO }
import com.codahale.metrics.MetricRegistry
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import com.codahale.metrics.UniformReservoir
import com.codahale.metrics.ExponentiallyDecayingReservoir
import com.codahale.metrics.SlidingTimeWindowArrayReservoir
import zio.test._
import Assertion._
import zio.test.environment.TestEnvironment

import java.util.concurrent.TimeUnit

object DropwizardTest extends DefaultRunnableSpec {
  private val metricName = "DropwizardTest"

  val tester: () => Long = () => System.nanoTime()

  val counterTestRegistry: RIO[Registry, MetricRegistry] = for {
    dwr <- RIO.environment[Registry]
    dwc <- dwr.get.registerCounter(Label(metricName, Array("test", "counter"), ""))
    c   <- Task(new Counter(dwc))
    _   <- c.inc()
    _   <- c.inc(2.0)
    r   <- dwr.get.getCurrent()
  } yield r

  val testCounterHelper: RIO[Registry, MetricRegistry] = for {
    c <- counter.register("DropwizardCounterHelper", Array("test", "counter"))
    _ <- c.inc()
    _ <- c.inc(2.0)
    r <- getCurrentRegistry()
  } yield r

  val testGauge: RIO[Registry, (MetricRegistry, Long)] = for {
    g <- gauge.register("DropwizardGauge", Array("test", "gauge"), tester)
    r <- getCurrentRegistry()
    l <- g.getValue[Long]()
  } yield (r, l)

  val testHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register("DropwizardHistogram", Array("test", "histogram"))
    _ <- RIO.foreach_(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update)
    r <- getCurrentRegistry()
  } yield r

  val testUniformHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register("DropwizardUniformHistogram", Array("uniform", "histogram"), new UniformReservoir(512))
    _ <- RIO.foreach_(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update)
    r <- getCurrentRegistry()
  } yield r

  val testExponentialHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register(
          "DropwizardExponentialHistogram",
          Array("exponential", "histogram"),
          new ExponentiallyDecayingReservoir
        )
    _ <- RIO.foreach_(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update)
    r <- getCurrentRegistry()
  } yield r

  val testSlidingTimeWindowHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register(
          "DropwizardSlidingHistogram",
          Array("sliding", "histogram"),
          new SlidingTimeWindowArrayReservoir(30, TimeUnit.SECONDS)
        )
    _ <- RIO.foreach_(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update)
    r <- getCurrentRegistry()
  } yield r

  val testMeter: RIO[Registry, MetricRegistry] = for {
    m <- meter.register("DropwizardMeter", Array("test", "meter"))
    _ <- RIO.foreach_(Seq(1L, 2L, 3L, 4L, 5L))(m.mark)
    r <- getCurrentRegistry()
  } yield r

  val testTimer: RIO[Registry, (MetricRegistry, List[Long])] = for {
    r   <- getCurrentRegistry()
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

  val counterSuite: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] = suite("Counter")(
    testM("counter increases by `inc` amount") {
      val name = MetricRegistry.name(metricName, Array("test", "counter"): _*)
      val r = for {
        r        <- counterTestRegistry
        counters <- UIO(r.getCounters())
        count    <- UIO(if (counters.get(name) == null) 0 else counters.get(name).getCount)
      } yield count

      assertM(r)(equalTo(3.toLong))
    }
  ).provideCustomLayer(Registry.live)

  val gaugeSuite = suite("Gauge")(
    testM("gauge increases in time") {
      val name = MetricRegistry.name("DropwizardGauge", Array("test", "gauge"): _*)
      for {
        r      <- testGauge
        gauges <- UIO(r._1.getGauges())
        g      <- UIO(if (gauges.get(name) == null) Long.MaxValue else gauges.get(name).getValue.asInstanceOf[Long])
      } yield {
        assert(r._2)(isLessThan(g)) &&
        assert(g)(isLessThan(tester()))
      }
    }
  ).provideCustomLayer(Registry.live)

  val histogramSuite = suite("Histogram")(
    testM("histogram increases in time") {
      val name = MetricRegistry.name("DropwizardHistogram", Array("test", "histogram"): _*)
      for {
        r        <- testHistogram
        perc75th <- UIO(r.getHistograms().get(name).getSnapshot.get75thPercentile())
      } yield assert(perc75th)(equalTo(53.5))
    },
    testM("customized uniform histogram increases in time") {
      val name = MetricRegistry.name("DropwizardUniformHistogram", Array("uniform", "histogram"): _*)
      for {
        registry <- testUniformHistogram
        perc75th <- UIO(registry.getHistograms().get(name).getSnapshot.get75thPercentile())
      } yield assert(perc75th)(equalTo(53.5))
    },
    testM("exponential histogram increases in time") {
      val name = MetricRegistry.name("DropwizardExponentialHistogram", Array("exponential", "histogram"): _*)

      for {
        r        <- testExponentialHistogram
        perc75th <- UIO(r.getHistograms().get(name).getSnapshot.get75thPercentile())
      } yield assert(perc75th)(equalTo(50.0))
    },
    testM("sliding time window histogram increases in time") {
      val name = MetricRegistry.name("DropwizardSlidingHistogram", Array("sliding", "histogram"): _*)

      for {
        r        <- testSlidingTimeWindowHistogram
        perc75th <- UIO(r.getHistograms().get(name).getSnapshot.get75thPercentile())
      } yield assert(perc75th)(equalTo(53.5))
    }
  ).provideCustomLayer(Registry.live)

  val meterSuite = suite("Meter")(
    testM("Meter count and mean rate are within bounds") {
      val name = MetricRegistry.name("DropwizardMeter", Array("test", "meter"): _*)

      for {
        r        <- testMeter
        count    <- UIO(r.getMeters.get(name).getCount)
        meanRate <- UIO(r.getMeters().get(name).getMeanRate)
      } yield {
        assert(count)(equalTo(15.toLong)) &&
        assert(meanRate)(isGreaterThan(60.toDouble)) &&
        assert(meanRate)(isLessThanEqualTo(1000.toDouble))
      }
    }
  ).provideCustomLayer(Registry.live)

  val timerSuite = suite("Timer")(
    testM("Timer called 3 times") {
      val name = MetricRegistry.name("DropwizardTimer", Array("test", "timer"): _*)

      for {
        r     <- testTimer
        count <- UIO(r._1.getTimers().get(name).getCount)
      } yield {
        assert(count.toInt)(equalTo(r._2.size)) &&
        assert(count.toInt)(equalTo(3))
      }
    },
    testM("Timer mean rate for 6 calls within bounds") {
      val name = MetricRegistry.name("DropwizardTimer", Array("test", "timer"): _*)

      for {
        r        <- testTimer
        meanRate <- UIO(r._1.getTimers().get(name).getMeanRate)
      } yield {
        assert(meanRate)(isGreaterThan(0.78)) &&
        assert(meanRate)(isLessThan(0.84))
      }
    }
  ).provideCustomLayer(Registry.live)

  val printerSuite = suite("Report printer")(
    testM("Report printer is consistent") {
      for {
        registry <- getCurrentRegistry()
        _        <- DropwizardExtractor.writeJson(registry)(None)
      } yield assert(true)(isTrue)
    }
  ).provideCustomLayer(Registry.live)

  def spec: Spec[_root_.zio.test.environment.TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("DropwizardTests")(counterSuite, gaugeSuite, histogramSuite, meterSuite, timerSuite, printerSuite)

}
