package zio.metrics.prometheus

import zio.Task
import zio.metrics.Exporters
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter._
import io.prometheus.client.bridge.Graphite
import java.net.InetSocketAddress

trait PrometheusExporters extends Exporters {
  val exporters = new Exporters.Service[CollectorRegistry] {
    override def http(r: CollectorRegistry, port: Int): zio.Task[HTTPServer] =
      Task {
        new HTTPServer(new InetSocketAddress(1234), r)
      }

    override def graphite(r: CollectorRegistry, host: String, port: Int, intervalSeconds: Int): Task[Thread] = {
      Task {
        val g = new Graphite(host, port)
        g.start(r, intervalSeconds)
      }
    }
  }
}
