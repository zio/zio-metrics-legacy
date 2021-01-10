package zio.metrics

import java.util
import zio.{ RIO, Runtime, Task }
import zio.console.putStrLn
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.CollectorRegistry
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters._
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration

object PrometheusTest {

  type Env = Registry with Exporters with Clock

  val rt: Runtime.Managed[Registry with Clock with Console] =
    Runtime.unsafeFromLayer(Registry.default ++ Clock.live ++ Console.live)

  val tester: () => Long = () => System.nanoTime()

  val testCounter: RIO[Registry, CollectorRegistry] = for {
    c  <- Counter("PrometheusTest", None)
    _  <- c.inc
    _  <- c.inc(2.0)
    cr <- collectorRegistry
  } yield cr

  val testCounterHelper: RIO[Registry, CollectorRegistry] = for {
    c  <- Counter("PrometheusTestHelper", None)
    _  <- c.inc
    _  <- c.inc(2.0)
    cr <- collectorRegistry
  } yield cr

  val testGauge: RIO[Registry with Clock, (CollectorRegistry, Double)] = for {
    g  <- Gauge("simple_gauge", None)
    _  <- g.inc
    _  <- g.inc(2.0)
    _  <- g.dec(1.0)
    d  <- g.get
    cr <- collectorRegistry
  } yield (cr, d)

  val testHistogram: RIO[Registry with Clock, CollectorRegistry] = for {
    h  <- Histogram("simple_histogram", Buckets.Default, None)
    _  <- RIO.foreach_(List(10500L, 25000L, 50700L, 57300L, 19800L))(l => h.observe(Duration.fromMillis(l)))
    cr <- collectorRegistry
  } yield cr

  val testHistogramTimer: RIO[Registry with Clock, CollectorRegistry] = for {
    h <- Histogram("simple_histogram_timer", Buckets.Default, None)
    t <- h.startTimer
    _ <- Task { () =>
          Thread.sleep(2000); t.stop
        }
    cr <- collectorRegistry
  } yield cr

  val testHistogramTask: RIO[Registry with Clock, (CollectorRegistry, String)] = for {
    h  <- Histogram("task_histogram_timer", Buckets.Default, None)
    s  <- h.observe(Task { Thread.sleep(2000); "Success" })
    cr <- collectorRegistry
  } yield (cr, s)

  val testHistogramTask2: RIO[Registry with Clock, (CollectorRegistry, String)] = for {
    h <- Histogram("task_histogram_timer_", Buckets.Default, None)
    a <- h.observe_[String]({ () =>
          Thread.sleep(2000); "Success"
        })
    cr <- collectorRegistry
  } yield (cr, a)

  val testSummary: RIO[Registry with Clock, CollectorRegistry] = for {
    s  <- Summary("simple_summary", List.empty[Quantile], None)
    _  <- RIO.foreach_(List(10500L, 25000L, 50700L, 57300L, 19800L))(l => s.observe(Duration.fromMillis(l)))
    cr <- collectorRegistry
  } yield cr

  val testSummaryTask: RIO[Registry with Clock, CollectorRegistry] = for {
    s  <- Summary("task_summary_timer", List.empty[Quantile], None)
    _  <- s.observe(Task(Thread.sleep(2000)))
    cr <- collectorRegistry
  } yield cr

  val testSummaryTask2: RIO[Registry with Clock, CollectorRegistry] = for {
    s  <- Summary("task_summary_timer_", List.empty[Quantile], None)
    _  <- s.observe(Task(Thread.sleep(2000)))
    cr <- collectorRegistry
  } yield cr

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("PrometheusTest")
        val r = rt.unsafeRun(testCounter)
        val counter = r
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        assert(counter == 3.0)
      },
      test("counter helper increases by `inc` amount") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("PrometheusTestHelper")
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

        val r = rt.unsafeRun(testHistogramTimer)

        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 1.0), assert(sum >= 2.0 && sum <= 3.0))
      },
      test("histogram timer accepts tasks") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("task_histogram_timer_count")
        set.add("task_histogram_timer_sum")

        val r = rt.unsafeRun(testHistogramTask)

        val count = r._1.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r._1.filteredMetricFamilySamples(set).nextElement().samples.get(1).value

        println(s"Timed Task returns ${r._2} after ${r._2}")

        Result.combine(assert(count == 1.0 && sum >= 2.0 && sum <= 3.0), assert(r._2 == "Success"))
      },
      test("histogram timer_ accepts tasks") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("task_histogram_timer__count")
        set.add("task_histogram_timer__sum")

        val r = rt.unsafeRun(testHistogramTask2)

        val count = r._1.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r._1.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 1.0 && sum >= 2.0 && sum <= 3.0), assert(r._2 == "Success"))
      },
      test("summary count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_summary_count")
        set.add("simple_summary_sum")

        val r     = rt.unsafeRun(testSummary)
        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 5.0), assert(sum == 163.3))
      },
      test("summary timer accepts tasks") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("task_summary_timer_count")
        set.add("task_summary_timer_sum")

        val r = rt.unsafeRun(testSummaryTask)

        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 1.0), assert(sum >= 2.0 && sum <= 3.0))
      },
      test("summary timer_ accepts tasks") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("task_summary_timer__count")
        set.add("task_summary_timer__sum")

        val testExec = testSummaryTask2 <* string004.map(println)

        val r = rt.unsafeRun(testExec)

        val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
        val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
        Result.combine(assert(count == 1.0), assert(sum >= 2.0 && sum <= 3.0))
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
