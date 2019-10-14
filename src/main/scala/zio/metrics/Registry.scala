package zio.metrics

import zio.UIO
import io.prometheus.client.CollectorRegistry
import com.codahale.metrics.MetricRegistry

trait Registry {
  val registry: Registry.Service
}

object Registry {
  trait Service {
    def build(): UIO[_]
  }
}

trait PrometheusRegistry extends Registry {
  val registry = new Registry.Service {
    override def build(): UIO[CollectorRegistry] = UIO.effectTotal(CollectorRegistry.defaultRegistry)
  }
}

trait DropWizardRegistry extends Registry {
  val registry = new Registry.Service {
    def build(): UIO[MetricRegistry] = UIO.effectTotal(new MetricRegistry())
  }
}
