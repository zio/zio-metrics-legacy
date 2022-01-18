package zio.metrics

import zio.ZLayer
import zio.{ Ref, Task, UIO }

package object dropwizard {

  import com.codahale.metrics.{ MetricFilter, MetricRegistry }
  import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
  import com.codahale.metrics.{ Histogram => DWHistogram, Timer => DWTimer }
  import com.codahale.metrics.{ Meter => DWMeter }
  import com.codahale.metrics.MetricRegistry.MetricSupplier
  import com.codahale.metrics.Reservoir

  type Registry          = Registry.Service
  type HasMetricRegistry = Option[MetricRegistry]

  object Registry {
    trait Service {
      def getCurrent(): UIO[MetricRegistry]
      def registerCounter[L: Show](label: Label[L]): Task[DWCounter]
      def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[DWGauge[A]]
      def registerHistogram[L: Show](label: Label[L], reservoir: Reservoir): Task[DWHistogram]
      def registerMeter[L: Show](label: Label[L]): Task[DWMeter]
      def registerTimer[L: Show](label: Label[L]): Task[DWTimer]
    }

    private def label2Name[L: Show](label: Label[L]): String =
      MetricRegistry.name(Show[L].show(label.name), label.labels: _*)

    val explicit: ZLayer[HasMetricRegistry, Nothing, Registry] =
      ZLayer.fromFunctionZIO[HasMetricRegistry, Nothing, Registry.Service] { optionalRegistry =>
        val registry = optionalRegistry.get
        Ref.make(registry.getOrElse(new MetricRegistry())).map { reg =>
          new Service {

            def getCurrent(): UIO[MetricRegistry] = reg.get

            def registerCounter[L: Show](label: Label[L]): Task[DWCounter] =
              reg.modify(r => {
                val name = label2Name(label)
                (r.counter(name), r)
              })

            def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[DWGauge[A]] =
              reg.modify(r => {
                val name   = label2Name(label)
                val gauges = r.getGauges(MetricFilter.startsWith(name))
                val dwgauge = if (gauges.isEmpty()) {
                  val gw = new DWGauge[A]() {
                    def getValue(): A = f()
                  }
                  gw.asInstanceOf[DWGauge[A]]
                } else gauges.get(gauges.firstKey()).asInstanceOf[DWGauge[A]]
                (r.register(name, dwgauge), r)
              })

            def registerHistogram[L: Show](label: Label[L], reservoir: Reservoir): Task[DWHistogram] =
              reg.modify(r => {
                val name = label2Name(label)
                val suppplier = new MetricSupplier[DWHistogram] {
                  def newMetric(): DWHistogram = new DWHistogram(reservoir)
                }
                (r.histogram(name, suppplier), r)
              })

            def registerTimer[L: Show](label: Label[L]): Task[DWTimer] =
              reg.modify(r => {
                val name = label2Name(label)
                (r.timer(name), r)
              })

            def registerMeter[L: Show](label: Label[L]): Task[DWMeter] =
              reg.modify(r => {
                val name = label2Name(label)
                (r.meter(name), r)
              })
          }
        }
      }

    val live: ZLayer[Any, Nothing, Registry] = ZLayer.succeed[Option[MetricRegistry]](None) >>> explicit

    def makeFilter(filter: Option[String]): MetricFilter = filter match {
      case Some(s) =>
        s.charAt(0) match {
          case '+' => MetricFilter.startsWith(s.substring(1))
          case '-' => MetricFilter.endsWith(s.substring(1))
          case _   => MetricFilter.contains(s)
        }
      case _ => MetricFilter.ALL
    }
  }

}
