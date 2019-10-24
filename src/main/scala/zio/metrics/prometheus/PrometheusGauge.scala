package zio.metrics.prometheus

import zio.RIO
import zio.metrics._
import io.prometheus.client.{Gauge => PGauge}

trait PrometheusGauge extends Gauge {
  val gauge = new Gauge.Service[PrometheusRegistry] {

    var pGauge: RIO[PrometheusRegistry, Option[PGauge]] = RIO.effect(None)

    override def register[A: Show](label: Label[A]): zio.RIO[PrometheusRegistry,Unit] = {
      val name = Show[A].show(label.name)
      pGauge = for {
        pr <- RIO.environment[PrometheusRegistry]
        p <- pr.registry.get()
      } yield Some(
          PGauge
            .build()
            .name(name)
            .labelNames(label.labels: _*)
            .help(s"$name gauge")
            .register(p)
        )
      RIO.unit
    }

    override def inc(): zio.RIO[PrometheusRegistry,Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.inc()))

    override def dec(): zio.RIO[PrometheusRegistry,Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.dec))

    override def inc(amount: Double): zio.RIO[PrometheusRegistry,Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.inc(amount)))

    override def dec(amount: Double): zio.RIO[PrometheusRegistry,Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.dec(amount)))

    override def set(amount: Double): zio.RIO[PrometheusRegistry,Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.set(amount)))
  }
}
