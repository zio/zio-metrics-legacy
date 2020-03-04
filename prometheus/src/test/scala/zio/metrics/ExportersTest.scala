package zio.metrics

import zio.{ RIO, Runtime }
import zio.console.putStrLn
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console

object ExportersTest {

  val rt = Runtime.default

  val exporterTest: RIO[
    Registry with Exporters with Console,
    HTTPServer
  ] =
    for {
      r  <- getCurrentRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      c  <- Counter("ExportersTest", Array("exporter"))
      _  <- c.inc(Array("counter"))
      _  <- c.inc(2.0, Array("counter"))
      h  <- histogram.register("export_histogram", Array("exporter", "method"))
      _  <- h.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- putStrLn(s)
    } yield hs

  val program = exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program.provideLayer(Registry.live ++ Exporters.live ++ Console.live))
}
