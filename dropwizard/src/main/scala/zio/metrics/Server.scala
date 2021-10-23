package zio.metrics.dropwizard

import scala.util.Properties.envOrNone

import cats.data.Kleisli
import cats.instances.list._
import com.codahale.metrics.MetricRegistry
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Response
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.blaze.server._
import zio.RIO
import zio.ZIO
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._
import zio.metrics.dropwizard.DropwizardExtractor._
import zio.metrics.dropwizard.typeclasses._
import zio.random.Random
import zio.system.System

object Server {
  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)

  type HttpEnvironment = Clock with Console with System with Random with Blocking
  type HttpTask[A]     = RIO[HttpEnvironment, A]

  type KleisliApp = Kleisli[HttpTask, Request[HttpTask], Response[HttpTask]]

  //type HttpApp[R <: Registry] = R => KleisliApp

  def builder[Ctx]: KleisliApp => HttpTask[Unit] =
    (app: KleisliApp) =>
      ZIO
        .runtime[HttpEnvironment]
        .flatMap { implicit rts =>
          BlazeServerBuilder[HttpTask](rts.platform.executor.asEC)
            .bindHttp(port)
            .withHttpApp(app)
            .serve
            .compile
            .drain
        }

  def serveMetrics: MetricRegistry => HttpRoutes[Server.HttpTask] =
    registry =>
      HttpRoutes.of[Server.HttpTask] {
        case GET -> Root / filter => {
          val optFilter = if (filter == "ALL") None else Some(filter)
          RegistryPrinter
            .report[List, Json](registry, optFilter)(
              (k: String, v: Json) => Json.obj((k, v))
            )
            .map(m => Response[Server.HttpTask](Ok).withEntity(m))
        }
      }
}
