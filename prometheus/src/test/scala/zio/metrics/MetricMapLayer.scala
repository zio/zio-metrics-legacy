package zio.metrics

/*import zio.{Has, IO, Layer, RIO, Runtime, Task, ZIO, ZLayer}
import zio.console.putStrLn
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters._
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console
import zio.clock.Clock
import io.prometheus.client.CollectorRegistry
import zio.metrics.prometheus.LabelList.LNil*/

object MetricMapLayer {
/*
  val rt: Runtime.Managed[MetricMap with Registry with Exporters with Console] = Runtime.unsafeFromLayer(MetricMap.live ++ (Registry.live >+> Exporters.live) ++ Console.live)

  type MetricMap = Has[MetricMap.Service]

  case class InvalidMetric(msg: String) extends Exception

  object MetricMap {
    trait Service {
      def getRegistry(): RIO[Registry, CollectorRegistry]

      def put(key: String, metric: Metric): Task[Unit]

      def getHistogram(key: String): IO[InvalidMetric, Histogram]

      def getCounter(key: String): IO[InvalidMetric, Counter]
    }

    val live: Layer[Nothing, MetricMap] = ZLayer.succeed(new Service {

      private var metricsMap: Map[String, Metric] = Map.empty[String, Metric]

      def getRegistry(): RIO[Registry, CollectorRegistry] =
        collectorRegistry

      def put(key: String, metric: MetricLabels): Task[Unit] =
        Task(
          this.metricsMap =
            if (metricsMap.contains(key))
              metricsMap.updated(key, metric)
            else
              metricsMap + (key -> metric)
        ).unit

      def getHistogram(key: String): IO[InvalidMetric, Histogram] =
        IO.effect(metricsMap(key).asInstanceOf[Histogram]).catchAll {
          case nsee: NoSuchElementException => IO.fail(InvalidMetric("Metric doesn't exists!"))
          case _                            => IO.fail(InvalidMetric("Metric is not a Histogram"))
        }

      def getCounter(key: String): ZIO[Any, InvalidMetric, Counter] =
        IO.effect(metricsMap(key).asInstanceOf[Counter]).catchAll {
          case nsee: NoSuchElementException => IO.fail(InvalidMetric("Metric doesn't exists!"))
          case _ => IO.fail(InvalidMetric("Metric is not a Counter"))
        }
    })
  }

  val startup: RIO[
    MetricMap with Registry with Clock,
    Unit
  ] =
    for {
      m     <- RIO.environment[MetricMap]
      name  = "MetricMapLayer"
      c     <- Counter(name, None, "metricmap" :: LNil)
      hname = "metricmap_histogram"
      h <- Histogram(hname, Buckets.Default, None, "metricmap" :: "method" :: LNil)
      _ <- m.get.put(name, c)
      _ <- m.get.put(hname, h)
    } yield ()

  val exporterTest: RIO[
    MetricMap with Exporters with Console,
    HTTPServer
  ] =
    http(9090).use(hs => {
      for {
        m  <- RIO.environment[MetricMap]
        _  <- putStrLn("MetricMapLayer")
        c: L[Counter]  <- m.get.getCounter("MetricMapLayer")
        _  <- c("counter" :: LNil).inc
        _  <- c("counter" :: LNil).inc(2.0)
        h  <- m.get.getHistogram("metricmap_histogram")
        _  <- h("histogram" :: "get" :: LNil).observe_(() => Thread.sleep(2000))
        s  <- string004
        _  <- putStrLn(s)
      } yield hs
    })

  val program = startup *> exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program)*/
}
