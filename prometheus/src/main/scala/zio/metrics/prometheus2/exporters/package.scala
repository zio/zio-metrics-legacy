package zio.metrics.prometheus2

import io.prometheus.{ client => jp }

import zio._
import zio.Duration
import java.net.InetSocketAddress

package object exporters {

  type Exporters = Exporters.Service

  object Exporters {
    trait Service {
      def http(port: Int): RIO[Scope, jp.exporter.HTTPServer]
      def graphite(host: String, port: Int, interval: Duration): RIO[Scope, Unit]
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
      ZLayer.fromZIO {
        ZIO
          .service[Registry]
          .map { registry =>
            new Service {
              def http(port: Int): RIO[Scope, jp.exporter.HTTPServer] =
                for {
                  r <- registry.collectorRegistry
                  server <- ZIO.acquireRelease(ZIO.attempt(new jp.exporter.HTTPServer(new InetSocketAddress(port), r)))(
                             server => ZIO.succeed(server.close())
                           )
                } yield server

              def graphite(host: String, port: Int, interval: Duration): RIO[Scope, Unit] =
                for {
                  g    <- ZIO.attempt(new jp.bridge.Graphite(host, port))
                  stop <- Ref.make(false)
                  _ <- ZIO.acquireRelease(
                        registry.collectorRegistry
                          .flatMap(r => ZIO.attempt(g.push(r)))
                          .repeatOrElse(
                            Schedule.fixed(interval) *> Schedule.recurUntilZIO((_: Unit) => stop.get),
                            (_, _: Option[Unit]) => ZIO.unit
                          )
                          .fork
                      )((fiber: Fiber.Runtime[Nothing, Unit]) => stop.set(true) *> fiber.join)
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
      }
  }

  def http(port: Int): ZIO[Scope with Exporters, Throwable, jp.exporter.HTTPServer] =
    ZIO.serviceWithZIO[Exporters](_.http(port))

  def graphite(host: String, port: Int, interval: Duration): ZIO[Scope with Exporters, Throwable, Unit] =
    ZIO.serviceWithZIO[Exporters](_.graphite(host, port, interval))

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
