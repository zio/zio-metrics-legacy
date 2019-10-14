package zio.metrics

import zio.RIO

trait Counter {
  val counter: Counter.Service[_]
}

object Counter {
  trait Service[R <: Registry] {
    def register[A: Show](label: Label[A]): RIO[R, Unit]

    def inc(): RIO[R, Unit]

    def inc(amount: Long): RIO[R, Unit]
  }
}

