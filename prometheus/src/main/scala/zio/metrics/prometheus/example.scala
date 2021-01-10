package zio.metrics.prometheus

import zio.metrics.prometheus.LabelList._

import zio._
import zio.clock._

object example {

  // Case class regrouping a set of metrics
  final case class MyMetrics(
    counterWithoutLabels: Counter,
    latencyWithLabels: Histogram.Labelled[L2] // `L2` captures the fact that there are 2 labels on that metric
  )

  object MyMetrics {
    // The layer to register the metrics in the Registry
    def live: ZLayer[Registry with Clock, Throwable, Has[MyMetrics]] =
      (for {
        counterWithoutLabels <- Counter("my_counter", Some("Counting something"))
        latencyWithLabels <- Histogram(
          "my_histogram",
          Buckets.Default,
          Some("Time to do something"),
          "method" :: "path" :: LNil
        )
      } yield MyMetrics(counterWithoutLabels, latencyWithLabels)).toLayer
  }

  val app: ZIO[Has[MyMetrics], Throwable, Unit] = for {
    // Access MyMetrics from the environment
    metrics <- ZIO.service[MyMetrics]

    // Increment the counter
    _ <- metrics.counterWithoutLabels.inc

    // Start a timer, do something, record how much time it took
    timer <- metrics.latencyWithLabels("GET" :: "/foo" :: LNil).startTimer
    _ <- ZIO.effect {
          // Do something
        }
    _ <- timer.stop

    // Same as above but using `observe`. Latency is recorded even if the effect fails
    _ <- metrics
          .latencyWithLabels("GET" :: "/foo" :: LNil)
          .observe(
            ZIO.effect {
              // Do something
            }
          )

    // Doesn't compile because there is only one label:
    // _ <- metrics.latencyWithLabels("GET" ::: LNil).startTimer
  } yield ()

  val runnableApp: ZIO[zio.ZEnv, Throwable, Unit] = app.provideCustomLayer((Registry.live ++ ZLayer.requires[Clock]) >>> MyMetrics.live)
}
