package zio.metrics.prometheus

import zio.RIO

import io.prometheus.client.{ Counter => PCounter, Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram, Summary => PSummary }

object counter {
  def inc(pCounter: PCounter): RIO[PrometheusCounter, Unit] = RIO.accessM(_.counter.inc(pCounter, Array.empty[String]))

  def inc(pCounter: PCounter, amount: Double): RIO[PrometheusCounter, Unit] =
    RIO.accessM(_.counter.inc(pCounter, amount, Array.empty[String]))

  def inc(pCounter: PCounter, labelNames: Array[String]): RIO[PrometheusCounter, Unit] =
    RIO.accessM(_.counter.inc(pCounter, labelNames))

  def inc(pCounter: PCounter, amount: Double, labelNames: Array[String]): RIO[PrometheusCounter, Unit] =
    RIO.accessM(_.counter.inc(pCounter, amount, labelNames))
}

object gauge {
  def getValue(g: PGauge): RIO[PrometheusGauge, Double] =
    RIO.accessM(_.gauge.getValue(g, Array.empty[String]))

  def inc(g: PGauge): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, Array.empty[String]))

  def dec(g: PGauge): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.dec(g, Array.empty[String]))

  def inc(g: PGauge, amount: Double): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount, Array.empty[String]))

  def dec(g: PGauge, amount: Double): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount, Array.empty[String]))

  def set(g: PGauge, amount: Double): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount, Array.empty[String]))

  def setToCurrentTime(g: PGauge): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.setToCurrentTime(g, Array.empty[String]))

  def setToTime(g: PGauge, f: () => Unit): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.setToTime(g, f, Array.empty[String]))

  def getValue(g: PGauge, labelNames: Array[String]): RIO[PrometheusGauge, Double] =
    RIO.accessM(_.gauge.getValue(g, labelNames))

  def inc(g: PGauge, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, labelNames))

  def dec(g: PGauge, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.dec(g, labelNames))

  def inc(g: PGauge, amount: Double, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount, labelNames))

  def dec(g: PGauge, amount: Double, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount, labelNames))

  def set(g: PGauge, amount: Double, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount, labelNames))

  def setToCurrentTime(g: PGauge, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.setToCurrentTime(g, labelNames))

  def setToTime(g: PGauge, labelNames: Array[String]): RIO[PrometheusGauge, Unit] =
   RIO.accessM(_.gauge.setToTime(g, () => Thread.sleep(1000), labelNames))
}

object histogram {
  def observe(h: PHistogram, amount: Double): RIO[PrometheusHistogram, Unit] =
    RIO.accessM(_.histogram.observe(h, amount))

  def startTimer(h: PHistogram): RIO[PrometheusHistogram, PHistogram.Timer] =
    RIO.accessM(_.histogram.startTimer(h))

  def observeDuration(timer: PHistogram.Timer): RIO[PrometheusHistogram, Double] =
    RIO.accessM(_.histogram.observeDuration(timer))

  def time(h: PHistogram, f: () => Unit): RIO[PrometheusHistogram, Double] =
    RIO.accessM(_.histogram.time(h, f))
}

object summary {
  def observe(s: PSummary, amount: Double): RIO[PrometheusSummary, Unit] =
    RIO.accessM(_.summary.observe(s, amount))

  def startTimer(s: PSummary): RIO[PrometheusSummary, PSummary.Timer] =
    RIO.accessM(_.summary.startTimer(s))

  def observeDuration(timer: PSummary.Timer): RIO[PrometheusSummary, Double] =
    RIO.accessM(_.summary.observeDuration(timer))

  def time(s: PSummary, f: () => Unit): RIO[PrometheusSummary, Double] =
    RIO.accessM(_.summary.time(s, f))
}
