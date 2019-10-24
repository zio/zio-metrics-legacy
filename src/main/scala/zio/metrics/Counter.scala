package zio.metrics

import zio.Task

trait Counter {
  val counter: Counter.Service[_, _]
}

object Counter {
  trait Service[R, C] {
    def register[A: Show](registry: R, label: Label[A]): Task[C]

    def inc(counter: C): Task[Unit]

    def inc(counter: C, amount: Double): Task[Unit]
  }
}

