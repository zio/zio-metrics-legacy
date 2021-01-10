package zio.metrics

import zio.{Has, Layer, RIO, Runtime, Task, ZIO, ZLayer}
import zio.console.putStrLn
import zio.metrics.prometheus._
import io.prometheus.client.exporter.HTTPServer
import zio.console.Console
import zio.clock.Clock
import io.prometheus.client.CollectorRegistry
import zio.metrics.prometheus.LabelList.LNil
import zio.metrics.prometheus.exporters.Exporters

object MetricsLayer {

  type Metrics = Has[Metrics.Service]

  type Env = Registry with Exporters with Console

  val rt: Runtime.Managed[Registry with Exporters with Clock with Console] =
    Runtime.unsafeFromLayer(Registry.liveWithDefaultMetrics >+> Exporters.live ++ Clock.live ++ Console.live)

  object Metrics {
    trait Service {
      def getRegistry: RIO[Registry, CollectorRegistry]

      def inc(tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Unit]

      def inc(amount: Double, tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Unit]

      def time(f: () => Double, tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Double]
    }

    type CounterInstance   = Counter.Labelled[LabelList.LCons[LabelList.LCons[LNil]]]
    type HistogramInstance = Histogram.Labelled[LabelList.LCons[LabelList.LCons[LNil]]]

    val live: Layer[Nothing, Metrics] = ZLayer.succeed(new Service {

      private val (myCounter, myHistogram) = rt.unsafeRun(
        for {
          c <- Counter("myCounter", None, "name" :: "method" :: LNil)
          h <- Histogram("myHistogram", Buckets.Default, None, "name" :: "method" :: LNil)
        } yield (c, h)
      )

      def getRegistry: RIO[Registry, CollectorRegistry] = collectorRegistry

      def inc(tags: LabelList.LCons[LabelList.LCons[LNil]]): zio.Task[Unit] =
        inc(1.0, tags)

      def inc(amount: Double, tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Unit] =
        myCounter(tags).inc(amount)

      def time(f: () => Double, tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Double] =
        myHistogram(tags).observe_(f)
    })

    val receiver: ZLayer[Has[(CounterInstance, HistogramInstance)], Nothing, Metrics] =
      ZLayer.fromFunction[Has[(CounterInstance, HistogramInstance)], Metrics.Service](
        f = minst =>
          new Service {
            def getRegistry: RIO[Registry, CollectorRegistry] =
              collectorRegistry

            def inc(tags: LabelList.LCons[LabelList.LCons[LNil]]): zio.Task[Unit] =
              inc(1.0, tags)

            def inc(amount: Double, tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Unit] =
              minst.get._1(tags).inc(amount)

            def time(f: () => Double, tags: LabelList.LCons[LabelList.LCons[LNil]]): Task[Double] =
              minst.get._2(tags).observe_(f)
          }
      )
  }

  val counter = Counter("PrometheusCounter", Some("Sample prometheus counter"), "class" :: "method" :: LNil)

  val histogram =
    Histogram("PrometheusHistogram", Buckets.Default, Some("Sample prometheus histogram"), "class" :: "method" :: LNil)

  val rLayer: ZLayer[Registry with Clock, Throwable, Metrics] = {
    val tup = (for {
      c <- counter
      h <- histogram
    } yield (c, h)).toLayer
    tup >>> Metrics.receiver
  }

  val rtReceiver: Runtime.Managed[Registry with Clock with Metrics with Console] =
    Runtime.unsafeFromLayer(Registry.liveWithDefaultMetrics >+> Clock.live >+> rLayer ++ Console.live)

  /*val chHas: ULayer[Has[(Counter, Histogram)]] = ZLayer.succeed[(Counter, Histogram)]((c, h))
  val rLayerHas: ZLayer[Any, Nothing, Metrics] = chHas >>> Metrics.receiverHas
  println(s"defining ReceiverHas RT: $rLayerHas")
  val combinedLayer: ZLayer[Any, Nothing, Metrics with Console] = rLayerHas ++ Console.live
  println(s"combined: $combinedLayer")
  val rtReceiverHas = Runtime.unsafeFromLayer(combinedLayer)*/

  println("defining Test program")
  import zio.metrics.prometheus.exporters._
  val exporterTest: ZIO[Console with Registry with Metrics with Exporters, Throwable, HTTPServer] =
    http(9090).use(hs =>
      for {
        m  <- RIO.environment[Metrics]
        _  <- putStrLn("Exporters")
        _  <- m.get.inc("RequestCounter" :: "get" :: LNil)
        _  <- m.get.inc("RequestCounter" :: "post" :: LNil)
        _  <- m.get.inc(2.0, "LoginCounter" :: "login" :: LNil)
        _  <- m.get.time(() => {Thread.sleep(2000); 2.0},"histogram" :: "get" :: LNil)
        s  <- string004
        _  <- putStrLn(s)
      } yield hs
    )

  val program: ZIO[Console with Registry with Metrics with Exporters, Throwable, Unit] =
    exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program.provideSomeLayer[Env](Metrics.live))
}
