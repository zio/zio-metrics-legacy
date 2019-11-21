package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Meter
import com.codahale.metrics.{ Meter => DWMeter }

trait DropWizardMeter extends Meter {
  val meter = new Meter.Service[DWMeter] {
    override def mark(m: DWMeter): zio.Task[Unit] =
      Task(m.mark())

    override def mark(m: DWMeter, amount: Long): zio.Task[Unit] =
      Task(m.mark(amount))
  }
}

object DropWizardMeter extends DropWizardMeter
