package zio.metrics

import zio.Task

trait Meter {
  val meter: Meter.Service[Nothing]
}

object Meter {
  trait Service[-R] {

    def mark(m: R): Task[Unit]

    def mark(m: R, amount: Long): Task[Unit]

  }
}
