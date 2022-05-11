package zio.metrics.prometheus

import io.prometheus.client.Summary.{ Child => SChild }
import io.prometheus.client.Histogram.{ Child => HChild }
import io.prometheus.client.Gauge.{ Child => GChild }
import io.prometheus.client.Counter.{ Child => CChild }
import zio.{ RIO, Task, ZIO }
import zio.metrics.prometheus.Registry.{ Percentile, Tolerance }
import io.prometheus.client.{ Counter => PCounter, Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram, Summary => PSummary }
import zio.metrics.prometheus.helpers._

sealed trait Metric {}

final case class Counter(private val pCounter: PCounter) extends Metric {
  def inc(): Task[Unit] = inc(Array.empty[String])

  def inc(amount: Double): Task[Unit] = inc(amount, Array.empty[String])

  def inc(labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pCounter.inc() else pCounter.labels(labelNames: _*).inc())

  def inc(amount: Double, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pCounter.inc(amount) else pCounter.labels(labelNames: _*).inc(amount))

  def labels(labelNames: Array[String]): CounterChild = CounterChild(pCounter.labels(labelNames: _*))

}

final case class CounterChild(private val pCounter: CChild) extends Metric {
  def inc(): Task[Unit] = ZIO.succeed(pCounter.inc())

  def inc(amount: Double): Task[Unit] = ZIO.succeed(pCounter.inc(amount))
}

object Counter {
  def apply(name: String, labels: Array[String]): RIO[Registry, Counter] =
    for {
      c <- registerCounter(name, labels)
    } yield new Counter(c)
  def apply(name: String, labels: Array[String], help: String): RIO[Registry, Counter] =
    for {
      c <- registerCounter(name, labels, help)
    } yield new Counter(c)
}

final case class Gauge(private val pGauge: PGauge) extends Metric {
  def getValue(): Task[Double] =
    getValue(Array.empty[String])

  def getValue(labelNames: Array[String]): Task[Double] =
    ZIO.succeed(if (labelNames.isEmpty) pGauge.get() else pGauge.labels(labelNames: _*).get())

  def inc(): Task[Unit] =
    inc(Array.empty[String])

  def inc(labelNames: Array[String]): Task[Unit] =
    ZIO.succeed {
      if (labelNames.isEmpty) pGauge.inc()
      else pGauge.labels(labelNames: _*).inc()
    }

  def dec(): Task[Unit] =
    dec(Array.empty[String])

  def dec(labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pGauge.dec() else pGauge.labels(labelNames: _*).dec())

  def inc(amount: Double): Task[Unit] =
    inc(amount, Array.empty[String])

  def inc(amount: Double, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pGauge.inc(amount) else pGauge.labels(labelNames: _*).inc(amount))

  def dec(amount: Double): Task[Unit] =
    dec(amount, Array.empty[String])

  def dec(amount: Double, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pGauge.dec(amount) else pGauge.labels(labelNames: _*).dec(amount))

  def set(amount: Double): Task[Unit] =
    set(amount, Array.empty[String])

  def set(amount: Double, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pGauge.set(amount) else pGauge.labels(labelNames: _*).set(amount))

  def setToCurrentTime(): Task[Unit] =
    setToCurrentTime(Array.empty[String])

  def setToCurrentTime(labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(
      if (labelNames.isEmpty) pGauge.setToCurrentTime() else pGauge.labels(labelNames: _*).setToCurrentTime()
    )

  def setToTime(f: () => Unit): Task[Unit] =
    ZIO.succeed {
      val t = pGauge.startTimer()
      f()
      pGauge.set(t.setDuration())
    }

  def setToTime(f: () => Unit, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed {
      val t = if (labelNames.isEmpty) pGauge.startTimer() else pGauge.labels(labelNames: _*).startTimer()
      f()
      pGauge.set(t.setDuration())
    }

  def labels(labelNames: Array[String]): GaugeChild = GaugeChild(pGauge.labels(labelNames: _*))
}

final case class GaugeChild(private val pGauge: GChild) extends Metric {

  def getValue(): Task[Double] = ZIO.succeed(pGauge.get())

  def inc(): Task[Unit] = ZIO.succeed(pGauge.inc())

  def dec(): Task[Unit] = ZIO.succeed(pGauge.dec())

  def inc(amount: Double): Task[Unit] = ZIO.succeed(pGauge.inc(amount))

  def dec(amount: Double): Task[Unit] = ZIO.succeed(pGauge.dec(amount))

  def set(amount: Double): Task[Unit] = ZIO.succeed(pGauge.set(amount))

  def setToCurrentTime(): Task[Unit] = ZIO.succeed(pGauge.setToCurrentTime())

  def setToTime(f: () => Unit): Task[Unit] =
    ZIO.succeed {
      val t = pGauge.startTimer()
      f()
      pGauge.set(t.setDuration())
    }

}

object Gauge {
  def apply(name: String, labels: Array[String]): RIO[Registry, Gauge] =
    for {
      g <- registerGauge(name, labels)
    } yield new Gauge(g)

  def apply(name: String, labels: Array[String], help: String): RIO[Registry, Gauge] =
    for {
      g <- registerGauge(name, labels, help)
    } yield new Gauge(g)
}

final case class Histogram(private val pHistogram: PHistogram) extends Metric {
  type HistogramTimer = PHistogram.Timer

  def observe(amount: Double): Task[Unit] =
    observe(amount, Array.empty[String])

  def observe(amount: Double, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(
      if (labelNames.isEmpty) pHistogram.observe(amount) else pHistogram.labels(labelNames: _*).observe(amount)
    )

  def startTimer(): Task[HistogramTimer] =
    startTimer(Array.empty[String])

  def startTimer(labelNames: Array[String]): Task[HistogramTimer] =
    ZIO.succeed(if (labelNames.isEmpty) pHistogram.startTimer() else pHistogram.labels(labelNames: _*).startTimer())

  def observeDuration(timer: HistogramTimer): Task[Double] =
    ZIO.succeed(timer.observeDuration())

  def time(f: () => Unit): Task[Double] =
    time(f, Array.empty[String])

  def time(f: () => Unit, labelNames: Array[String]): Task[Double] =
    ZIO.succeed {
      val t = if (labelNames.isEmpty) pHistogram.startTimer() else pHistogram.labels(labelNames: _*).startTimer()
      f()
      t.observeDuration()
    }

  def time[R, A](task: RIO[R, A]): RIO[R, (Double, A)] =
    time(task, Array.empty[String])

  def time[R, A](task: RIO[R, A], labelNames: Array[String]): RIO[R, (Double, A)] = {
    val t = if (labelNames.isEmpty) pHistogram.startTimer() else pHistogram.labels(labelNames: _*).startTimer()
    task flatMap (a => ZIO.succeed((t.observeDuration(), a)))
  }

  def time_[R, A](task: RIO[R, A]): RIO[R, A] =
    time_(task, Array.empty[String])

  def time_[R, A](task: RIO[R, A], labelNames: Array[String]): RIO[R, A] =
    ZIO.acquireReleaseWith(
      ZIO
        .succeed(if (labelNames.isEmpty) pHistogram.startTimer() else pHistogram.labels(labelNames: _*).startTimer())
    )(t => ZIO.succeed(t.close()))(_ => task)

  def labels(labelNames: Array[String]): HistogramChild = HistogramChild(pHistogram.labels(labelNames: _*))
}

final case class HistogramChild(private val pHistogram: HChild) extends Metric {
  type HistogramTimer = PHistogram.Timer

  def observe(amount: Double): Task[Unit] =
    ZIO.succeed(pHistogram.observe(amount))

  def startTimer(): Task[HistogramTimer] =
    ZIO.succeed(pHistogram.startTimer())

  def observeDuration(timer: HistogramTimer): Task[Double] =
    ZIO.succeed(timer.observeDuration())

  def time(f: () => Unit): Task[Double] =
    ZIO.succeed {
      val t = pHistogram.startTimer()
      f()
      t.observeDuration()
    }

  def time[R, A](task: RIO[R, A]): RIO[R, (Double, A)] = {
    val t = pHistogram.startTimer()
    task flatMap (a => ZIO.succeed((t.observeDuration(), a)))
  }

  def time_[R, A](task: RIO[R, A]): RIO[R, A] =
    ZIO.acquireReleaseWith(ZIO.succeed(pHistogram.startTimer()))(t => ZIO.succeed(t.close()))(_ => task)
}

object Histogram {
  def apply(name: String, labels: Array[String], buckets: Buckets): RIO[Registry, Histogram] =
    for {
      h <- registerHistogram(name, labels, buckets)
    } yield new Histogram(h)

  def apply(name: String, labels: Array[String], buckets: Buckets, help: String): RIO[Registry, Histogram] =
    for {
      h <- registerHistogram(name, labels, buckets, help)
    } yield new Histogram(h)
}

final case class Summary(private val pSummary: PSummary) extends Metric {
  type SummaryTimer = PSummary.Timer

  def observe(amount: Double): Task[Unit] =
    observe(amount, Array.empty[String])

  def observe(amount: Double, labelNames: Array[String]): Task[Unit] =
    ZIO.succeed(if (labelNames.isEmpty) pSummary.observe(amount) else pSummary.labels(labelNames: _*).observe(amount))

  def startTimer(): Task[SummaryTimer] =
    startTimer(Array.empty[String])

  def startTimer(labelNames: Array[String]): Task[SummaryTimer] =
    ZIO.succeed(if (labelNames.isEmpty) pSummary.startTimer() else pSummary.labels(labelNames: _*).startTimer)

  def observeDuration(timer: SummaryTimer): Task[Double] =
    ZIO.succeed(timer.observeDuration())

  def time(f: () => Unit): Task[Double] =
    time(f, Array.empty[String])

  def time(f: () => Unit, labelNames: Array[String]): Task[Double] =
    ZIO.succeed {
      val t = if (labelNames.isEmpty) pSummary.startTimer() else pSummary.labels(labelNames: _*).startTimer()
      f()
      t.observeDuration()
    }

  def time[R, A](task: RIO[R, A]): RIO[R, (Double, A)] =
    time(task, Array.empty[String])

  def time[R, A](task: RIO[R, A], labelNames: Array[String]): RIO[R, (Double, A)] = {
    val t = if (labelNames.isEmpty) pSummary.startTimer() else pSummary.labels(labelNames: _*).startTimer()
    task flatMap (a => ZIO.succeed((t.observeDuration(), a)))
  }

  def time_[R, A](task: RIO[R, A]): RIO[R, A] =
    time_(task, Array.empty[String])

  def time_[R, A](task: RIO[R, A], labelNames: Array[String]): RIO[R, A] =
    ZIO.acquireReleaseWith(
      ZIO.attempt(if (labelNames.isEmpty) pSummary.startTimer() else pSummary.labels(labelNames: _*).startTimer())
    )(t => ZIO.succeed(t.close()))(_ => task)

  def labels(labelNames: Array[String]): SummaryChild = SummaryChild(pSummary.labels(labelNames: _*))
}

final case class SummaryChild(private val pSummary: SChild) extends Metric {
  type SummaryTimer = PSummary.Timer

  def observe(amount: Double): Task[Unit] =
    ZIO.succeed(pSummary.observe(amount))

  def startTimer(): Task[SummaryTimer] =
    ZIO.succeed(pSummary.startTimer())

  def observeDuration(timer: SummaryTimer): Task[Double] =
    ZIO.succeed(timer.observeDuration())

  def time(f: () => Unit): Task[Double] =
    ZIO.succeed {
      val t = pSummary.startTimer()
      f()
      t.observeDuration()
    }

  def time[R, A](task: RIO[R, A]): RIO[R, (Double, A)] = {
    val t = pSummary.startTimer()
    task flatMap (a => ZIO.succeed((t.observeDuration(), a)))
  }

  def time_[R, A](task: RIO[R, A]): RIO[R, A] =
    ZIO.acquireReleaseWith(ZIO.attempt(pSummary.startTimer()))(t => ZIO.succeed(t.close()))(_ => task)

}

object Summary {
  def apply(
    name: String,
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)]
  ): RIO[Registry, Summary] =
    for {
      s <- registerSummary(name, labels, percentiles)
    } yield new Summary(s)

  def apply(
    name: String,
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)],
    help: String
  ): RIO[Registry, Summary] =
    for {
      s <- registerSummary(name, labels, percentiles, help)
    } yield new Summary(s)
}
