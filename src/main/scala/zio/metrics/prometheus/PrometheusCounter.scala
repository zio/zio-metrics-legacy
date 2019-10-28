package zio.metrics.prometheus

import zio.Task
import zio.metrics._
import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.CollectorRegistry

trait PrometheusCounter extends Counter {

  val counter = new Counter.Service[PCounter, CollectorRegistry, PCounter] {

    override def register[A: Show](registry: CollectorRegistry, label: Label[A]): Task[(CollectorRegistry, PCounter)] = {
      val name = Show[A].show(label.name)
      val c = PCounter
          .build()
          .name(name)
          .labelNames(label.labels: _*)
          .help(s"$name counter")
          .register(registry)
      Task((registry, c))
    }

    override def inc(pCounter: PCounter): Task[Unit] = {
      Task(pCounter.inc())
    }

    override def inc(pCounter: PCounter, amount: Double): Task[Unit] =
      Task(pCounter.inc(amount))

  }
}

object PrometheusCounter extends PrometheusCounter
