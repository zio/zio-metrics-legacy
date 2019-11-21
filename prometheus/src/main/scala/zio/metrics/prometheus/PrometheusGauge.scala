package zio.metrics.prometheus

import zio.Task
import zio.metrics.Gauge
import io.prometheus.client.{ Gauge => PGauge }

trait PrometheusGauge extends Gauge {
  val gauge = new Gauge.Service[PGauge] {
    override def getValue(g: PGauge, labelNames: Array[String]): Task[Double] =
      Task(if (labelNames.isEmpty) g.get() else g.labels(labelNames: _*).get())

    override def inc(g: PGauge, labelNames: Array[String]): Task[Unit] =
      Task {
        if (labelNames.isEmpty) g.inc()
        else g.labels(labelNames: _*).inc()
      }

    override def dec(g: PGauge, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) g.dec() else g.labels(labelNames: _*).dec())

    override def inc(g: PGauge, amount: Double, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) g.inc(amount) else g.labels(labelNames: _*).inc(amount))

    override def dec(g: PGauge, amount: Double, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) g.dec(amount) else g.labels(labelNames: _*).dec(amount))

    override def set(g: PGauge, amount: Double, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) g.set(amount) else g.labels(labelNames: _*).set(amount))

    override def setToCurrentTime(g: PGauge, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) g.setToCurrentTime() else g.labels(labelNames: _*).setToCurrentTime())

    override def setToTime(g: PGauge, f: () => Unit, labelNames: Array[String]): Task[Unit] =
      Task {
        val t = if (labelNames.isEmpty) g.startTimer() else g.labels(labelNames: _*).startTimer()
        f()
        g.set(t.setDuration)
      }
  }
}

object PrometheusGauge extends PrometheusGauge
