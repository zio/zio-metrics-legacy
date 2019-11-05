package zio.metrics.prometheus

import zio.metrics.Histogram
import zio.Task
import io.prometheus.client.{ Histogram => PHistogram }

trait PrometheusHistogram extends Histogram {

  val histogram = new Histogram.Service[PHistogram] {
    override def update(h: PHistogram, amount: Double): Task[Unit] =
      Task(h.observe(amount))
  }

}
