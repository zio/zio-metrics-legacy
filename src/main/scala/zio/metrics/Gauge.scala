package zio.metrics

import zio.RIO

trait Gauge {
  val gauge: Gauge.Service[_]
}

object Gauge {
  trait Service[R <: Registry] {
    def register[A: Show](label: Label[A]): RIO[R, Unit]

    def inc(): RIO[R, Unit]

    def dec(): RIO[R, Unit]

    def inc(amount: Double): RIO[R, Unit]

    def dec(amount: Double): RIO[R, Unit]

    def set(amount: Double): RIO[R, Unit]
  }
}
