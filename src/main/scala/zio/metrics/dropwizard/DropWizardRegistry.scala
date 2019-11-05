package zio.metrics.dropwizard

import com.codahale.metrics.{ Metric, MetricFilter, MetricRegistry }
import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
import com.codahale.metrics.{ Histogram => DWHistogram }
import com.codahale.metrics.MetricRegistry.MetricSupplier
import com.codahale.metrics.UniformReservoir

import zio.metrics.{ Label, Registry, Show }
import zio.{ Ref, Task, UIO }

trait DropWizardRegistry extends Registry {

  val registry = new Registry.Service[Metric, MetricRegistry] {
    val registryRef: UIO[Ref[MetricRegistry]] = Ref.make(new MetricRegistry())

    override def getCurrent(): UIO[MetricRegistry] = registryRef >>= (_.get)

    override def registerCounter[L: Show](label: Label[L]): Task[DWCounter] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        (r.counter(name), r)
      }))

    override def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[DWGauge[A]] =
      registryRef >>= (_.modify(r => {
        val name   = Show[L].show(label.name)
        val gauges = r.getGauges(MetricFilter.startsWith(name))
        val dwgauge = if (gauges.isEmpty()) {
            val gw = new DWGauge[A]() {
              override def getValue(): A = f()
            }
            gw.asInstanceOf[DWGauge[A]]
          } else gauges.get(gauges.firstKey()).asInstanceOf[DWGauge[A]]
        (r.register(name, dwgauge), r)
      }))


    override def registerHistogram[L: Show](label: Label[L]): Task[DWHistogram] =
      registryRef >>= (_.modify(r => {
        val name = Show[L].show(label.name)
        val suppplier = new MetricSupplier[DWHistogram] {
          override def newMetric(): DWHistogram = new DWHistogram(new UniformReservoir)
        }
        (r.histogram(name, suppplier), r)
      }))
  }
}

object DropWizardRegistry extends DropWizardRegistry
