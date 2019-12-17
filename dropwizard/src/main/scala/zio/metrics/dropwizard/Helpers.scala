package zio.metrics.dropwizard.helpers

import zio.RIO
import zio.metrics.Label
import zio.metrics.dropwizard._
import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
import com.codahale.metrics.{ Histogram => DWHistogram, Meter => DWMeter }
import com.codahale.metrics.{ Timer => DWTimer }
import com.codahale.metrics.{ MetricRegistry, Reservoir, UniformReservoir }
import java.util.concurrent.TimeUnit

object registry {
  def getCurrent(): RIO[DropwizardRegistry, MetricRegistry] =
    RIO.accessM(_.registry.getCurrent())

  def registerCounter(name: String, labels: Array[String]): RIO[DropwizardRegistry, DWCounter] =
    RIO.accessM(_.registry.registerCounter(Label(name, labels)))

  def registerGauge[A](name: String, labels: Array[String], f: () => A): RIO[DropwizardRegistry, DWGauge[A]] =
    RIO.accessM(_.registry.registerGauge[String, A](Label(name, labels), f))

  def registerTimer(name: String, labels: Array[String]): RIO[DropwizardRegistry, DWTimer] =
    RIO.accessM(_.registry.registerTimer(Label(name, labels)))

  def registerMeter(name: String, labels: Array[String]): RIO[DropwizardRegistry, DWMeter] =
    RIO.accessM(_.registry.registerMeter(Label(name, labels)))

  def registerHistogram(
    name: String,
    labels: Array[String],
    reservoir: Reservoir
  ): RIO[DropwizardRegistry, DWHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, labels), reservoir))
}

object counter {
  def register(name: String) = Counter(name, Array.empty[String])

  def register(name: String, labels: Array[String]) =
    Counter(name, labels)
}

object gauge {
  def register[A](name: String, f: () => A) =
    Gauge(name, Array.empty[String], f)

  def register[A](name: String, labels: Array[String], f: () => A) =
    Gauge(name, labels, f)
}

object timer {
  def register(name: String) = Timer(name, Array.empty[String])

  def register(name: String, labels: Array[String]) =
    Timer(name, labels)
}

object meter {
  def register(name: String) = Meter(name, Array.empty[String])

  def register(name: String, labels: Array[String]) =
    Meter(name, labels)
}

object histogram {
  def register(name: String) =
    Histogram(name, Array.empty[String], new UniformReservoir)

  def register(name: String, labels: Array[String]) =
    Histogram(name, labels, new UniformReservoir)

  def register(name: String, labels: Array[String], reservoir: Reservoir) =
    Histogram(name, labels, reservoir)
}

object reporters {
  def jmx(r: MetricRegistry): RIO[DropwizardReporters, Unit] =
    RIO.accessM(
      dwr =>
        for {
          cr <- dwr.reporter.jmx(r)
        } yield cr.start()
    )

  def console(r: MetricRegistry, duration: Long, unit: TimeUnit): RIO[DropwizardReporters, Unit] =
    RIO.accessM(
      dwr =>
        for {
          cr <- dwr.reporter.console(r)
        } yield cr.start(duration, unit)
    )
}
