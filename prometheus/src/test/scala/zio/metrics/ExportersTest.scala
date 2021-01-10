package zio.metrics

import io.prometheus.client.exporter.HTTPServer

import zio.{ RIO, Runtime, ZIO }
import zio.console.putStrLn
import zio.clock.Clock
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters._
import zio.console.Console
import zio.metrics.prometheus.LabelList.LNil

object ExportersTest {

  val rt: Runtime.Managed[Registry with Exporters with Clock with Console] =
    Runtime.unsafeFromLayer((Registry.liveWithDefaultMetrics >+> Exporters.live) ++ Clock.live ++ Console.live)

  val exporterTest: RIO[
    Registry with Exporters with Clock with Console,
    HTTPServer
  ] =
    http(9090).use(hs =>
      for {
        c <- Counter("ExportersTest", None, "exporter" :: LNil)
        _ <- c("counter" :: LNil).inc
        _ <- c("counter" :: LNil).inc(2.0)
        h <- Histogram("export_histogram", Buckets.Default, None, "exporter" :: "method" :: LNil)
        _ <- h("histogram" :: "get" :: LNil).observe_(() => Thread.sleep(2000))
        s <- string004
        _ <- putStrLn(s)
      } yield hs
    )

  val program: ZIO[Registry with Exporters with Clock with Console, Throwable, Unit] =
    exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program)
}
