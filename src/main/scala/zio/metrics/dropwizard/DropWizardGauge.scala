package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Gauge
import zio.metrics.typeclasses._
import com.codahale.metrics.{ MetricRegistry, Gauge => DWGauge }
import com.codahale.metrics.MetricFilter

trait DropWizardGauge extends Gauge {
  val gauge = new Gauge.Service[MetricRegistry, DWGauge[_]] {
    override def register[L: Show, A, B](
      registry: MetricRegistry,
      label: Label[L],
      f: A => B
    ): Task[(MetricRegistry, A => DWGauge[B])] = {
      val name   = Show[L].show(label.name)
      val gauges = registry.getGauges(MetricFilter.startsWith(name))
      val dwgauge = (a: A) => {
        if (gauges.isEmpty()) {
          val gw = new DWGauge[B]() {
            override def getValue(): B = f(a)
          }
          gw.asInstanceOf[DWGauge[B]]
        } else gauges.get(gauges.firstKey()).asInstanceOf[DWGauge[B]]
      }
      Task((registry, dwgauge))
    }

    override def inc[A, B](g: A => DWGauge[_], a: A): Task[B] =
      Task(g(a).getValue().asInstanceOf[B])
  }
}

object DropWizardGauge extends DropWizardGauge
