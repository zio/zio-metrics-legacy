package zio.metrics

import zio.Task

trait Counter {
  val counter: Counter.Service[Nothing]
}

object Counter {
  trait Service[-C] {
    def inc(counter: C): Task[Unit]

    def inc(counter: C, amount: Double): Task[Unit]
  }
}

