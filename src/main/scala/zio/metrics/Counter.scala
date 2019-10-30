package zio.metrics

import zio.Task

trait Counter {
  val counter: Counter.Service[Nothing, _, _]
}

object Counter {
  trait Service[-C, R, +O] {
    def inc(counter: C): Task[Unit]

    def inc(counter: C, amount: Double): Task[Unit]
  }
}

