package zio.metrics

import zio.{ Layer, RIO, Runtime, Task, ZLayer }
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters.Exporters
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.CollectorRegistry
import zio.Console
import zio.Console.printLine

object MetricsLayer {

  type Env = Registry with Exporters

  val rt = Runtime.unsafeFromLayer(Registry.live ++ Exporters.live)

  type Metrics = Metrics.Service

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

    val receiverHas: ZLayer[(Counter, Histogram), Nothing, Metrics] =
      ZLayer.fromFunction[(Counter, Histogram), Metrics.Service](
        minst =>
          new Service {
            def getRegistry(): Task[CollectorRegistry] =
              getCurrentRegistry().provideLayer(Registry.live)

            def inc(tags: Array[String]): zio.Task[Unit] =
              inc(1.0, tags)

            def inc(amount: Double, tags: Array[String]): Task[Unit] =
              minst._1.inc(amount, tags)

            def time(f: () => Unit, tags: Array[String]): Task[Double] =
              minst._2.time(f, tags)
          }
      )
  }

  import io.prometheus.client.{ Counter => PCounter, Histogram => PHistogram }
  val c: Counter = Counter(
    PCounter
      .build()
      .name("PrometheusCounter")
      .labelNames(Array("class", "method"): _*)
      .help(s"Sample prometheus counter")
      .register()
  )
  val h: Histogram = Histogram(
    PHistogram
      .build()
      .name("PrometheusHistogram")
      .labelNames(Array("class", "method"): _*)
      .help(s"Sample prometheus histogram")
      .register()
  )

  val rLayer: Layer[Nothing, Metrics] = Metrics.receiver(c, h)
  val rtReceiver: Runtime.Scoped[Metrics with Exporters] =
    Runtime.unsafeFromLayer(rLayer ++ Exporters.live)

  /*val chHas: ULayer[Has[(Counter, Histogram)]] = ZLayer.succeed[(Counter, Histogram)]((c, h))
  val rLayerHas: ZLayer[Any, Nothing, Metrics] = chHas >>> Metrics.receiverHas
  println(s"defining ReceiverHas RT: $rLayerHas")
  val combinedLayer: ZLayer[Any, Nothing, Metrics with Console] = rLayerHas
  println(s"combined: $combinedLayer")
  val rtReceiverHas = Runtime.unsafeFromLayer(combinedLayer)*/

  println("defining Test program")
  val exporterTest: RIO[
    Metrics with Exporters,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[Metrics]
      _  <- printLine("Exporters")
      r  <- m.get.getRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      _  <- m.get.inc(Array("RequestCounter", "get"))
      _  <- m.get.inc(Array("RequestCounter", "post"))
      _  <- m.get.inc(2.0, Array("LoginCounter", "login"))
      _  <- m.get.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- printLine(s)
    } yield hs

  val program = exporterTest flatMap (server => Console.printLine(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program.provideSomeLayer[Env](Metrics.live))
}
