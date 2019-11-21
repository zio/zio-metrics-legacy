package zio.metrics

import zio.Task

trait Summary {
  val summary: Summary.Service[Nothing, _]
}

object Summary {
  trait Service[-R, T] {
    def observe(s: R, amount: Double, labelNames: Array[String]): Task[Unit]

    def startTimer(s: R, labelNames: Array[String]): Task[T]

    def observeDuration(timer: T): Task[Double]

    def time(s: R, f: () => Unit, labelNames: Array[String]): Task[Double]
  }
}
