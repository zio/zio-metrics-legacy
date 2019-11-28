package zio.metrics

import zio.Task
import io.prometheus.client.exporter.HttpConnectionFactory

trait Exporters {
  val exporters: Exporters.Service[Nothing]
}

object Exporters {
  trait Service[-R] {
    def http(r: R, port: Int): Task[Any]

    def graphite(r: R, host: String, port: Int, intervalSeconds: Int): Task[Thread]

    def pushGateway(
      r: R,
      hots: String,
      port: Int,
      jobName: String,
      user: Option[String],
      password: Option[String],
      httpConnectionFactory: Option[HttpConnectionFactory]
    ): Task[Unit]

    def write004(r: R): Task[String]

    def initializeDefaultExports(r: R): Task[Unit]
  }
}
