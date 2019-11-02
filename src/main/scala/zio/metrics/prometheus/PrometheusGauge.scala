package zio.metrics.prometheus

import zio.Task
import zio.metrics._
import io.prometheus.client.{Gauge => PGauge}

trait PrometheusGauge extends Gauge {

  val gauge = new Gauge.Service[PGauge] {

    override def inc[A](g: PGauge): Task[Either[Unit,A]] =
      Task(Left(g.inc()))

   /* override def dec(): zio.Task[Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.dec))

    override def inc(amount: Double): zio.Task[Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.inc(amount)))

    override def dec(amount: Double): zio.Task[Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.dec(amount)))

    override def set(amount: Double): zio.Task[Unit] =
      pGauge.fold(_ => println("Error"), c => c.fold(println("No gauge"))(_.set(amount)))*/
  }
}
