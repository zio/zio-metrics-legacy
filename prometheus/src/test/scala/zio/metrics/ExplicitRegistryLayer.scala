package zio.metrics

/*import zio.Runtime
import zio.clock.Clock
import zio.console.putStrLn
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.console.Console
import zio.{ Has, URLayer, ZLayer }
import zio.{ IO, RIO, Task }
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.{ Counter => PCounter }
import io.prometheus.client.exporter.HTTPServer
import zio.metrics.prometheus.LabelList.LNil*/

object ExplicitRegistryLayer {
  /*
  val myRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
  val preCounter: PCounter = PCounter
    .build()
    .name("PreExistingCounter")
    .help("Counter configured before using zio-metrics")
    .register(myRegistry)
  preCounter.inc(9)

  val myCustomLayer: ZLayer[Any, Nothing, Registry] = Registry.importRegistry(myRegistry)

  val rt =
    Runtime.unsafeFromLayer(Registry.liveWithDefaultMetrics ++ Console.live)

  type MetricMap = Has[MetricMap.Service]

  case class InvalidMetric(msg: String) extends Exception

  object MetricMap {
    trait Service {
      def getRegistry(): RIO[Registry, CollectorRegistry]

      def put[L <: LabelList](key: String, metric: L): Task[Unit]

      def getHistogram(key: String): IO[InvalidMetric, Histogram]

      def getCounter(key: String): IO[InvalidMetric, Counter]
    }

    val live: URLayer[Registry, MetricMap] = ZLayer.succeed(a = new Service {

      private var metricsMap: Map[String, Metric] = Map.empty[String, Metric]

      def getRegistry(): RIO[Registry, CollectorRegistry] =
        for {
          r  <- RIO.environment[Registry]
          cr <- r.get.collectorRegistry
        } yield cr

      def put(key: String, metric: Metric): Task[Unit] =
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

      def getCounter(key: String): IO[InvalidMetric, Counter] =
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
      m    <- RIO.environment[MetricMap]
      name  = "ExportersTest"
      hname = "export_histogram"
      c    <- Counter(name, None, "exporter" :: LNil)
      h    <- Histogram(hname, Buckets.Default, None,  "exporter" :: "method" :: LNil)
      _    <- m.get.put(name, c)
      _    <- m.get.put(hname, h)
    } yield ()

  val exporterTest: RIO[
    MetricMap with Exporters with Console,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[MetricMap]
      x  <- RIO.environment[Exporters]
      _  <- putStrLn("Exporters")
      r  <- m.get.getRegistry()
      hs <- x.get.http(9090)
      c  <- m.get.getCounter("ExportersTest")
      _  <- c("counter" :: LNil).inc
      _  <- c.inc(2.0, Array("counter"))
      h  <- m.get.getHistogram("export_histogram")
      _  <- h("histogram" :: "get" :: LNil).observe_(() => Thread.sleep(2000))
      s  <- string004
      _  <- putStrLn(s)
    } yield hs

  val program = startup *> exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program)*/
}
