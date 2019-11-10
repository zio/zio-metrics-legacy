package zio.metrics.prometheus

import zio.Task
import zio.metrics.Gauge
import io.prometheus.client.{ Gauge => PGauge }

trait PrometheusGauge extends Gauge {
  val gauge = new Gauge.Service[PGauge] {
    override def getValue(g: PGauge): Task[Double] =
      Task(g.get())

    override def inc(g: PGauge): Task[Unit] =
      Task(g.inc())

    override def dec(g: PGauge): Task[Unit] =
      Task(g.dec())

    override def inc(g: PGauge, amount: Double): Task[Unit] =
      Task(g.inc(amount))

    override def dec(g: PGauge, amount: Double): Task[Unit] =
      Task(g.dec(amount))

    override def set(g: PGauge, amount: Double): Task[Unit] =
      Task(g.set(amount))
  }
}

object PrometheusGauge extends PrometheusGauge
