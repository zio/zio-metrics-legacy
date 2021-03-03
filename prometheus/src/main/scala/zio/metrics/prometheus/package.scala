package zio.metrics

import zio.{ Has, ZLayer }
import zio.{ Ref, Task, UIO }

package object prometheus {

  import io.prometheus.client.{ Counter => PCounter }
  import io.prometheus.client.{ Gauge => PGauge }
  import io.prometheus.client.{ Histogram => PHistogram }
  import io.prometheus.client.CollectorRegistry
  import io.prometheus.client.{ Summary => PSummary }

  type Registry = Has[Registry.Service]

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

    val explicit: ZLayer[Has[Option[CollectorRegistry]], Nothing, Registry] =
      ZLayer.fromFunction[Has[Option[CollectorRegistry]], Registry.Service](
        optionalRegistry =>
          new Service {
            private val registryRef: UIO[Ref[CollectorRegistry]] = {
              val registry = optionalRegistry.get
              Ref.make(registry.getOrElse(CollectorRegistry.defaultRegistry))
            }

            def getCurrent(): UIO[CollectorRegistry] = registryRef >>= (_.get)

            def registerCounter[A: Show](label: Label[A]): Task[PCounter] =
              registryRef >>= (_.modify(r => {
                val name = Show[A].show(label.name)
                val c = PCounter
                  .build()
                  .name(name)
                  .labelNames(label.labels: _*)
                  .help(label.help)
                  .register(r)
                (c, r)
              }))

            def registerGauge[L: Show](label: Label[L]): Task[PGauge] =
              registryRef >>= (_.modify(r => {
                val name = Show[L].show(label.name)
                val g = PGauge
                  .build()
                  .name(name)
                  .labelNames(label.labels: _*)
                  .help(label.help)
                  .register(r)
                (g, r)
              }))

            def registerHistogram[L: Show](label: Label[L], buckets: Buckets): Task[PHistogram] =
              registryRef >>= (_.modify(r => {
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
                (h.register(r), r)
              }))

            def registerSummary[L: Show](label: Label[L], quantiles: List[(Percentile, Tolerance)]): Task[PSummary] =
              registryRef >>= (_.modify(r => {
                val name = Show[L].show(label.name)
                val sb = PSummary
                  .build()
                  .name(name)
                  .labelNames(label.labels: _*)
                  .help(label.help)

                val s = quantiles.foldLeft(sb)((acc, c) => acc.quantile(c._1, c._2)).register(r)
                (s, r)
              }))
          }
      )

    val live: ZLayer[Any, Nothing, Has[Registry.Service]] = ZLayer.succeed[Option[CollectorRegistry]](None) >>> explicit

  }
}
