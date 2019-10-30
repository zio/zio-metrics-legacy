package zio.metrics.dropwizard

import com.codahale.metrics.{ Metric, MetricFilter, MetricRegistry }
import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }

import zio.metrics.Registry
import zio.metrics.typeclasses._
import zio.{ Ref, Task, UIO }

trait DropWizardRegistry extends Registry {

  val registry = new Registry.Service[Metric, MetricRegistry] {
    val registryRef: UIO[Ref[MetricRegistry]] = Ref.make(new MetricRegistry())

    override def getCurrent(): UIO[MetricRegistry] = registryRef >>= (_.get)

    override def registerCounter[A: Show](label: Label[A]): Task[DWCounter] =
      registryRef >>= (_.modify(r => {
        val name = Show[A].show(label.name)
        (r.counter(name), r)
      }))

    override def registerGauge[L: Show, A, B](label: Label[L], f: A => B, a: A): Task[DWGauge[B]] =
      registryRef >>= (_.modify(r => {
        val name   = Show[L].show(label.name)
        val gauges = r.getGauges(MetricFilter.startsWith(name))
        val dwgauge = if (gauges.isEmpty()) {
            val gw = new DWGauge[B]() {
              override def getValue(): B = f(a)
            }
            gw.asInstanceOf[DWGauge[B]]
          } else gauges.get(gauges.firstKey()).asInstanceOf[DWGauge[B]]
        (dwgauge, r)
      }))
  }
}

object DropWizardRegistry extends DropWizardRegistry
