package zio.metrics.prometheus

import zio.RIO

import io.prometheus.client.{ Counter => PCounter, Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram, Summary => PSummary }

object counter {
  def inc(pCounter: PCounter): RIO[PrometheusCounter, Unit] = RIO.accessM(_.counter.inc(pCounter))

  def inc(pCounter: PCounter, amount: Double): RIO[PrometheusCounter, Unit] =
    RIO.accessM(_.counter.inc(pCounter, amount))
}

object gauge {
  def getValue(g: PGauge): RIO[PrometheusGauge, Double] =
    RIO.accessM(_.gauge.getValue(g))

  def inc(g: PGauge): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g))

  def dec(g: PGauge): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.dec(g))

  def inc(g: PGauge, amount: Double): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount))

  def dec(g: PGauge, amount: Double): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount))

  def set(g: PGauge, amount: Double): RIO[PrometheusGauge, Unit] =
    RIO.accessM(_.gauge.inc(g, amount))
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
