package zio.metrics

import zio.Task

trait Histogram {
  val histogram: Histogram.Service[Nothing]
}

object Histogram {
  trait Service[-R] {
    def update(h: R, amount: Double): Task[Unit]
  }
}
