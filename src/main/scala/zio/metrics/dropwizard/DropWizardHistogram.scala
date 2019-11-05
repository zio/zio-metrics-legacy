package zio.metrics.dropwizard

import zio.metrics.Histogram
import zio.Task
import com.codahale.metrics.{ Histogram => DWHistogram }

trait DropWizardHistogram extends Histogram {

  val histogram = new Histogram.Service[DWHistogram] {
    override def update(h: DWHistogram, amount: Double): Task[Unit] =
      Task(h.update(amount.toLong))
  }
}
