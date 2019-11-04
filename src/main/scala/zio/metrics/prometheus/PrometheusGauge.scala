package zio.metrics.prometheus

import zio.Task
import zio.metrics._
import io.prometheus.client.{ Gauge => PGauge }

trait PrometheusGauge extends Gauge {

  val gauge = new Gauge.Service[PGauge] {

    override def getValue[A](g: PGauge): Task[A] =
      Task(g.get().asInstanceOf[A])

  }

  object Service {
    def inc(g: PGauge): Task[Unit] =
      Task(g.inc())

    def dec(g: PGauge): Task[Unit] =
      Task(g.dec())

    def inc(g: PGauge, amount: Double): Task[Unit] =
      Task(g.inc(amount))

    def dec(g: PGauge, amount: Double): Task[Unit] =
      Task(g.dec(amount))

    def set[A](g: PGauge, amount: Double): Task[Unit] =
      Task(g.set(amount))
  }
}
