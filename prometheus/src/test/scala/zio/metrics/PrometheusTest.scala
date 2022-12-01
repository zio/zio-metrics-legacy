package zio.metrics

import io.prometheus.client.CollectorRegistry
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.helpers._
import zio.test.Assertion._
import zio.test.{ assert, Spec, TestAspect, TestClock, TestEnvironment, ZIOSpecDefault }
import zio.{ RIO, ZIO }

import java.util
import zio._

object PrometheusTest extends ZIOSpecDefault {

  private val env = Registry.live ++ Exporters.live

  val counterTestRegistry: RIO[Registry, CollectorRegistry] = for {
    c <- Counter("simple_counter", Array("method", "resource"))
    _ <- c.inc(Array("get", "users"))
    _ <- c.inc(2.0, Array("get", "users"))
    r <- getCurrentRegistry()
  } yield r

  val gaugeTestRegistry: RIO[Registry, (CollectorRegistry, Double)] = for {
    g <- Gauge("simple_gauge", Array("method"))
    _ <- g.inc(Array("get"))
    _ <- g.inc(2.0, Array("get"))
    _ <- g.dec(1.0, Array("get"))
    d <- g.getValue(Array("get"))
    r <- getCurrentRegistry()
  } yield (r, d)

  val histogramTestRegistry: RIO[Registry, CollectorRegistry] = for {
    h <- Histogram("simple_histogram", Array("method"), DefaultBuckets(Seq(10, 20, 30, 40, 50)))
    _ <- ZIO.foreachDiscard(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.observe(_, Array("get")))
    r <- getCurrentRegistry()
  } yield r

  val histogramTimerTestRegistry: RIO[Registry, CollectorRegistry] = for {
    h <- Histogram("simple_histogram_timer", Array("method"), LinearBuckets(1, 2, 5))
    _ <- h.time(() => Thread.sleep(2000), Array("post"))
    r <- getCurrentRegistry()
  } yield r

  val histogramDurationTestRegistry: ZIO[Registry, Throwable, CollectorRegistry] = {
    for {
      h <- Histogram("duration_histogram", Array("method"), ExponentialBuckets(0.25, 2, 5))
      t <- h.startTimer(Array("time"))
      _ <- ZIO.foreachDiscard(List(75L, 750L, 2000L))(
            n =>
              for {
                _ <- ZIO.succeed(n).delay(n.millis)
                _ <- TestClock.adjust(n.millis)
                d <- h.observeDuration(t)
              } yield d
          )
      r <- getCurrentRegistry()
    } yield r
  }

  val summaryTestRegistry: RIO[Registry, CollectorRegistry] = for {
    s <- Summary("simple_summary", Array("method"), List((0.5, 0.05), (0.9, 0.01)))
    _ <- ZIO.foreachDiscard(List(10.5, 25.0, 50.7, 57.3, 19.8))(s.observe(_, Array("put")))
    r <- getCurrentRegistry()
  } yield r

  override def spec: Spec[TestEnvironment, Any] =
    suite("PrometheusLabelsTest")(
      suite("Counter")(
        test("counter increases by `inc` amount") {
          val timeSeriesNames = new util.HashSet[String]() {
            add("simple_counter_total")
          }

          for {
            registry <- counterTestRegistry
            counterValue <- ZIO.succeed(
                             registry
                               .filteredMetricFamilySamples(timeSeriesNames)
                               .nextElement()
                               .samples
                               .get(0)
                               .value
                           )
          } yield assert(counterValue)(equalTo(3.0))
        }
      ),
      suite("Gauge")(
        test("gauge returns latest value") {
          val set: util.Set[String] = new util.HashSet[String]()
          set.add("simple_gauge")

          for {
            registry <- gaugeTestRegistry
            value <- ZIO.succeed(
                      registry._1.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
                    )
          } yield {
            assert(value)(equalTo(registry._2)) &&
            assert(value)(equalTo(2.0))
          }
        }
      ),
      suite("Histogram")(
        test("histogram count and sum are as expected") {
          val set: util.Set[String] = new util.HashSet[String]()
          set.add("simple_histogram_count")
          set.add("simple_histogram_sum")

          for {
            registry <- histogramTestRegistry
            count    <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value)
            sum      <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value)
          } yield {
            assert(count)(equalTo(5.0)) &&
            assert(sum)(equalTo(163.3))
          }
        },
        test("histogram timer accepts lambdas") {
          val set: util.Set[String] = new util.HashSet[String]()
          set.add("simple_histogram_timer_count")
          set.add("simple_histogram_timer_sum")

          for {
            registry <- histogramTimerTestRegistry
            count    <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value)
            sum      <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value)
          } yield {
            assert(count)(equalTo(1.0)) &&
            assert(sum)(isGreaterThanEqualTo(2.0)) &&
            assert(sum)(isLessThanEqualTo(3.0))
          }
        },
        test("histogram duration count and sum are as expected") {
          val set: util.Set[String] = new util.HashSet[String]()
          set.add("duration_histogram_count")
          set.add("duration_histogram_sum")

          for {
            registry <- histogramDurationTestRegistry
            count    <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value)
            sum      <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value)
          } yield {
            assert(count)(equalTo(3.0)) &&
            assert(sum)(isGreaterThanEqualTo(3.1)) &&
            assert(sum)(isLessThan(15.0))
          }
        }
      ),
      suite("Summary")(
        test("summary count and sum are as expected") {
          val set: util.Set[String] = new util.HashSet[String]()
          set.add("simple_summary_count")
          set.add("simple_summary_sum")

          for {
            registry <- summaryTestRegistry
            count    <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(0).value)
            sum      <- ZIO.succeed(registry.filteredMetricFamilySamples(set).nextElement().samples.get(1).value)
          } yield {
            assert(count)(equalTo(5.0)) &&
            assert(sum)(equalTo(163.3))
          }
        }
      )
    ).provideLayer(env) @@ TestAspect.withLiveClock
}
