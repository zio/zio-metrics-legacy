package zio.metrics

import zio.Task

trait Gauge {
  val gauge: Gauge.Service[_]
}

object Gauge {
  trait Service[G] {

    def getValue[A](g: G): Task[A]

  }
}
