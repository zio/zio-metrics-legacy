package zio.metrics

import java.util

import zio.{ RIO, Runtime }
import zio.console.{ Console, putStrLn }
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.CollectorRegistry
import zio.internal.PlatformLive
import zio.metrics.prometheus._

object PrometheusTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge with PrometheusHistogram with PrometheusSummary
        with PrometheusExporters with Console.Live,
    PlatformLive.Default
  )

  val tester = () => System.nanoTime()

  val testCounter: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(Label(PrometheusTest.getClass(), Array.empty[String]))
    pc <- RIO.environment[PrometheusCounter]
    _  <- pc.counter.inc(c, Array.empty[String])
    _  <- pc.counter.inc(c, 2.0, Array.empty[String])
    r  <- pr.registry.getCurrent()
  } yield r

  val testCounterHelper: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    c  <- registry.registerCounter("PrometheusTestHelper")
    _  <- counter.inc(c)
    _  <- counter.inc(c, 2.0)
    r  <- registry.getCurrent()
  } yield r

  val testGauge: RIO[PrometheusRegistry with PrometheusGauge, (CollectorRegistry, Double)] = for {
    pr <- RIO.environment[PrometheusRegistry]
    r  <- pr.registry.getCurrent()
    g  <- pr.registry.registerGauge(Label("simple_gauge", Array.empty[String]))
    _  <- gauge.inc(g)
    _  <- gauge.inc(g, 2.0)
    d  <- gauge.getValue(g)
  } yield (r, d)

  val testHistogram: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    h  <- registry.registerHistogram("simple_histogram")
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(histogram.observe(h, _))
    r  <- registry.getCurrent()
  } yield r

  val testHistogramTimer: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    h  <- registry.registerHistogram("simple_histogram_timer")
    _  <- histogram.time(h, () => Thread.sleep(2000))
    r  <- registry.getCurrent()
  } yield r

  val testSummary: RIO[PrometheusRegistry with PrometheusSummary, CollectorRegistry] = for {
    s  <- registry.registerSummary("simple_summary")
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(summary.observe(s, _))
    r  <- registry.getCurrent()
  } yield r

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add(Show.fixClassName(PrometheusTest.getClass()))
        val r = rt.unsafeRun(testCounter)
        val counter = r
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        assert(counter == 3.0)
      },
      test("counter increases by `inc` amount on helper method") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add(Show.fixClassName(PrometheusTest.getClass()))
        val r = rt.unsafeRun(testCounterHelper)
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
        assert(a1 == 3.0)
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
