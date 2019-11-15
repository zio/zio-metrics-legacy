package zio.metrics

import zio.Task

trait Gauge {
  val gauge: Gauge.Service[_]
}

object Gauge {
  trait Service[G] {

    def getValue(g: G, labelNames: Array[String]): Task[Double]

    def inc(g: G, labelNames: Array[String]): Task[Unit]

    def inc(g: G, amount: Double, labelNames: Array[String]): Task[Unit]

    def dec(g: G, labelNames: Array[String]): Task[Unit]

    def dec(g: G, amount: Double, labelNames: Array[String]): Task[Unit]

    def set(g: G, amount: Double, labelNames: Array[String]): Task[Unit]

    def setToCurrentTime(g: G, labelNames: Array[String]): Task[Unit]

    def setToTime(g: G, f: () => Unit, labelNames: Array[String]): Task[Unit]
  }
}
