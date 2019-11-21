package zio.metrics.prometheus

import zio.Task
import zio.metrics.Exporters
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.{ HTTPServer, PushGateway }
import io.prometheus.client.bridge.Graphite
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.exporter.HttpConnectionFactory
import io.prometheus.client.exporter.BasicAuthHttpConnectionFactory

import java.net.InetSocketAddress
import java.io.StringWriter

trait PrometheusExporters extends Exporters {
  val exporters = new Exporters.Service[CollectorRegistry] {
    override def http(r: CollectorRegistry, port: Int): zio.Task[HTTPServer] =
      Task {
        new HTTPServer(new InetSocketAddress(port), r)
      }

    override def graphite(r: CollectorRegistry, host: String, port: Int, intervalSeconds: Int): Task[Thread] =
      Task {
        val g = new Graphite(host, port)
        g.start(r, intervalSeconds)
      }

    override def pushGateway(
      r: CollectorRegistry,
      host: String,
      port: Int,
      jobName: String,
      user: Option[String],
      password: Option[String],
      httpConnectionFactory: Option[HttpConnectionFactory]
    ): Task[Unit] =
      Task {
        val pg = new PushGateway(s"$host:$port")

        if (user.isDefined)
          for {
            u <- user
            p <- password
          } yield pg.setConnectionFactory(new BasicAuthHttpConnectionFactory(u, p))
        else if (httpConnectionFactory.isDefined)
          for {
            conn <- httpConnectionFactory
          } yield pg.setConnectionFactory(conn)

        pg.pushAdd(r, jobName)
      }

    override def write004(r: CollectorRegistry): Task[String] =
      Task {
        val writer = new StringWriter
        TextFormat.write004(writer, r.metricFamilySamples)
        writer.toString
      }
  }
}
