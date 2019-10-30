package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Counter
import com.codahale.metrics.{ MetricRegistry, Counter => DWCounter }

trait DropWizardCounter extends Counter {

  val counter = new Counter.Service[DWCounter, MetricRegistry, DWCounter] {

    override def inc(dwCounter: DWCounter): Task[Unit] =
      Task(dwCounter.inc())

    override def inc(dwCounter: DWCounter, amount: Double): Task[Unit] =
      Task(dwCounter.inc(amount.toLong))
  }
}

object DropWizardCounter extends DropWizardCounter
