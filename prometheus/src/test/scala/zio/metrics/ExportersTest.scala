package zio.metrics

import zio.{ RIO, Runtime }
import zio.console.putStrLn
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import io.prometheus.client.exporter.HTTPServer
import testz.{ assert, Harness, PureHarness }
import zio.console.Console

object ExportersTest {

  val rt = Runtime.unsafeFromLayer(Registry.live ++ Exporters.live ++ Console.live)

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

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("Exporter returns help text") { () =>
        val ex = for {
          r <- getCurrentRegistry()
          c <- Counter("ExportersTest", Array("exporter"), "help text")
          _ <- c.inc(Array("counter"))
          s <- write004(r)
          _ <- putStrLn(s)
        } yield s
        val exportedString = rt.unsafeRun(ex)
        assert(exportedString.contains("# HELP ExportersTest help text"))
      }
    )
  }

  val harness: Harness[PureHarness.Uses[Unit]] =
    PureHarness.makeFromPrinter((result, name) => {
      println(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result")
    })

  def main(args: Array[String]): Unit =
    tests(harness)((), Nil).print()
}
