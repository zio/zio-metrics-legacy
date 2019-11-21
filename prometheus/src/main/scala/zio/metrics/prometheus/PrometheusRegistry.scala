package zio.metrics.prometheus

import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.{ Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram }
import io.prometheus.client.{ Collector, CollectorRegistry }
import io.prometheus.client.Summary

import zio.metrics.{ Label, Registry, Show }
import zio.{ Ref, Task, UIO }

trait PrometheusRegistry extends Registry {

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

    override def registerHistogram[L: Show](label: Label[L]): Task[PHistogram] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val h = PHistogram
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name histogram")
          .register(r)
        (h, r)
      }))

    type PTimer     = Summary.Timer
    type Percentile = Double
    type Tolerance  = Double

    override def registerSummary[L: Show](label: Label[L], quantiles: List[(Percentile, Tolerance)]): Task[Summary] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val sb = Summary
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
