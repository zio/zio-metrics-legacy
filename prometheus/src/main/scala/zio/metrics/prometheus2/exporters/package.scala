package zio.metrics.prometheus2

import io.prometheus.{ client => jp }

import zio._
import zio.Clock
import zio.Duration
import java.net.InetSocketAddress

package object exporters {

  type Exporters = Exporters.Service

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

    def live: URLayer[Registry, Exporters] =
      ZIO
        .service[Registry]
        .map { registry =>
          new Service {
            def http(port: Int): TaskManaged[jp.exporter.HTTPServer] =
              for {
                r <- registry.collectorRegistry.toManaged
                server <- ZIO
                           .attempt(
                             new jp.exporter.HTTPServer(new InetSocketAddress(port), r)
                           )
                           .toManagedWith(server => ZIO.succeed(server.close()))
              } yield server

            def graphite(host: String, port: Int, interval: Duration): RManaged[Clock, Unit] =
              for {
                g    <- ZIO.attempt(new jp.bridge.Graphite(host, port)).toManaged
                stop <- Ref.make(false).toManaged
                _ <- registry.collectorRegistry
                      .flatMap(r => ZIO.attempt(g.push(r)))
                      .repeatOrElse(
                        Schedule.fixed(interval) *> Schedule.recurUntilZIO((_: Unit) => stop.get),
                        (_, _: Option[Unit]) => ZIO.unit
                      )
                      .fork
                      .toManagedWith((fiber: Fiber.Runtime[Nothing, Unit]) => stop.set(true) *> fiber.join)
              } yield ()

            def pushGateway(
              host: String,
              port: Int,
              jobName: String,
              user: Option[String],
              password: Option[String],
              httpConnectionFactory: Option[jp.exporter.HttpConnectionFactory]
            ): Task[Unit] = registry.collectorRegistry flatMap { r =>
              ZIO.attempt {
                val pg = new jp.exporter.PushGateway(s"$host:$port")
                val authHttpConnectionFactory = for {
                  u <- user
                  p <- password
                } yield new jp.exporter.BasicAuthHttpConnectionFactory(u, p)

                authHttpConnectionFactory
                  .orElse(httpConnectionFactory)
                  .foreach(pg.setConnectionFactory)
                pg.pushAdd(r, jobName)
              }
            }
          }
        }
        .toLayer
  }

  def http(port: Int): RManaged[Exporters, jp.exporter.HTTPServer] =
    ZManaged.environmentWithManaged(_.get[Exporters].http(port))

  def graphite(host: String, port: Int, interval: Duration): RManaged[Exporters with Clock, Unit] =
    ZManaged.environmentWithManaged(_.get[Exporters].graphite(host, port, interval))

  def pushGateway(
    host: String,
    port: Int,
    jobName: String,
    user: Option[String],
    password: Option[String],
    httpConnectionFactory: Option[jp.exporter.HttpConnectionFactory]
  ): RIO[Exporters, Unit] =
    ZIO.serviceWithZIO(_.pushGateway(host, port, jobName, user, password, httpConnectionFactory))
}
