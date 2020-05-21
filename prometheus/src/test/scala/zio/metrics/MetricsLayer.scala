package zio.metrics

import zio.Runtime
import zio.console.putStrLn
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console
import zio.{ Has, Layer, ZLayer }
import zio.{ RIO, Task }
import io.prometheus.client.CollectorRegistry

object MetricsLayer {

  type Env = Registry with Exporters with Console

  val rt = Runtime.unsafeFromLayer(Registry.live ++ Exporters.live ++ Console.live)

  type Metrics = Has[Metrics.Service]

  object Metrics {
    trait Service {
      def getRegistry(): Task[CollectorRegistry]

      def inc(tags: Array[String]): Task[Unit]

      def inc(amount: Double, tags: Array[String]): Task[Unit]

      def time(f: () => Unit, tags: Array[String]): Task[Double]

    }

    val live: Layer[Nothing, Metrics] = ZLayer.succeed(new Service {

      private val (myCounter, myHistogram) = rt.unsafeRun(
        for {
          c <- counter.register("myCounter", Array("name", "method"))
          h <- histogram.register("myHistogram", Array("name", "method"))
        } yield (c, h)
      )

      def getRegistry(): Task[CollectorRegistry] =
        getCurrentRegistry().provideLayer(Registry.live)

      def inc(tags: Array[String]): zio.Task[Unit] =
        inc(1.0, tags)

      def inc(amount: Double, tags: Array[String]): Task[Unit] =
        myCounter.inc(amount, tags)

      def time(f: () => Unit, tags: Array[String]): Task[Double] =
        myHistogram.time(f, tags)
    })

    val receiver: (Counter, Histogram) => Layer[Nothing, Metrics] =
      (counter, histogram) =>
        ZLayer.succeed(
          new Service {

            def getRegistry(): Task[CollectorRegistry] =
              getCurrentRegistry().provideLayer(Registry.live)

            def inc(tags: Array[String]): zio.Task[Unit] =
              inc(1.0, tags)

            def inc(amount: Double, tags: Array[String]): Task[Unit] =
              counter.inc(amount, tags)

            def time(f: () => Unit, tags: Array[String]): Task[Double] =
              histogram.time(f, tags)
          }
        )

    val receiverHas: ZLayer[Has[(Counter, Histogram)], Nothing, Metrics] =
      ZLayer.fromFunction[Has[(Counter, Histogram)], Metrics.Service](
        minst =>
          new Service {

            def getRegistry(): Task[CollectorRegistry] =
              getCurrentRegistry().provideLayer(Registry.live)

            def inc(tags: Array[String]): zio.Task[Unit] =
              inc(1.0, tags)

            def inc(amount: Double, tags: Array[String]): Task[Unit] =
              minst.get._1.inc(amount, tags)

            def time(f: () => Unit, tags: Array[String]): Task[Double] =
              minst.get._2.time(f, tags)
          }
      )
  }

  import io.prometheus.client.{ Counter => PCounter, Histogram => PHistogram }
  val c = Counter(
    PCounter
      .build()
      .name("PrometheusCounter")
      .labelNames(Array("class", "method"): _*)
      .help(s"Sample prometheus counter")
      .register()
  )
  val h = Histogram(
    PHistogram
      .build()
      .name("PrometheusHistogram")
      .labelNames(Array("class", "method"): _*)
      .help(s"Sample prometheus histogram")
      .register()
  )

  val rLayerHas     = ZLayer.succeed[(Counter, Histogram)]((c, h)) >>> Metrics.receiverHas
  val rtReceiverhas = Runtime.unsafeFromLayer(rLayerHas ++ Exporters.live ++ Console.live)

  val rLayer     = Metrics.receiver(c, h)
  val rtReceiver = Runtime.unsafeFromLayer(rLayer ++ Exporters.live ++ Console.live)

  val exporterTest: RIO[
    Metrics with Exporters with Console,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[Metrics]
      _  <- putStrLn("Exporters")
      r  <- m.get.getRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      _  <- m.get.inc(Array("RequestCounter", "get"))
      _  <- m.get.inc(Array("RequestCounter", "post"))
      _  <- m.get.inc(2.0, Array("LoginCounter", "login"))
      _  <- m.get.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- putStrLn(s)
    } yield hs

  val program = exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program.provideSomeLayer[Env](Metrics.live))
}
