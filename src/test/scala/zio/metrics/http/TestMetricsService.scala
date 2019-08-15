package zio.metrics.http

import argonaut.Argonaut._
import org.http4s.argonaut._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.{ HttpRoutes, Response }
import zio.metrics._
import zio.interop.catz._
import zio.{ Task, TaskR, ZIO }
import Server._

import scala.math.Numeric.IntIsIntegral

object TestMetricsService {
  println("Serving")

  val s: Stream[Int] = Stream.from(1)
  val tester: Option[Int] => Int =
    (opt: Option[Int]) => opt.map(i => s.takeWhile(_ < i).fold(1)((i, j) => i * j)).get

  //def performTests[Ctx](metrics: Metrics[Task[?], Ctx]): Task[String] =
  def performTests[Ctx](metrics: Metrics[Task[?], Ctx]): HttpTask[String] =
    for {
      f  <- metrics.counter(Label("simple_counter", Array("test", "counter")))
      _  <- f(1)
      _  <- f(2)
      g  <- metrics.gauge(Label("simple_gauge", Array("test", "gauge")))(tester)
      _  <- g(10.some)
      t  <- metrics.timer(Label("simple_timer", Array("test", "timer")))
      t1 = t.start
      l <- ZIO.foreachPar(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(t1))
      h <- metrics.histogram(Label("simple_histogram", Array("test", "histogram")))
      _ <- ZIO.foreach(List(h(10), h(25), h(50), h(57), h(19)))(_.unit)
      m <- metrics.meter(Label("simple_meter", Array("test", "meter")))
      _ <- ZIO.foreach(List(1.0, 2.0, 3.0, 4.0, 5.0))(d => m(d))
    } yield { s"time $l ns" }

  def service[Ctx] =
    (metrics: Metrics[Task[?], Ctx]) =>
      HttpRoutes.of[HttpTask] {
        case GET -> Root =>
          val m = performTests(metrics).fold(
            t => jSingleObject("error", jString(s"failure encountered $t")),
            s => jSingleObject("time", jString(s))
          )
          m.flatMap(j => TaskR(Response[HttpTask](Ok).withEntity(j)))
      }
}
