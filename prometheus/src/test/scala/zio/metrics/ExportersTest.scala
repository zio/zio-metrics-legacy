package zio.metrics

import zio.{ /*RIO,*/ Runtime }
//import io.prometheus.client.{ CollectorRegistry }
import zio.internal.PlatformLive
import zio.metrics.prometheus._

object ExportersTest {

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge with PrometheusHistogram
        with PrometheusSummary with PrometheusExporters,
    PlatformLive.Default
  )

  val tests = {}

}
