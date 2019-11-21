package zio.metrics

import zio.{ RIO, Runtime }
import zio.internal.PlatformLive
import zio.metrics.prometheus._
import io.prometheus.client.CollectorRegistry

object ExportersTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge with PrometheusHistogram with PrometheusSummary
    with PrometheusExporters,
    PlatformLive.Default
  )

  val tests: RIO[
    PrometheusRegistry with PrometheusCounter with PrometheusHistogram with PrometheusExporters,
    CollectorRegistry
  ] =
    for {
      pr <- RIO.environment[PrometheusRegistry]
      c  <- pr.registry.registerCounter(Label(ExportersTest.getClass(), Array("exporter")))
      _  <- counter.inc(c, Array("counter"))
      _  <- counter.inc(c, 2.0, Array("counter"))
      h  <- pr.registry.registerHistogram(Label("export_histogram", Array("exporter", "method")))
      _  <- histogram.time(h, () => Thread.sleep(2000), Array("histogram", "get"))
      r  <- pr.registry.getCurrent()
    } yield r

  def main(args: Array[String]): Unit = {
    val server = rt.unsafeRun {
      tests >>= (r => exporters.http(r, 9090))
    }
    println(server.getPort())
  }
}
