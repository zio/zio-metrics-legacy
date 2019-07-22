package zio.metrics.http

import java.io.File
import java.util.concurrent.Executors

import org.http4s._
import org.http4s.dsl.io._
import zio.clock.Clock
import zio.interop.catz._
import zio.metrics.http.Server._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutorService }

object StaticService {
  val blockingEc: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  implicit val clock: Clock = Clock.Live

  val service: HttpRoutes[HttpTask] = HttpRoutes.of[HttpTask] {
    case request @ GET -> Root / name =>
      StaticFile
        .fromFile(new File(s"dist/$name"), blockingEc, Some(request))
        .getOrElse(Response.notFound)
  }
}
