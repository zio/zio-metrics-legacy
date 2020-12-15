package zio.metrics

import zio.{ Has, ZLayer }
import zio.{ Ref, Task, UIO }

package object dropwizard {

  import com.codahale.metrics.{ MetricFilter, MetricRegistry }
  import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
  import com.codahale.metrics.{ Histogram => DWHistogram, Timer => DWTimer }
  import com.codahale.metrics.{ Meter => DWMeter }
  import com.codahale.metrics.MetricRegistry.MetricSupplier
  import com.codahale.metrics.Reservoir

  type Registry          = Has[Registry.Service]
  type HasMetricRegistry = Has[Option[MetricRegistry]]

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
      ZLayer.fromFunction[HasMetricRegistry, Registry.Service](
        optionalRegistry =>
          new Service {
            private val registryRef: UIO[Ref[MetricRegistry]] = {
              val registry = optionalRegistry.get
              Ref.make(registry.getOrElse(new MetricRegistry()))
            }

            def getCurrent(): UIO[MetricRegistry] = registryRef >>= (_.get)

            def registerCounter[L: Show](label: Label[L]): Task[DWCounter] =
              registryRef >>= (_.modify(r => {
                val name = label2Name(label)
                (r.counter(name), r)
              }))

            def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[DWGauge[A]] =
              registryRef >>= (_.modify(r => {
                val name   = label2Name(label)
                val gauges = r.getGauges(MetricFilter.startsWith(name))
                val dwgauge = if (gauges.isEmpty()) {
                  val gw = new DWGauge[A]() {
                    def getValue(): A = f()
                  }
                  gw.asInstanceOf[DWGauge[A]]
                } else gauges.get(gauges.firstKey()).asInstanceOf[DWGauge[A]]
                (r.register(name, dwgauge), r)
              }))

            def registerHistogram[L: Show](label: Label[L], reservoir: Reservoir): Task[DWHistogram] =
              registryRef >>= (_.modify(r => {
                val name = label2Name(label)
                val suppplier = new MetricSupplier[DWHistogram] {
                  def newMetric(): DWHistogram = new DWHistogram(reservoir)
                }
                (r.histogram(name, suppplier), r)
              }))

            def registerTimer[L: Show](label: Label[L]): Task[DWTimer] =
              registryRef >>= (_.modify(r => {
                val name = label2Name(label)
                (r.timer(name), r)
              }))

            def registerMeter[L: Show](label: Label[L]): Task[DWMeter] =
              registryRef >>= (_.modify(r => {
                val name = label2Name(label)
                (r.meter(name), r)
              }))
          }
      )

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
