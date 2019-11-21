package zio.metrics.dropwizard

import zio.metrics.MetricsService
import zio.metrics.Server
import argonaut.Argonaut.jSingleObject
import argonaut.Json
import org.http4s.argonaut._
import org.http4s.dsl.impl.Root
import org.http4s.dsl.io._
import org.http4s.{ HttpRoutes, Response }
import zio.RIO
import zio.interop.catz._
import zio.metrics.RegistryPrinter
import zio.metrics.instances.jsonMonoid
import cats.instances.list._
import zio.metrics.dropwizard.DropWizardExtractor._

trait DropWizardMetricsService extends MetricsService {
  val service = new MetricsService.Service[DropWizardRegistry] {
    override def serveMetrics: DropWizardRegistry => HttpRoutes[Server.HttpTask] = {
     registry =>
        HttpRoutes.of[Server.HttpTask] {
          case GET -> Root / filter => {
            val optFilter = if (filter == "ALL") None else Some(filter)
            RegistryPrinter.report[DropWizardRegistry, List, Json](registry, optFilter)(jSingleObject) >>=
              (m => RIO(Response[Server.HttpTask](Ok).withEntity(m)))
          }
        }
    }
  }
}

object DropWizardMetricsService extends DropWizardMetricsService
