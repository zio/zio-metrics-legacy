package zio.metrics

import zio.{ RIO, Runtime }
import zio.console.putStrLn
import zio.internal.PlatformLive
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console

object ExportersTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusExporters with Console.Live,
    PlatformLive.Default
  )

  val exporterTest: RIO[
    PrometheusRegistry with PrometheusExporters with Console,
    HTTPServer
  ] =
    for {
      r  <- registry.getCurrent()
      _  <- exporters.initializeDefaultExports(r)
      hs <- exporters.http(r, 9090)
      c  <- Counter("ExportersTest", Array("exporter"))
      _  <- c.inc(Array("counter"))
      _  <- c.inc(2.0, Array("counter"))
      h  <- Histogram("export_histogram", Array("exporter", "method"), DefaultBuckets(Seq.empty[Double]))
      _  <- h.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- exporters.write004(r)
      _  <- putStrLn(s)
    } yield hs

  def main(args: Array[String]): Unit =
    rt.unsafeRun(exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}")))
}
