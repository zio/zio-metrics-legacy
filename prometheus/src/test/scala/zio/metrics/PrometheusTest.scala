package zio.metrics

import java.util

import zio.{ RIO, Runtime }
import zio.console.{ putStrLn, Console }
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.CollectorRegistry
import zio.internal.PlatformLive
import zio.metrics.prometheus._

object PrometheusTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusExporters with Console.Live,
    PlatformLive.Default
  )

  val tester = () => System.nanoTime()

  val testCounter: RIO[PrometheusRegistry, CollectorRegistry] = for {
    c <- Counter("PrometheusTestHelper", Array.empty[String])
    _ <- c.inc()
    _ <- c.inc(2.0)
    r <- registry.getCurrent()
  } yield r

  val testGauge: RIO[PrometheusRegistry, (CollectorRegistry, Double)] = for {
    pr <- RIO.environment[PrometheusRegistry]
    g  <- Gauge("simple_gauge", Array.empty[String])
    _  <- g.inc()
    _  <- g.inc(2.0)
    _  <- g.dec(1.0)
    d  <- g.getValue()
    r  <- pr.registry.getCurrent()
  } yield (r, d)

  val testHistogram: RIO[PrometheusRegistry, CollectorRegistry] = for {
    h <- Histogram("simple_histogram", Array.empty[String], DefaultBuckets(Seq.empty[Double]))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.observe(_))
    r <- registry.getCurrent()
  } yield r

  val testHistogramTimer: RIO[PrometheusRegistry, CollectorRegistry] = for {
    h <- Histogram("simple_histogram_timer", Array.empty[String], DefaultBuckets(Seq.empty[Double]))
    _ <- h.time(() => Thread.sleep(2000))
    r <- registry.getCurrent()
  } yield r

  val testSummary: RIO[PrometheusRegistry, CollectorRegistry] = for {
    s <- Summary("simple_summary", Array.empty[String], List.empty[(Double, Double)])
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(s.observe(_))
    r <- registry.getCurrent()
  } yield r

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("PrometheusTestHelper")
        val r = rt.unsafeRun(testCounter)
        val counter = r
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        assert(counter == 3.0)
      },
      test("gauge returns latest value") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_gauge")
        val r = rt.unsafeRun(testGauge)
        val a1 = r._1
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value

        assert(a1 == r._2)
        assert(a1 == 2.0)
      },
      test("histogram count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_count")
        set.add("simple_histogram_sum")

        val r     = rt.unsafeRun(testHistogram)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 163.3))
      },
      test("histogram timer accepts lambdas") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_timer_count")
        set.add("simple_histogram_timer_sum")

        val r = rt.unsafeRun(testHistogramTimer.tap(r => exporters.write004(r).map(println)))

        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 1.0), assert(sum >= 2.0 && sum <= 3.0))
      },
      test("summary count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_summary_count")
        set.add("simple_summary_sum")

        val r     = rt.unsafeRun(testSummary)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 163.3))
      }
    )
  }

  val harness: Harness[PureHarness.Uses[Unit]] =
    PureHarness.makeFromPrinter((result, name) => {
      rt.unsafeRun(putStrLn(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result"))
    })

  def main(args: Array[String]): Unit =
    tests(harness)((), Nil).print()
}
