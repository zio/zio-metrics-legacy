package zio.metrics.dropwizard

import zio.{ RIO, Task, ZIO }
import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
import com.codahale.metrics.{ Timer => DWTimer, Meter => DWMeter }
import com.codahale.metrics.{ Histogram => DWHistogram }
import com.codahale.metrics.Reservoir
import zio.metrics.dropwizard.helpers._

sealed trait Metric {}

class Counter(private val dwCounter: DWCounter) extends Metric {
  def inc(): Task[Unit] =
    ZIO.succeed(dwCounter.inc())

  def inc(amount: Double): Task[Unit] =
    ZIO.succeed(dwCounter.inc(amount.toLong))
}

object Counter {
  def apply(name: String, labels: Array[String]): RIO[Registry, Counter] =
    for {
      c <- registerCounter(name, labels)
    } yield new Counter(c)
}

class Gauge(private val dwGauge: DWGauge[_]) extends Metric {
  def getValue[A](): Task[A] =
    ZIO.succeed(dwGauge.getValue().asInstanceOf[A])
}

object Gauge {
  def apply[A](name: String, labels: Array[String], f: () => A): RIO[Registry, Gauge] =
    for {
      g <- registerGauge[A](name, labels, f)
    } yield new Gauge(g)
}

class Timer(private val dwTimer: DWTimer) extends Metric {
  def start(): zio.Task[DWTimer.Context] =
    ZIO.succeed(dwTimer.time())

  def stop(c: DWTimer.Context): Task[Long] =
    ZIO.succeed(c.stop())
}

object Timer {
  def apply(name: String, labels: Array[String]): RIO[Registry, Timer] =
    for {
      t <- registerTimer(name, labels)
    } yield new Timer(t)
}

class Meter(private val dwMeter: DWMeter) extends Metric {
  def mark(): zio.Task[Unit] =
    ZIO.succeed(dwMeter.mark())

  def mark(amount: Long): zio.Task[Unit] =
    ZIO.succeed(dwMeter.mark(amount))
}

object Meter {
  def apply(name: String, labels: Array[String]): RIO[Registry, Meter] =
    for {
      m <- registerMeter(name, labels)
    } yield new Meter(m)
}

class Histogram(private val dwHistogram: DWHistogram) extends Metric {
  def update(amount: Double): Task[Unit] =
    ZIO.succeed(dwHistogram.update(amount.toLong))
}

object Histogram {
  def apply(name: String, labels: Array[String], reservoir: Reservoir): RIO[Registry, Histogram] =
    for {
      h <- registerHistogram(name, labels, reservoir)
    } yield new Histogram(h)
}
