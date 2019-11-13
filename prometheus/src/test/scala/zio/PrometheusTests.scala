package zio.metrics

import java.util

import zio.{ RIO, Runtime }
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.{ CollectorRegistry }
import zio.internal.PlatformLive
import zio.metrics.prometheus._

import java.io.StringWriter
import io.prometheus.client.exporter.common.TextFormat

object PrometheusTests {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge
        with PrometheusHistogram with PrometheusSummary,
    PlatformLive.Default
  )

  def write004(r: CollectorRegistry): String = {
    val writer = new StringWriter
    TextFormat.write004(writer, r.metricFamilySamples)
    writer.toString
  }

  val tester = () => System.nanoTime()

  val testCounter: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(Label("simple_counter", Array.empty[String]))
    _  <- counter.inc(c)
    _  <- counter.inc(c, 2.0)
    r  <- pr.registry.getCurrent()
  } yield r

  val testGauge: RIO[PrometheusRegistry with PrometheusGauge, (CollectorRegistry, Double)] = for {
    pr    <- RIO.environment[PrometheusRegistry]
    r     <- pr.registry.getCurrent()
    g     <- pr.registry.registerGauge(Label("simple_gauge", Array.empty[String]), tester)
    _     <- gauge.inc(g)
    _     <- gauge.inc(g, 2.0)
    d     <- gauge.getValue(g)
  } yield (r, d)

  val testHistogram: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("simple_histogram", Array.empty[String]))
    _  <-  RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(histogram.observe(h, _))
    r  <- pr.registry.getCurrent()
  } yield r

  val testHistogramTimer: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("simple_histogram_timer", Array.empty[String]))
    _  <- histogram.time(h, () => Thread.sleep(2000))
    r  <- pr.registry.getCurrent()
  } yield r

  val testSummary: RIO[PrometheusRegistry with PrometheusSummary, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    s  <- pr.registry.registerSummary(Label("simple_summary", Array.empty[String]), List.empty[(Double, Double)])
    _  <-  RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(summary.observe(s, _))
    r  <- pr.registry.getCurrent()
  } yield r

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_counter")
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
        assert(a1 == 3.0)
      },
      test("histogram count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_count")
        set.add("simple_histogram_sum")

        val r = rt.unsafeRun(testHistogram)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 163.3))
      },
      test("histogram timer accepts lambdas") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_timer_count")
        set.add("simple_histogram_timer_sum")

        val r = rt.unsafeRun(testHistogramTimer)
        println(s"registry: ${write004(r)}")

        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 1.0), assert(sum >= 2.0 && sum <= 3.0))
      },
      test("summary count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_summary_count")
        set.add("simple_summary_sum")

        val r = rt.unsafeRun(testSummary)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 163.3))
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
