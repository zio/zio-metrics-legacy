package zio.metrics

import zio.Runtime
import zio.console.putStrLn
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console
import zio.{ Has, Layer, ZLayer }
import zio.{ IO, RIO, Task }
import io.prometheus.client.CollectorRegistry

object MetricMapLayer {

  val rt = Runtime.unsafeFromLayer(MetricMap.live ++ Registry.live ++ Exporters.live ++ Console.live)

  type MetricMap = Has[MetricMap.Service]

  case class InvalidMetric(msg: String) extends Exception

  object MetricMap {
    trait Service {
      def getRegistry(): Task[CollectorRegistry]

      def put(name: String, metric: Metric): Task[Unit]

      def getHistogram(name: String): IO[InvalidMetric, Histogram]

      def getCounter(name: String): IO[InvalidMetric, Counter]
    }

    val live: Layer[Nothing, MetricMap] = ZLayer.succeed(new Service {

      private var metricsMap: Map[String, Metric] = Map.empty[String, Metric]

      def getRegistry(): Task[CollectorRegistry] =
        getCurrentRegistry().provideLayer(Registry.live)

      def put(name: String, metric: Metric): Task[Unit] =
        Task(
          this.metricsMap =
            if (metricsMap.contains(name))
              metricsMap.updated(name, metric)
            else
              metricsMap + (name -> metric)
        ).unit

      def getHistogram(name: String): IO[InvalidMetric, Histogram] =
        metricsMap(name) match {
          case h @ Histogram(_) => IO.succeed(h)
          case _                => IO.fail(InvalidMetric("Metric is not a Histogram or doesn't exists!"))
        }

      def getCounter(name: String): IO[InvalidMetric, Counter] =
        metricsMap(name) match {
          case c @ Counter(_) => IO.succeed(c)
          case _              => IO.fail(InvalidMetric("Metric is not a Counter or doesn't exists!"))
        }
    })

  }

  val startup: RIO[
    MetricMap with Registry,
    Unit
  ] =
    for {
      m     <- RIO.environment[MetricMap]
      name  = "ExportersTest"
      c     <- Counter(name, Array("exporter"))
      hname = "export_histogram"
      h <- histogram
            .register(hname, Array("exporter", "method"))
            .provideLayer(Registry.live)
      _ <- m.get.put(name, c)
      _ <- m.get.put(hname, h)
    } yield ()

  val exporterTest: RIO[
    MetricMap with Exporters with Console,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[MetricMap]
      _  <- putStrLn("Exporters")
      r  <- m.get.getRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      c  <- m.get.getCounter("ExportersTest")
      _  <- c.inc(Array("counter"))
      _  <- c.inc(2.0, Array("counter"))
      h  <- m.get.getHistogram("export_histogram")
      _  <- h.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- putStrLn(s)
    } yield hs

  val program = startup *> exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program)
}
