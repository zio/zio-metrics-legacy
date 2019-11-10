package zio.metrics

import java.util

import zio.{ RIO, Runtime }
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.{ CollectorRegistry, Counter => PCounter}
import io.prometheus.client.{ Histogram => PHistogram }
import zio.internal.PlatformLive
import zio.metrics.prometheus._

import java.io.StringWriter
import io.prometheus.client.exporter.common.TextFormat

object PrometheusTests {

  object counter {
    def inc(pCounter: PCounter): RIO[PrometheusCounter, Unit] = RIO.accessM(_.counter.inc(pCounter))

    def inc(pCounter: PCounter, amount: Double): RIO[PrometheusCounter, Unit] =
      RIO.accessM(_.counter.inc(pCounter, amount))
  }

  object histogram {
    def update(h: PHistogram, amount: Double): RIO[PrometheusHistogram, Unit] =
      RIO.accessM(_.histogram.observe(h, amount))

    def time(h: PHistogram, f: () => Unit): RIO[PrometheusHistogram, Double] =
      RIO.accessM(_.histogram.time(h, f))
  }

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge with PrometheusHistogram,
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
    gauge <- RIO.environment[PrometheusGauge]
    r     <- pr.registry.getCurrent()
    g     <- pr.registry.registerGauge(Label("simple_gauge", Array.empty[String]), tester)
    _     <- gauge.gauge.inc(g)
    _     <- gauge.gauge.inc(g, 2.0)
    d     <- gauge.gauge.getValue(g)
  } yield (r, d)

  val testHistogram: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("simple_histogram", Array.empty[String]))
    _  <-  RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(histogram.update(h, _))
    r  <- pr.registry.getCurrent()
  } yield r

  val testHistogramTimer: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("simple_histogram_timer", Array.empty[String]))
    _  <- histogram.time(h, () => Thread.sleep(2000))
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
      test("histogram returns latest value") { () =>
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