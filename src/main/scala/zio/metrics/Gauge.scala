package zio.metrics

import zio.RIO

trait Gauge {
  val gauge: Gauge.Service[_, _]
}

object Gauge {
  trait Service[R, G] {
    def register[L: Show, A, B](registry: R, label: Label[L], f: A => B): RIO[R, (R, A => G)]

    def inc[A, B](g: A => G, a: A): RIO[R, B]

    /*def dec(gauge: I): Task[Unit]

    def inc(gauge: I, amount: Double): Task[Unit]

    def dec(gauge: I, amount: Double): Task[Unit]

    def set(gauge: I, amount: Double): Task[Unit]*/
  }
}
