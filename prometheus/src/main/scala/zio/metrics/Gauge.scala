package zio.metrics

import zio.Task

trait Gauge {
  val gauge: Gauge.Service[_]
}

object Gauge {
  trait Service[G] {

    def getValue(g: G): Task[Double]

    def inc(g: G): Task[Unit]

    def inc(g: G, amount: Double): Task[Unit]

    def dec(g: G): Task[Unit]

    def dec(g: G, amount: Double): Task[Unit]

    def set(g: G, amount: Double): Task[Unit]
  }
}
