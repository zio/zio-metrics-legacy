package zio.metrics.prometheus

import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.{ Gauge => PGauge }
import io.prometheus.client.{ Collector, CollectorRegistry }

import zio.metrics.Registry
import zio.metrics.typeclasses._
import zio.{Ref, Task, UIO}

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

    override def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[PGauge] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val g = PGauge
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name gauge")
          .register(r)
        val init = f()
        g.set(if (init.isInstanceOf[Double]) init.asInstanceOf[Double] else 0D)
        (g, r)
      }))
  }
}

object PrometheusRegistry extends PrometheusRegistry
