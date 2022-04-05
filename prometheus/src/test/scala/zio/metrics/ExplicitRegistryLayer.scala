package zio.metrics

import zio.Runtime
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import zio.{ Layer, ZLayer }
import zio.{ IO, RIO, Task }
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.exporter.HTTPServer
import zio.Console
import zio.Console.printLine

object ExplicitRegistryLayer {

  val myRegistry = CollectorRegistry.defaultRegistry
  val preCounter = PCounter
    .build()
    .name("PreExistingCounter")
    .help("Counter configured before using zio-metrics")
    .register(myRegistry)
  preCounter.inc(9)

  val myCustomLayer = ZLayer.succeed(myRegistry) >>> Registry.explicit

  val rt = Runtime.unsafeFromLayer(MetricMap.live ++ Exporters.live ++ Console.live)

  type MetricMap = MetricMap.Service

  case class InvalidMetric(msg: String) extends Exception

  object MetricMap {
    import zio.metrics.prometheus.Metric

    trait Service {
      def getRegistry(): Task[CollectorRegistry]

      def put(key: String, metric: Metric): Task[Unit]

      def getHistogram(key: String): IO[InvalidMetric, Histogram]

      def getCounter(key: String): IO[InvalidMetric, Counter]
    }

    val live: Layer[Nothing, MetricMap] = ZLayer.succeed(new Service {

      private var metricsMap: Map[String, Metric] = Map.empty[String, Metric]

      def getRegistry(): Task[CollectorRegistry] =
        getCurrentRegistry().provideLayer(myCustomLayer)

      def put(key: String, metric: Metric): Task[Unit] =
        Task
          .succeed(
            this.metricsMap =
              if (metricsMap.contains(key))
                metricsMap.updated(key, metric)
              else
                metricsMap + (key -> metric)
          )
          .unit

      def getHistogram(key: String): IO[InvalidMetric, Histogram] =
        metricsMap(key) match {
          case h @ Histogram(_) => IO.succeed(h)
          case _                => IO.fail(InvalidMetric("Metric is not a Histogram or doesn't exists!"))
        }

      def getCounter(key: String): IO[InvalidMetric, Counter] =
        metricsMap(key) match {
          case c @ Counter(_) => IO.succeed(c)
          case _              => IO.fail(InvalidMetric("Metric is not a Counter or doesn't exists!"))
        }
    })
  }

  val startup: RIO[
    MetricMap,
    Unit
  ] =
    for {
      m     <- RIO.environment[MetricMap]
      name  = "ExportersTest"
      c     <- counter.register(name, Array("exporter")).provideLayer(myCustomLayer)
      hname = "export_histogram"
      h <- histogram
            .register(hname, Array("exporter", "method"))
            .provideLayer(myCustomLayer)
      _ <- m.get.put(name, c)
      _ <- m.get.put(hname, h)
    } yield ()

  val exporterTest: RIO[
    MetricMap with Exporters with Console,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[MetricMap]
      _  <- printLine("Exporters")
      r  <- m.get.getRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      c  <- m.get.getCounter("ExportersTest")
      _  <- c.inc(Array("counter"))
      _  <- c.inc(2.0, Array("counter"))
      h  <- m.get.getHistogram("export_histogram")
      _  <- h.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- printLine(s)
    } yield hs

  val program = startup *> exporterTest flatMap (server => Console.printLine(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program)
}
