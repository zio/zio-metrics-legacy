package zio.metrics.dropwizard

import zio.Task
import zio.metrics._
import com.codahale.metrics.{ MetricRegistry, Counter => DWCounter }

trait DropWizardCounter extends Counter {

  val counter = new Counter.Service[DWCounter, MetricRegistry, DWCounter] {

    override def register[A: Show](registry: MetricRegistry, label: Label[A]): Task[(MetricRegistry, DWCounter)] = {
      val name = Show[A].show(label.name)
      Task((registry, registry.counter(name)))
    }

    override def inc(dwCounter: DWCounter): Task[Unit] =
      Task(dwCounter.inc())

    override def inc(dwCounter: DWCounter, amount: Double): Task[Unit] =
      Task(dwCounter.inc(amount.toLong))
  }
}

object DropWizardCounter extends DropWizardCounter
