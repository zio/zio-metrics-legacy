package zio.metrics.http

import cats.data.Kleisli
import org.http4s.server.blaze._
import org.http4s.{ Request, Response }
import zio.metrics.Metrics
import zio.{ Task, TaskR, ZIO }
//import zio.scheduler.Scheduler
import zio.interop.catz._
import scala.util.Properties.envOrNone
import zio.system.System
import zio.clock.Clock
import zio.console.Console
import zio.random.Random
import zio.blocking.Blocking

object Server {
  val port: Int = envOrNone("HTTP_PORT").fold(9090)(_.toInt)

  type HttpEnvironment = Clock with Console with System with Random with Blocking
  type HttpTask[A]     = TaskR[HttpEnvironment, A]

  type KleisliApp = Kleisli[HttpTask, Request[HttpTask], Response[HttpTask]]

  type HttpApp[Ctx] = Metrics[Task[?], Ctx] => KleisliApp

  def builder[Ctx]: KleisliApp => HttpTask[Unit] =
    (app: Kleisli[HttpTask, Request[HttpTask], Response[HttpTask]]) =>
      ZIO
        .runtime[HttpEnvironment]
        .flatMap { implicit rts =>
          BlazeServerBuilder[HttpTask]
            .bindHttp(port)
            .withHttpApp(app)
            .serve
            .compile
            .drain
        }
}
