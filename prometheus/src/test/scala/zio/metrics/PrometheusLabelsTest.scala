package zio.metrics

import java.util

import zio.{ RIO, Runtime }
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.{ CollectorRegistry }
import zio.internal.PlatformLive
import zio.metrics.prometheus._
import zio.console.putStrLn
import zio.console.Console
import zio.duration.Duration
import scala.concurrent.duration._
import _root_.zio.clock.Clock

object PrometheusLabelsTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge with PrometheusHistogram
        with PrometheusSummary with PrometheusExporters with Console.Live with Clock.Live,
    PlatformLive.Default
  )

  val testCounter: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(Label("simple_counter", Array("method", "resource")))
    _  <- counter.inc(c, Array("get", "users"))
    _  <- counter.inc(c, 2.0, Array("get", "users"))
    r  <- pr.registry.getCurrent()
  } yield r

  val testGauge: RIO[PrometheusRegistry with PrometheusGauge, (CollectorRegistry, Double)] = for {
    pr <- RIO.environment[PrometheusRegistry]
    r  <- pr.registry.getCurrent()
    g  <- pr.registry.registerGauge(Label("simple_gauge", Array("method")))
    _  <- gauge.inc(g, Array("get"))
    _  <- gauge.inc(g, 2.0, Array("get"))
    d  <- gauge.getValue(g, Array("get"))
  } yield (r, d)

  val testHistogram: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    h  <- registry.registerHistogram("simple_histogram", Array("method"))
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(histogram.observe(h, _, Array("get")))
    r  <- registry.getCurrent()
  } yield r

  val testHistogramTimer: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    h  <- registry.registerHistogram("simple_histogram_timer", Array("method"))
    _  <- histogram.time(h, () => Thread.sleep(2000), Array("post"))
    r  <- registry.getCurrent()
  } yield r

  val f = (n: Long) => {
    RIO.sleep(Duration.fromScala(n.millis)) *> putStrLn(s"n = $n")
  }

  val testHistogramDuration: RIO[PrometheusRegistry with PrometheusHistogram
      with Console with Clock, CollectorRegistry] = for {
    h  <- registry.registerHistogram("duration_histogram", Array("method"))
    t  <- histogram.startTimer(h, Array("time"))
    dl <- RIO.foreach(List(75L, 150L, 200L))(n => for {
            _ <- f(n)
            d <- histogram.observeDuration(t)
          } yield d
          )
    _  <- RIO.foreach(dl)(d => putStrLn(d.toString()))
    r  <- registry.getCurrent()
  } yield r

  val testSummary: RIO[PrometheusRegistry with PrometheusSummary, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    s  <- pr.registry.registerSummary(Label("simple_summary", Array("method")), List((0.5, 0.05), (0.9, 0.01)))
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(summary.observe(s, _, Array("put")))
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

        val r     = rt.unsafeRun(testHistogram)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 163.3))
      },
      test("histogram timer accepts lambdas") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_timer_count")
        set.add("simple_histogram_timer_sum")

        val r     = rt.unsafeRun(testHistogramTimer)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value

        Result.combine(assert(count == 1.0), assert(sum >= 2.0 && sum <= 3.0))
      },
      test("histogram duration count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("duration_histogram_count")
        set.add("duration_histogram_sum")

        val r     = rt.unsafeRun(testHistogramDuration)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 3.0), assert(sum >= 1.1 && sum <= 1.6))
      },
      test("summary count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_summary_count")
        set.add("simple_summary_sum")

        val r = rt.unsafeRun(testSummary.tap(r => exporters.write004(r).map(println)))

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
