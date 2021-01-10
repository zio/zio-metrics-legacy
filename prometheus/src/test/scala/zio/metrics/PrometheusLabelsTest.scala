package zio.metrics

import java.util
import zio.{ RIO, Runtime, ZIO }
import testz.{ assert, Harness, PureHarness, Result }
import io.prometheus.client.CollectorRegistry
import zio.metrics.prometheus._
import zio.console.putStrLn
import zio.console.Console
import zio.duration.Duration

import scala.concurrent.duration._
import zio.clock.Clock
import zio.metrics.prometheus.LabelList.LNil

object PrometheusLabelsTest {

  val rt: Runtime.Managed[Registry with Console with Clock] = Runtime
    .unsafeFromLayer(Registry.live ++ Console.live ++ Clock.live)

  val testCounter: RIO[Registry, CollectorRegistry] = for {
    c  <- Counter("simple_counter", None, "method" :: "path" :: LNil)
    _  <- c("GET" :: "/users" :: LNil).inc
    _  <- c("GET" :: "/users" :: LNil).inc(2.0)
    cr <- collectorRegistry
  } yield cr

  val testGauge: RIO[Registry with Clock, (CollectorRegistry, Double)] = for {
    g  <- Gauge("simple_gauge", None, "method" :: LNil)
    _  <- g("GET" :: LNil).inc
    _  <- g("GET" :: LNil).inc(2.0)
    _  <- g("GET" :: LNil).dec(1.0)
    d  <- g("GET" :: LNil).get
    cr <- collectorRegistry
  } yield (cr, d)

  val testHistogram: RIO[Registry with Clock, CollectorRegistry] = for {
    h <- Histogram("simple_histogram", Buckets.Simple(Seq(10, 20, 30, 40, 50)), None, "method" :: LNil)
    _ <- RIO.foreach_(List(10500L, 25000L, 50700L, 57300L, 19800L))(
          (l: Long) => h("GET" :: LNil).observe(Duration.fromMillis(l))
        )
    cr <- collectorRegistry
  } yield cr

  val testHistogramTimer: RIO[Registry with Clock, CollectorRegistry] = for {
    h  <- Histogram("simple_histogram_timer", Buckets.Linear(1, 2, 5), None, "method" :: LNil)
    _  <- h("POST" :: LNil).observe_(() => Thread.sleep(2000))
    cr <- collectorRegistry
  } yield cr

  val f: Long => ZIO[Console with Clock, Nothing, Unit] = (n: Long) => {
    putStrLn(s"n = $n").delay(Duration.fromScala(n.millis))
  }

  val testHistogramDuration: RIO[Registry with Console with Clock, CollectorRegistry] = for {
    h <- Histogram("duration_histogram", Buckets.Exponential(0.25, 2, 5), None, "method" :: LNil)
    t <- h("POST" :: LNil).startTimer
    dl <- RIO.foreach(List(75L, 750L, 2000L))(
           n =>
             for {
               _ <- f(n)
               d <- t.stop
             } yield d
         )
    _  <- RIO.foreach_(dl)(d => putStrLn(d.toString))
    cr <- collectorRegistry
  } yield cr

  val testSummary: RIO[Registry with Clock, CollectorRegistry] = for {
    s <- Summary("simple_summary", List(Quantile(0.5, 0.05), Quantile(0.9, 0.01)), None, "method" :: LNil)
    _ <- RIO.foreach_(List(10500L, 25000L, 50700L, 57300L, 19800L))(
          (l: Long) => s("POST" :: LNil).observe(Duration.fromMillis(millis = l))
        )
    cr <- collectorRegistry
  } yield cr

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
        Result.combine(assert(count == 3.0), assert(sum >= 3.1 && sum <= 5.0))
      },
      test("summary count and sum are as expected") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_summary_count")
        set.add("simple_summary_sum")

        val testExec = testSummary <* string004.map(println)

        val r = rt.unsafeRun(testExec)

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
