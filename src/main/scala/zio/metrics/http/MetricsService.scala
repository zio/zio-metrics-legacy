package zio.metrics.http

import argonaut.Argonaut.jSingleObject
import argonaut.Json
import org.http4s.argonaut._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.{ HttpRoutes, Response }
import zio.metrics.Metrics
import scalaz.std.list.listInstance
import zio.{ Task, TaskR }
import zio.interop.catz._
import zio.metrics.ReportPrinter
import zio.metrics.http.Server._

trait MetricsService[M <: Metrics[Task[?], Ctx], Ctx] {
  def service: M => HttpRoutes[HttpTask]
}

object MetricsService {

  import com.codahale.metrics.Timer.Context
  import zio.metrics.DropwizardMetrics
  import zio.metrics.DropwizardReporters.jsonDWReporter
  implicit val dropwizardMetricsService = new MetricsService[DropwizardMetrics, Context] {

    def service: DropwizardMetrics => HttpRoutes[HttpTask] =
      (metrics: DropwizardMetrics) =>
        HttpRoutes.of[HttpTask] {
          case GET -> Root / filter => {
            val optFilter = if (filter == "ALL") None else Some(filter)
            val m: Json   = ReportPrinter[Context, DropwizardMetrics].report(metrics, optFilter)(jSingleObject)
            TaskR(Response[HttpTask](Ok).withEntity(m))
          }
        }
  }

  import io.prometheus.client.Summary
  import io.prometheus.client.Summary.Timer
  import zio.metrics.PrometheusMetrics
  import zio.metrics.PrometheusReporters.jsonPrometheusReporter
  implicit val prometheusMetricsService = new MetricsService[PrometheusMetrics, Timer] {

    def service: PrometheusMetrics => HttpRoutes[HttpTask] =
      (metrics: PrometheusMetrics) =>
        HttpRoutes.of[HttpTask] {
          case GET -> Root / filter =>
            val optFilter = if (filter == "ALL") None else Some(filter)
            val m: Json   = ReportPrinter[Summary.Timer, PrometheusMetrics].report(metrics, optFilter)(jSingleObject)
            println(m)
            TaskR(Response[HttpTask](Ok).withEntity(m))
        }
  }

}
