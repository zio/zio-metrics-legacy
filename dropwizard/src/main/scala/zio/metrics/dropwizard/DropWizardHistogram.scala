package zio.metrics.dropwizard

import zio.metrics.Histogram
import zio.Task
import com.codahale.metrics.{ Histogram => DWHistogram }
import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context

trait DropWizardHistogram extends Histogram {

  val histogram = new Histogram.Service[DWHistogram, Timer, Context] {
    override def update(histogram: DWHistogram, amount: Double): Task[Unit] =
      Task(histogram.update(amount.toLong))

  }
}

object DropWizardHistogram extends DropWizardHistogram
