package zio.metrics

import zio.Task

trait Gauge {
  val gauge: Gauge.Service[_]
}

object Gauge {
  trait Service[G] {

    def inc[A](g: G): Task[Either[Unit,A]]

    /*def dec[A](g: A => G, a: A): Task[Unit]

   def inc(gauge: I, amount: Double): Task[Unit]

    def dec(gauge: I, amount: Double): Task[Unit]

    def set(gauge: I, amount: Double): Task[Unit]*/
  }
}
