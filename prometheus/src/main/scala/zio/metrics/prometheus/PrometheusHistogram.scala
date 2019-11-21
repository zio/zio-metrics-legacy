package zio.metrics.prometheus

import zio.metrics.Histogram
import zio.Task
import io.prometheus.client.{ Histogram => PHistogram }

trait PrometheusHistogram extends Histogram {

  type HistogramTimer = PHistogram.Timer

  val histogram = new Histogram.Service[PHistogram, HistogramTimer] {
    override def observe(h: PHistogram, amount: Double, labelNames: Array[String]): Task[Unit] =
      Task(if (labelNames.isEmpty) h.observe(amount) else h.labels(labelNames: _*).observe(amount))

    override def startTimer(h: PHistogram, labelNames: Array[String]): Task[HistogramTimer] =
      Task(if (labelNames.isEmpty) h.startTimer() else h.labels(labelNames: _*).startTimer())

    override def observeDuration(timer: HistogramTimer): Task[Double] =
      Task(timer.observeDuration())

    override def time(h: PHistogram, f: () => Unit, labelNames: Array[String]): Task[Double] =
      Task {
        val t = if (labelNames.isEmpty) h.startTimer() else h.labels(labelNames: _*).startTimer()
        f()
        t.observeDuration()
      }
  }
}

object PrometheusHistogram extends PrometheusHistogram
