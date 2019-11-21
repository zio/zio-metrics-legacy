package zio.metrics

import zio.Task

trait Exporters {
  val exporters: Exporters.Service[Nothing]
}

object Exporters {
  trait Service[-R] {
    def http(r: R, port: Int): Task[Any]

    def graphite(r: R, host: String, port: Int, intervalSeconds: Int): Task[Thread]
  }
}
