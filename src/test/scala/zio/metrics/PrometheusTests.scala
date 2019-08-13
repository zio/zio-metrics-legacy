package zio.metrics

import java.util

import scalaz.Scalaz._
import zio.metrics.PrometheusMetrics.DoubleSemigroup
import scalaz.std.string.stringInstance
import zio.{ DefaultRuntime, IO, Task }
import testz.{ assert, Harness, PureHarness, Result }

object PrometheusTests extends DefaultRuntime {

  val prometheusMetrics = new PrometheusMetrics

  val testCounter: Task[Unit] = for {
    f <- prometheusMetrics.counter(Label("simple_counter", Array("test", "counter"), ""))
    _ <- f(1)
    b <- f(2)
  } yield b

  val testGauge: (Option[Double] => Double) => Task[Unit] = (f: Option[Double] => Double) =>
    for {
      g <- prometheusMetrics.gauge[Double, Double, String](Label("simple_gauge", Array("test", "gauge"), ""))(f)
      _ <- g(5.0.some)
      b <- g((-3.0).some)
    } yield b

  val testTimer: Task[List[Double]] = for {
    t  <- prometheusMetrics.timer(Label("simple_timer", Array("test", "timer"), ""))
    t1 = t.start
    l <- IO.foreach(
          List(
            Thread.sleep(1000L),
            Thread.sleep(1400L),
            Thread.sleep(1200L)
          )
        )(_ => t.stop(t1))
  } yield l

  val testHistogram: Task[Unit] = {
    import scala.math.Numeric.IntIsIntegral
    for {
      h <- prometheusMetrics.histogram(Label("simple_histogram", Array("test", "hist"), ""))
      _ <- IO.foreach(List(h(10), h(25), h(50), h(57), h(19)))(_.unit)
    } yield ()
  }

  val testHistogramTimer: Task[Unit] = {
    import scala.math.Numeric.IntIsIntegral
    for {
      h <- prometheusMetrics.histogramTimer(Label("simple_histogram_timer", Array("test", "tid"), ""))
      _ <- IO.foreach(List(h(), h(), h(), h(), h()))(io => {
            Thread.sleep(500)
            io.unit
          })
    } yield ()
  }

  val testMeter: Task[Unit] = for {
    m <- prometheusMetrics.meter(Label("simple_meter", Array("test", "meter"), ""))
    _ <- IO.foreach(1 to 5)(_ => m(2))
  } yield ()

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        unsafeRun(testCounter)
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_counter")
        val counter = prometheusMetrics.registry
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        assert(counter == 3.0)
      },
      test("gauge returns latest value") { () =>
        val tester: Option[Double] => Double = (op: Option[Double]) => op.getOrElse(0.0)
        unsafeRun(testGauge(tester))
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_gauge")
        val a1 = prometheusMetrics.registry
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value

        assert(a1 == 2.0)
      },
      test("Timer called 3 times") { () =>
        unsafeRun(testTimer)
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_timer_count")
        set.add("simple_timer_sum")
        val count = prometheusMetrics.registry
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        val sum = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value

        Result.combine(assert(count == 3.0), assert(sum >= 3.6))
      },
      test("Histogram sum is 161 and count is 5") { () =>
        unsafeRun(testHistogram)
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_count")
        set.add("simple_histogram_sum")

        val count = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 161.0))
      },
      test("Histogram timer") { () =>
        unsafeRun(testHistogramTimer)
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_histogram_timer_count")
        set.add("simple_histogram_timer_sum")
        val count = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value

        val sum = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum > 2.5))
      },
      test("Meter invoked 5 times") { () =>
        unsafeRun(testMeter)
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_meter_count")
        set.add("simple_meter_sum")
        val count = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = prometheusMetrics.registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum >= 10.0))
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
