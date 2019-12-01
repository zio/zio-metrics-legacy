package zio.metrics

import zio.{ RIO, Runtime }
import zio.console.putStrLn
import zio.internal.PlatformLive
import zio.metrics.prometheus._
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console

object ExportersTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusHistogram
        with PrometheusExporters with Console.Live,
    PlatformLive.Default
  )

  val exporterTest: RIO[
    PrometheusRegistry with PrometheusCounter with PrometheusHistogram with PrometheusExporters with Console,
    (CollectorRegistry, HTTPServer)
  ] =
    for {
      r  <- registry.getCurrent()
      _  <- exporters.initializeDefaultExports(r)
      hs <- exporters.http(r, 9090)
      c  <- registry.registerCounter(ExportersTest.getClass(), Array("exporter"))
      _  <- counter.inc(c, Array("counter"))
      _  <- counter.inc(c, 2.0, Array("counter"))
      h  <- registry.registerHistogram("export_histogram", Array("exporter", "method"))
      _  <- histogram.time(h, () => Thread.sleep(2000), Array("histogram", "get"))
      s  <- exporters.write004(r)
      _  <- putStrLn(s)
    } yield (r, hs)

  def main(args: Array[String]): Unit =
    rt.unsafeRun(exporterTest >>= (tup => putStrLn(s"Server port: ${tup._2.getPort()}")))
}
