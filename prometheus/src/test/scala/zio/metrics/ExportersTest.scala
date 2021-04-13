package zio.metrics

import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import zio.test._
import zio.test.Assertion._

object ExportersTest extends DefaultRunnableSpec {
  override def spec =
    suite("ExportersTest")(
      testM("Exporter returns help text") {
        for {
          registry       <- getCurrentRegistry()
          c              <- Counter("ExportersTest", Array("exporter"), "help text")
          _              <- c.inc(Array("counter"))
          exportedString <- write004(registry)
        } yield assert(exportedString)(containsString("# HELP ExportersTest help text"))
      }
    ).provideCustomLayer(Registry.live ++ Exporters.live)
}
