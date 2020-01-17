package zio.metrics.statsd

import zio.Task

trait Encoder {
  val encoder: Encoder.Service[Nothing]
}

object Encoder {
  trait Service[-M] {
    def encode(metric: M): Task[Option[String]]
  }
}
