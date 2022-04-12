package zio.metrics.prometheus2

import zio.metrics.prometheus2.LabelList._

import zio._

object example {

  // Case class regrouping a set of metrics
  final case class MyMetrics(
    counterWithoutLabels: Counter,
    latencyWithLabels: Histogram.Labelled[L2] // `L2` captures the fact that there are 2 labels on that metric
  )

  object MyMetrics {
    // The layer to register the metrics in the Registry
    def live: ZLayer[Registry with Clock, Throwable, MyMetrics] =
      ZLayer.fromZIO(
        for {
          counterWithoutLabels <- Counter("my_counter", Some("Counting something"))
          latencyWithLabels <- Histogram(
                                "my_histogram",
                                Buckets.Default,
                                Some("Time to do something"),
                                "method" :: "path" :: LNil
                              )
        } yield MyMetrics(counterWithoutLabels, latencyWithLabels)
      )
  }

  val app = for {
    // Access MyMetrics from the environment
    metrics <- ZIO.service[MyMetrics]

    // Increment the counter
    _ <- metrics.counterWithoutLabels.inc

    // Start a timer, do something, record how much time it took
    timer <- metrics.latencyWithLabels("GET" :: "/foo" :: LNil).startTimer
    _ <- ZIO.attempt {
          // Do something
        }
    _ <- timer.stop

    // Same as above but using `observe`. Latency is recorded even if the effect fails
    _ <- metrics
          .latencyWithLabels("GET" :: "/foo" :: LNil)
          .observe(
            ZIO.attempt {
              // Do something
            }
          )

    // Doesn't compile because there is only one label:
    // _ <- metrics.latencyWithLabels("GET" ::: LNil).startTimer
  } yield ()

  val runnableApp = app.provideCustomLayer((Registry.live ++ ZLayer.environment[Clock]) >>> MyMetrics.live)
}
