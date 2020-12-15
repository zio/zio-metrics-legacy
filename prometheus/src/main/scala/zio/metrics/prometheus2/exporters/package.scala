package zio.metrics.prometheus2

import io.prometheus.{ client => jp }

import zio._
import zio.clock.Clock
import zio.duration.Duration
import java.net.InetSocketAddress

package object exporters {

  type Exporters = Has[Exporters.Service]

  object Exporters {
    trait Service {
      def http(port: Int): TaskManaged[jp.exporter.HTTPServer]
      def graphite(host: String, port: Int, interval: Duration): RManaged[Clock, Unit]
      def pushGateway(
        host: String,
        port: Int,
        jobName: String,
        user: Option[String],
        password: Option[String],
        httpConnectionFactory: Option[jp.exporter.HttpConnectionFactory]
      ): Task[Unit]
    }

    def live: URLayer[Registry, Exporters] = ZLayer.fromService { (registry: Registry.Service) =>
      new Service {
        def http(port: Int): TaskManaged[jp.exporter.HTTPServer] =
          for {
            r <- registry.collectorRegistry.toManaged_
            server <- ZIO
                       .effect(
                         new jp.exporter.HTTPServer(new InetSocketAddress(port), r)
                       )
                       .toManaged(server => ZIO.effectTotal(server.stop()))
          } yield server

        def graphite(host: String, port: Int, interval: Duration): RManaged[Clock, Unit] =
          for {
            g    <- ZIO.effect(new jp.bridge.Graphite(host, port)).toManaged_
            stop <- Ref.make(false).toManaged_
            _ <- registry.collectorRegistry
                  .flatMap(r => ZIO.effect(g.push(r)))
                  .repeatOrElse(
                    Schedule.fixed(interval) *> Schedule.recurUntilM((_: Unit) => stop.get),
                    (_, _: Option[Unit]) => ZIO.unit
                  )
                  .fork
                  .toManaged(fiber => stop.set(true) *> fiber.join)
          } yield ()

        def pushGateway(
          host: String,
          port: Int,
          jobName: String,
          user: Option[String],
          password: Option[String],
          httpConnectionFactory: Option[jp.exporter.HttpConnectionFactory]
        ): Task[Unit] = registry.collectorRegistry >>= { r =>
          ZIO.effect {
            val pg = new jp.exporter.PushGateway(s"$host:$port")
            (
              for {
                u <- user
                p <- password
              } yield new jp.exporter.BasicAuthHttpConnectionFactory(u, p)
            ).orElse(httpConnectionFactory).foreach(pg.setConnectionFactory)
            pg.pushAdd(r, jobName)
          }
        }
      }
    }
  }

  def http(port: Int): RManaged[Exporters, jp.exporter.HTTPServer] = ZManaged.accessManaged(_.get.http(port))
  def graphite(host: String, port: Int, interval: Duration): RManaged[Exporters with Clock, Unit] =
    ZManaged.accessManaged(_.get.graphite(host, port, interval))
  def pushGateway(
    host: String,
    port: Int,
    jobName: String,
    user: Option[String],
    password: Option[String],
    httpConnectionFactory: Option[jp.exporter.HttpConnectionFactory]
  ): RIO[Exporters, Unit] = ZIO.accessM(_.get.pushGateway(host, port, jobName, user, password, httpConnectionFactory))

}
