package zio.metrics.prometheus

import zio.Task
import zio.metrics.Counter
import io.prometheus.client.{ Counter => PCounter }

trait PrometheusCounter extends Counter {

  val counter = new Counter.Service[PCounter] {
    override def inc(pCounter: PCounter, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) pCounter.inc() else pCounter.labels(labelNames: _*).inc())

    override def inc(pCounter: PCounter, amount: Double, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) pCounter.inc(amount) else pCounter.labels(labelNames: _*).inc(amount))
  }
}

object PrometheusCounter extends PrometheusCounter
