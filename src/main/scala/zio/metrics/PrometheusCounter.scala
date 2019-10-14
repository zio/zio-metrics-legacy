package zio.metrics

import io.prometheus.client.{ Counter => PCounter }
import zio.{ RIO, ZIO }

trait PrometheusCounter extends Counter {
  val counter = new Counter.Service[PrometheusRegistry] {

    private var pCounter: RIO[PrometheusRegistry, Option[PCounter]] = RIO(None)

    override def register[A: Show](label: Label[A]): RIO[PrometheusRegistry, Unit] = {
      val name = Show[A].show(label.name)
      pCounter  = RIO.effect(
        Some(
          PCounter
            .build()
            .name(name)
            .labelNames(label.labels: _*)
            .help(s"$name counter")
            .register()
        )
      )
      RIO.unit
    }

    override def inc(): ZIO[PrometheusRegistry, Nothing, Unit] =
      pCounter.fold(_ => println("Error"), c => c.fold(println("No counter"))(_.inc()))

    override def inc(ammount: Long): ZIO[PrometheusRegistry, Nothing, Unit] =
      pCounter.fold(_ => println("Error"), c => c.fold(println("No counter"))(_.inc(ammount.toDouble)))

  }
}
