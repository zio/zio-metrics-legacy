package zio.metrics

import zio.Task

trait Encoder {
  val encoder: Encoder.Service[Metric]
}

object Encoder {
  trait Service[M] {
    def encode(metric: M): Task[Option[String]]
  }
}
