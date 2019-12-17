package zio.metrics.dropwizard

import org.http4s.HttpRoutes

trait MetricsService {
  val service: MetricsService.Service[Nothing]
}

object MetricsService {
  trait Service[-R] {
    def serveMetrics: R => HttpRoutes[Server.HttpTask]
  }
}
