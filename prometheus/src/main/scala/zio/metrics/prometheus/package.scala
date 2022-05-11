package zio.metrics

import zio.{ Task, UIO, ZIO, ZLayer }

package object prometheus {

  import io.prometheus.client.{ Counter => PCounter }
  import io.prometheus.client.{ Gauge => PGauge }
  import io.prometheus.client.{ Histogram => PHistogram }
  import io.prometheus.client.CollectorRegistry
  import io.prometheus.client.{ Summary => PSummary }

  type Registry = Registry.Service

  object Registry {
    trait Service {
      def getCurrent(): UIO[CollectorRegistry]
      def registerCounter[L: Show](label: Label[L]): Task[PCounter]
      def registerGauge[L: Show](label: Label[L]): Task[PGauge]
      def registerHistogram[L: Show](label: Label[L], buckets: Buckets): Task[PHistogram]
      def registerSummary[L: Show](label: Label[L], quantiles: List[(Double, Double)]): Task[PSummary]
    }

    type PTimer     = PSummary.Timer
    type Percentile = Double
    type Tolerance  = Double

    val explicit: ZLayer[CollectorRegistry, Nothing, Registry] = {
      ZLayer.fromZIO {
        ZIO
          .service[CollectorRegistry]
          .map { registry =>
            new Service {

              def getCurrent(): UIO[CollectorRegistry] = ZIO.succeed(registry)

              def registerCounter[A: Show](label: Label[A]): Task[PCounter] =
                ZIO.succeed({
                  val name = Show[A].show(label.name)
                  PCounter
                    .build()
                    .name(name)
                    .labelNames(label.labels: _*)
                    .help(label.help)
                    .register(registry)
                })

              def registerGauge[L: Show](label: Label[L]): Task[PGauge] =
                ZIO.succeed({
                  val name = Show[L].show(label.name)
                  PGauge
                    .build()
                    .name(name)
                    .labelNames(label.labels: _*)
                    .help(label.help)
                    .register(registry)
                })

              def registerHistogram[L: Show](label: Label[L], buckets: Buckets): Task[PHistogram] =
                ZIO.succeed({
                  val name = Show[L].show(label.name)
                  val hb = PHistogram
                    .build()
                    .name(name)
                    .labelNames(label.labels: _*)
                    .help(label.help)

                  val h = buckets match {
                    case DefaultBuckets(bs)          => if (bs.isEmpty) hb else hb.buckets(bs: _*)
                    case LinearBuckets(s, w, c)      => hb.linearBuckets(s, w, c)
                    case ExponentialBuckets(s, f, c) => hb.exponentialBuckets(s, f, c)
                  }
                  h.register(registry)
                })

              def registerSummary[L: Show](label: Label[L], quantiles: List[(Percentile, Tolerance)]): Task[PSummary] =
                ZIO.succeed({
                  val name = Show[L].show(label.name)
                  val sb = PSummary
                    .build()
                    .name(name)
                    .labelNames(label.labels: _*)
                    .help(label.help)

                  quantiles.foldLeft(sb)((acc, c) => acc.quantile(c._1, c._2)).register(registry)
                })
            }
          }
      }
    }

    val live: ZLayer[Any, Nothing, Registry.Service] = ZLayer.succeed(CollectorRegistry.defaultRegistry) >>> explicit

  }
}
