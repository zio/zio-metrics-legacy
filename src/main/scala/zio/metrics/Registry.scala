package zio.metrics

import zio.Task
import io.prometheus.client.CollectorRegistry
import com.codahale.metrics.MetricRegistry

trait Registry {
  val registry: Registry.Service[Any]
}

object Registry {
  trait Service[+R] {
    def build(): Task[R]
  }
}

trait PrometheusRegistry extends Registry {
  val registry = new Registry.Service[CollectorRegistry] {
    override def build(): Task[CollectorRegistry] = {
      Task(CollectorRegistry.defaultRegistry)
    }
  }
}

object PrometheusRegistry extends PrometheusRegistry

trait DropWizardRegistry extends Registry {
  val registry = new Registry.Service[MetricRegistry] {
    def build(): Task[MetricRegistry] = {
      Task(new MetricRegistry())
    }
  }
}

object DropWizardRegistry extends DropWizardRegistry
