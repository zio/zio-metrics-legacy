package zio.metrics.dropwizard

import argonaut.Argonaut.jSingleObject
import argonaut.Json
import org.http4s.argonaut._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.{ HttpRoutes, Response }
import zio.RIO
import zio.interop.catz._
import zio.metrics.dropwizard.typeclasses.Monoid.jsonMonoid
import zio.metrics.dropwizard.DropwizardExtractor._

trait DropwizardMetricsService extends MetricsService {
  val service = new MetricsService.Service[DropwizardRegistry] {
    override def serveMetrics: DropwizardRegistry => HttpRoutes[Server.HttpTask] = { registry =>
      HttpRoutes.of[Server.HttpTask] {
        case GET -> Root / filter => {
          val optFilter = if (filter == "ALL") None else Some(filter)
          RegistryPrinter.report[DropwizardRegistry, List, Json](registry, optFilter)(jSingleObject) >>=
            (m => RIO(Response[Server.HttpTask](Ok).withEntity(m)))
        }
      }
    }
  }
}

object DropwizardMetricsService extends DropwizardMetricsService
