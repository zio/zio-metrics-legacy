package zio.metrics.prometheus

import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.{ Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram }
import io.prometheus.client.{ Collector, CollectorRegistry }
import io.prometheus.client.{ Summary => PSummary }

import zio.metrics.{ Label, Show }
import zio.{ Ref, Task, UIO }

trait PrometheusRegistry extends Registry {

  type PTimer     = PSummary.Timer
  type Percentile = Double
  type Tolerance  = Double

  val registry = new Registry.Service[Collector, CollectorRegistry] {
    val registryRef: UIO[Ref[CollectorRegistry]] = Ref.make(CollectorRegistry.defaultRegistry)

    override def getCurrent(): UIO[CollectorRegistry] = registryRef >>= (_.get)

    override def registerCounter[A: Show](label: Label[A]): Task[PCounter] =
      registryRef >>= (_.modify(r => {
        val name = Show[A].show(label.name)
        val c = PCounter
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name counter")
          .register(r)
        (c, r)
      }))

    override def registerGauge[L: Show](label: Label[L]): Task[PGauge] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val g = PGauge
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name gauge")
          .register(r)
        (g, r)
      }))

    override def registerHistogram[L: Show](label: Label[L], buckets: Buckets): Task[PHistogram] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val hb = PHistogram
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name histogram")

        val h = buckets match {
          case DefaultBuckets(bs)          => if (bs.isEmpty) hb else hb.buckets(bs: _*)
          case LinearBuckets(s, w, c)      => hb.linearBuckets(s, w, c)
          case ExponentialBuckets(s, f, c) => hb.exponentialBuckets(s, f, c)
        }
        (h.register(r), r)
      }))

    override def registerSummary[L: Show](label: Label[L], quantiles: List[(Percentile, Tolerance)]): Task[PSummary] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val sb = PSummary
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name timer")

        val s = quantiles.foldLeft(sb)((acc, c) => acc.quantile(c._1, c._2)).register(r)
        (s, r)
      }))
  }
}

object PrometheusRegistry extends PrometheusRegistry
