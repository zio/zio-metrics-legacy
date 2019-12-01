package zio.metrics.prometheus

import zio.RIO

import io.prometheus.client.{ Counter => PCounter, Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram, Summary => PSummary }
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.exporter.HttpConnectionFactory
import zio.metrics.{ Buckets, DefaultBuckets, Label }
import zio.metrics.prometheus.PrometheusRegistry.{ Percentile, Tolerance }

object registry {
  def getCurrent(): RIO[PrometheusRegistry, CollectorRegistry] =
    RIO.accessM(_.registry.getCurrent())

  def registerCounter(name: String): RIO[PrometheusRegistry, PCounter] =
    RIO.accessM(_.registry.registerCounter(Label(name, Array.empty[String])))

  def registerCounter(name: String, labels: Array[String]): RIO[PrometheusRegistry, PCounter] =
    RIO.accessM(_.registry.registerCounter(Label(name, labels)))

  def registerGauge(name: String): RIO[PrometheusRegistry, PGauge] =
    RIO.accessM(_.registry.registerGauge(Label(name, Array.empty[String])))

  def registerGauge(name: String, labels: Array[String]): RIO[PrometheusRegistry, PGauge] =
    RIO.accessM(_.registry.registerGauge(Label(name, labels)))

  def registerHistogram(name: String): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, Array.empty[String]), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: String, labels: Array[String]): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, labels), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: String, buckets: Buckets): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, Array.empty[String]), buckets))

  def registerHistogram(name: String, labels: Array[String], buckets: Buckets): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, labels), buckets))

  def registerSummary(name: String): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, Array.empty[String]), List.empty[(Double, Double)]))

  def registerSummary(name: String, labels: Array[String]): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, labels), List.empty[(Double, Double)]))

  def registerSummary(name: String, percentiles: List[(Percentile, Tolerance)]): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, Array.empty[String]), percentiles))

  def registerSummary(
    name: String,
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)]
  ): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, labels), percentiles))

  def registerCounter(name: Class[_]): RIO[PrometheusRegistry, PCounter] =
    RIO.accessM(_.registry.registerCounter(Label(name, Array.empty[String])))

  def registerCounter(name: Class[_], labels: Array[String]): RIO[PrometheusRegistry, PCounter] =
    RIO.accessM(_.registry.registerCounter(Label(name, labels)))

  def registerGauge(name: Class[_]): RIO[PrometheusRegistry, PGauge] =
    RIO.accessM(_.registry.registerGauge(Label(name, Array.empty[String])))

  def registerGauge(name: Class[_], labels: Array[String]): RIO[PrometheusRegistry, PGauge] =
    RIO.accessM(_.registry.registerGauge(Label(name, labels)))

  def registerHistogram(name: Class[_]): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, Array.empty[String]), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: Class[_], labels: Array[String]): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, labels), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: Class[_], buckets: Buckets): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, Array.empty[String]), buckets))

  def registerHistogram(name: Class[_], labels: Array[String], buckets: Buckets): RIO[PrometheusRegistry, PHistogram] =
    RIO.accessM(_.registry.registerHistogram(Label(name, labels), buckets))

  def registerSummary(name: Class[_]): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, Array.empty[String]), List.empty[(Double, Double)]))

  def registerSummary(name: Class[_], labels: Array[String]): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, labels), List.empty[(Double, Double)]))

  def registerSummary(name: Class[_], percentiles: List[(Percentile, Tolerance)]): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, Array.empty[String]), percentiles))

  def registerSummary(
    name: Class[_],
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)]
  ): RIO[PrometheusRegistry, PSummary] =
    RIO.accessM(_.registry.registerSummary(Label(name, labels), percentiles))
}

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
    RIO.accessM(_.histogram.observe(h, amount, Array.empty[String]))

  def startTimer(h: PHistogram): RIO[PrometheusHistogram, PHistogram.Timer] =
    RIO.accessM(_.histogram.startTimer(h, Array.empty[String]))

  def observeDuration(timer: PHistogram.Timer): RIO[PrometheusHistogram, Double] =
    RIO.accessM(_.histogram.observeDuration(timer))

  def time(h: PHistogram, f: () => Unit): RIO[PrometheusHistogram, Double] =
    RIO.accessM(_.histogram.time(h, f, Array.empty[String]))

  def observe(h: PHistogram, amount: Double, labelNames: Array[String]): RIO[PrometheusHistogram, Unit] =
    RIO.accessM(_.histogram.observe(h, amount, labelNames))

  def startTimer(h: PHistogram, labelNames: Array[String]): RIO[PrometheusHistogram, PHistogram.Timer] =
    RIO.accessM(_.histogram.startTimer(h, labelNames))

  def time(h: PHistogram, f: () => Unit, labelNames: Array[String]): RIO[PrometheusHistogram, Double] =
    RIO.accessM(_.histogram.time(h, f, labelNames))
}

object summary {
  def observe(s: PSummary, amount: Double): RIO[PrometheusSummary, Unit] =
    RIO.accessM(_.summary.observe(s, amount, Array.empty[String]))

  def startTimer(s: PSummary): RIO[PrometheusSummary, PSummary.Timer] =
    RIO.accessM(_.summary.startTimer(s, Array.empty[String]))

  def observeDuration(timer: PSummary.Timer): RIO[PrometheusSummary, Double] =
    RIO.accessM(_.summary.observeDuration(timer))

  def time(s: PSummary, f: () => Unit): RIO[PrometheusSummary, Double] =
    RIO.accessM(_.summary.time(s, f, Array.empty[String]))

  def observe(s: PSummary, amount: Double, labelNames: Array[String]): RIO[PrometheusSummary, Unit] =
    RIO.accessM(_.summary.observe(s, amount, labelNames))

  def startTimer(s: PSummary, labelNames: Array[String]): RIO[PrometheusSummary, PSummary.Timer] =
    RIO.accessM(_.summary.startTimer(s, labelNames))

  def time(s: PSummary, f: () => Unit, labelNames: Array[String]): RIO[PrometheusSummary, Double] =
    RIO.accessM(_.summary.time(s, f, labelNames))
}

object exporters {
  def http(r: CollectorRegistry, port: Int): RIO[PrometheusExporters, HTTPServer] =
    RIO.accessM(_.exporters.http(r, port))

  def graphite(r: CollectorRegistry, host: String, port: Int, intervalSeconds: Int): RIO[PrometheusExporters, Thread] =
    RIO.accessM(_.exporters.graphite(r, host, port, intervalSeconds))

  def pushGateway(r: CollectorRegistry, host: String, port: Int, jobName: String): RIO[PrometheusExporters, Unit] =
    pushGateway(r, host, port, jobName, None, None, None)

  def pushGateway(
    r: CollectorRegistry,
    host: String,
    port: Int,
    jobName: String,
    user: String,
    password: String
  ): RIO[PrometheusExporters, Unit] =
    pushGateway(r, host, port, jobName, Some(user), Some(password), None)

  def pushGateway(
    r: CollectorRegistry,
    host: String,
    port: Int,
    jobName: String,
    httpConnectionFactory: HttpConnectionFactory
  ): RIO[PrometheusExporters, Unit] =
    pushGateway(r, host, port, jobName, None, None, Some(httpConnectionFactory))

  def pushGateway(
    r: CollectorRegistry,
    host: String,
    port: Int,
    jobName: String,
    user: Option[String],
    password: Option[String],
    httpConnectionFactory: Option[HttpConnectionFactory]
  ): RIO[PrometheusExporters, Unit] =
    RIO.accessM(_.exporters.pushGateway(r, host, port, jobName, user, password, httpConnectionFactory))

  def write004(r: CollectorRegistry): RIO[PrometheusExporters, String] =
    RIO.accessM(_.exporters.write004(r))

  def initializeDefaultExports(r: CollectorRegistry): RIO[PrometheusExporters, Unit] =
    RIO.accessM(_.exporters.initializeDefaultExports(r))

  def stopHttp(s: HTTPServer): RIO[PrometheusExporters, Unit] =
    PrometheusExporters.stopHttp(s)
}
