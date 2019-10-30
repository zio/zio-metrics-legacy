package zio.metrics.prometheus

import zio.Task
import zio.metrics.Counter
import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.CollectorRegistry

trait PrometheusCounter extends Counter {

  val counter = new Counter.Service[PCounter, CollectorRegistry, PCounter] {
    override def inc(pCounter: PCounter): Task[Unit] = {
      Task(pCounter.inc())
    }

    override def inc(pCounter: PCounter, amount: Double): Task[Unit] =
      Task(pCounter.inc(amount))

  }
}

object PrometheusCounter extends PrometheusCounter
