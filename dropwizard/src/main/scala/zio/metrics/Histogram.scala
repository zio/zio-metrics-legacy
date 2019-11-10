package zio.metrics

import zio.Task

trait Histogram {
  val histogram: Histogram.Service[Nothing, _, _]
}

object Histogram {
  trait Service[-R, T, C] {

    def update(histogram: R, amount: Double): Task[Unit]

  }
}
