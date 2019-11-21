package zio.metrics

import zio.Task

trait Histogram {
  val histogram: Histogram.Service[Nothing, _]
}

object Histogram {
  trait Service[-R, T] {
    def observe(h: R, amount: Double, labelNames: Array[String]): Task[Unit]

    def startTimer(h: R, labelNames: Array[String]): Task[T]

    def observeDuration(timer: T): Task[Double]

    def time(h: R, f: () => Unit, labelNames: Array[String]): Task[Double]
  }
}
