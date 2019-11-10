package zio.metrics.prometheus

import zio.metrics.Histogram
import zio.Task
import io.prometheus.client.{ Histogram => PHistogram }

trait PrometheusHistogram extends Histogram {

  type HistogramTimer = PHistogram.Timer

  val histogram = new Histogram.Service[PHistogram, HistogramTimer] {
    override def observe(h: PHistogram, amount: Double): Task[Unit] =
      Task(h.observe(amount))

    override def startTimer(h: PHistogram): Task[HistogramTimer] =
      Task(h.startTimer())

    override def observeDuration(timer: HistogramTimer): Task[Double] =
      Task(timer.observeDuration())

    override def time(h: PHistogram, f: () => Unit): Task[Double] =
      Task{
        val t = h.startTimer()
        f()
        t.observeDuration()
      }
  }
}

object PrometheusHistogram extends PrometheusHistogram
