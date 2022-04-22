package zio.metrics.dropwizard

import zio.RIO
import zio.metrics.Label
import zio.metrics.dropwizard.reporters._
import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
import com.codahale.metrics.{ Histogram => DWHistogram, Meter => DWMeter }
import com.codahale.metrics.{ Timer => DWTimer }
import com.codahale.metrics.{ MetricRegistry, Reservoir, UniformReservoir }
import java.util.concurrent.TimeUnit

package object helpers {

  def getCurrentRegistry(): RIO[Registry, MetricRegistry] =
    RIO.serviceWithZIO(_.getCurrent())

  def registerCounter(name: String, labels: Array[String]): RIO[Registry, DWCounter] =
    RIO.serviceWithZIO(_.registerCounter(Label(name, labels, s"$name counter")))

  def registerGauge[A](name: String, labels: Array[String], f: () => A): RIO[Registry, DWGauge[A]] =
    RIO.serviceWithZIO(_.registerGauge[String, A](Label(name, labels, s"$name gauge"), f))

  def registerTimer(name: String, labels: Array[String]): RIO[Registry, DWTimer] =
    RIO.serviceWithZIO(_.registerTimer(Label(name, labels, s"$name timer")))

  def registerMeter(name: String, labels: Array[String]): RIO[Registry, DWMeter] =
    RIO.serviceWithZIO(_.registerMeter(Label(name, labels, s"$name meter")))

  def registerHistogram(
    name: String,
    labels: Array[String],
    reservoir: Reservoir
  ): RIO[Registry, DWHistogram] =
    RIO.serviceWithZIO(_.registerHistogram(Label(name, labels, s"$name histogram"), reservoir))

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

  def jmx(r: MetricRegistry): RIO[Reporters, Unit] =
    RIO.serviceWithZIO(
      dwr =>
        for {
          cr <- dwr.jmx(r)
        } yield cr.start()
    )

  def console(r: MetricRegistry, duration: Long, unit: TimeUnit): RIO[Reporters, Unit] =
    RIO.serviceWithZIO(
      dwr =>
        for {
          cr <- dwr.console(r)
        } yield cr.start(duration, unit)
    )

}
