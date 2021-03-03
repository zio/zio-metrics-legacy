package zio.metrics.prometheus

import io.prometheus.client.{ Counter => PCounter, Gauge => PGauge }
import io.prometheus.client.{ Histogram => PHistogram, Summary => PSummary }
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.exporter.HttpConnectionFactory

import zio.{ RIO, URIO, ZIO }
import zio.metrics.Label
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.Registry.{ Percentile, Tolerance }

package object helpers {

  def getCurrentRegistry(): URIO[Registry, CollectorRegistry] =
    ZIO.accessM(_.get.getCurrent())

  def registerCounter(name: String): RIO[Registry, PCounter] =
    ZIO.accessM(_.get.registerCounter(Label(name, Array.empty[String], s"$name counter")))

  def registerCounter(name: String, help: String): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, Array.empty[String], help)))

  def registerCounter(name: String, labels: Array[String]): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, labels, s"$name counter")))

  def registerCounter(name: String, labels: Array[String], help: String): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, labels, help)))

  def registerGauge(name: String): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, Array.empty[String], s"$name gauge")))

  def registerGauge(name: String, help: String): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, Array.empty[String], help)))

  def registerGauge(name: String, labels: Array[String]): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, labels, s"$name gauge")))

  def registerGauge(name: String, labels: Array[String], help: String): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, labels, help)))

  def registerHistogram(name: String): RIO[Registry, PHistogram] =
    RIO.accessM(
      _.get.registerHistogram(Label(name, Array.empty[String], s"$name histogram"), DefaultBuckets(Seq.empty[Double]))
    )

  def registerHistogram(name: String, help: String): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, Array.empty[String], help), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: String, labels: Array[String]): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, s"$name histogram"), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: String, labels: Array[String], help: String): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, help), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: String, buckets: Buckets): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, Array.empty[String], s"$name histogram"), buckets))

  def registerHistogram(name: String, buckets: Buckets, help: String): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, Array.empty[String], help), buckets))

  def registerHistogram(name: String, labels: Array[String], buckets: Buckets): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, s"$name histogram"), buckets))

  def registerHistogram(
    name: String,
    labels: Array[String],
    buckets: Buckets,
    help: String
  ): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, help), buckets))

  def registerSummary(name: String): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], s"$name summary"), List.empty[(Double, Double)]))

  def registerSummary(name: String, help: String): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], help), List.empty[(Double, Double)]))

  def registerSummary(name: String, labels: Array[String]): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, s"$name summary"), List.empty[(Double, Double)]))

  def registerSummary(name: String, labels: Array[String], help: String): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, help), List.empty[(Double, Double)]))

  def registerSummary(name: String, percentiles: List[(Percentile, Tolerance)]): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], s"$name summary"), percentiles))

  def registerSummary(name: String, percentiles: List[(Percentile, Tolerance)], help: String): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], help), percentiles))

  def registerSummary(
    name: String,
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)]
  ): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, s"$name summary"), percentiles))

  def registerSummary(
    name: String,
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)],
    help: String
  ): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, help), percentiles))

  def registerCounter(name: Class[_]): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, Array.empty[String], s"$name counter")))

  def registerCounter(name: Class[_], help: String): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, Array.empty[String], help)))

  def registerCounter(name: Class[_], labels: Array[String]): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, labels, s"$name counter")))

  def registerCounter(name: Class[_], labels: Array[String], help: String): RIO[Registry, PCounter] =
    RIO.accessM(_.get.registerCounter(Label(name, labels, help)))

  def registerGauge(name: Class[_]): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, Array.empty[String], s"$name gauge")))

  def registerGauge(name: Class[_], help: String): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, Array.empty[String], help)))

  def registerGauge(name: Class[_], labels: Array[String]): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, labels, s"$name gauge")))

  def registerGauge(name: Class[_], labels: Array[String], help: String): RIO[Registry, PGauge] =
    RIO.accessM(_.get.registerGauge(Label(name, labels, help)))

  def registerHistogram(name: Class[_]): RIO[Registry, PHistogram] =
    RIO.accessM(
      _.get.registerHistogram(Label(name, Array.empty[String], s"$name gauge"), DefaultBuckets(Seq.empty[Double]))
    )

  def registerHistogram(name: Class[_], help: String): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, Array.empty[String], help), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: Class[_], labels: Array[String]): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, s"$name histogram"), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: Class[_], labels: Array[String], help: String): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, help), DefaultBuckets(Seq.empty[Double])))

  def registerHistogram(name: Class[_], buckets: Buckets): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, Array.empty[String], s"$name histogram"), buckets))

  def registerHistogram(name: Class[_], buckets: Buckets, help: String): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, Array.empty[String], help), buckets))

  def registerHistogram(name: Class[_], labels: Array[String], buckets: Buckets): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, s"$name histogram"), buckets))

  def registerHistogram(
    name: Class[_],
    labels: Array[String],
    buckets: Buckets,
    help: String
  ): RIO[Registry, PHistogram] =
    RIO.accessM(_.get.registerHistogram(Label(name, labels, help), buckets))

  def registerSummary(name: Class[_]): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], s"$name summary"), List.empty[(Double, Double)]))

  def registerSummary(name: Class[_], help: String): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], help), List.empty[(Double, Double)]))

  def registerSummary(name: Class[_], labels: Array[String]): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, s"$name summary"), List.empty[(Double, Double)]))

  def registerSummary(name: Class[_], labels: Array[String], help: String): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, help), List.empty[(Double, Double)]))

  def registerSummary(name: Class[_], percentiles: List[(Percentile, Tolerance)]): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], s"$name summary"), percentiles))

  def registerSummary(
    name: Class[_],
    percentiles: List[(Percentile, Tolerance)],
    help: String
  ): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, Array.empty[String], help), percentiles))

  def registerSummary(
    name: Class[_],
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)]
  ): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, s"$name summary"), percentiles))

  def registerSummary(
    name: Class[_],
    labels: Array[String],
    percentiles: List[(Percentile, Tolerance)],
    help: String
  ): RIO[Registry, PSummary] =
    RIO.accessM(_.get.registerSummary(Label(name, labels, help), percentiles))

  object counter {
    def register(name: String) =
      Counter(name, Array.empty[String])

    def register(name: String, help: String) =
      Counter(name, Array.empty[String], help)

    def register(name: String, labels: Array[String]) =
      Counter(name, labels)

    def register(name: String, labels: Array[String], help: String) =
      Counter(name, labels, help)
  }

  object gauge {
    def register(name: String) =
      Gauge(name, Array.empty[String])

    def register(name: String, help: String) =
      Gauge(name, Array.empty[String], help)

    def register(name: String, labels: Array[String]) =
      Gauge(name, labels)

    def register(name: String, labels: Array[String], help: String) =
      Gauge(name, labels, help)
  }

  object histogram {
    def register(name: String) =
      Histogram(name, Array.empty[String], DefaultBuckets(Seq.empty[Double]))

    def register(name: String, help: String) =
      Histogram(name, Array.empty[String], DefaultBuckets(Seq.empty[Double]), help)

    def register(name: String, labels: Array[String]) =
      Histogram(name, labels, DefaultBuckets(Seq.empty[Double]))

    def register(name: String, labels: Array[String], help: String) =
      Histogram(name, labels, DefaultBuckets(Seq.empty[Double]), help)

    def register(name: String, buckets: Buckets) =
      Histogram(name, Array.empty[String], buckets)

    def register(name: String, buckets: Buckets, help: String) =
      Histogram(name, Array.empty[String], buckets, help)

    def register(name: String, labels: Array[String], buckets: Buckets) =
      Histogram(name, labels, buckets)

    def register(name: String, labels: Array[String], buckets: Buckets, help: String) =
      Histogram(name, labels, buckets, help)
  }

  object summary {
    def register(name: String) =
      Summary(name, Array.empty[String], List.empty[(Double, Double)])

    def register(name: String, help: String) =
      Summary(name, Array.empty[String], List.empty[(Double, Double)], help)

    def register(name: String, labels: Array[String]) =
      Summary(name, labels, List.empty[(Percentile, Tolerance)])

    def register(name: String, labels: Array[String], help: String) =
      Summary(name, labels, List.empty[(Percentile, Tolerance)], help)

    def register(name: String, percentiles: List[(Percentile, Tolerance)]) =
      Summary(name, Array.empty[String], percentiles)

    def register(name: String, percentiles: List[(Percentile, Tolerance)], help: String) =
      Summary(name, Array.empty[String], percentiles, help)

    def register(name: String, labels: Array[String], percentiles: List[(Percentile, Tolerance)]) =
      Summary(name, labels, percentiles)

    def register(name: String, labels: Array[String], percentiles: List[(Percentile, Tolerance)], help: String) =
      Summary(name, labels, percentiles, help)
  }

  def http(r: CollectorRegistry, port: Int): RIO[Exporters, HTTPServer] =
    RIO.accessM(_.get.http(r, port))

  def graphite(
    r: CollectorRegistry,
    host: String,
    port: Int,
    intervalSeconds: Int
  ): RIO[Exporters, Thread] =
    RIO.accessM(_.get.graphite(r, host, port, intervalSeconds))

  def pushGateway(r: CollectorRegistry, host: String, port: Int, jobName: String): RIO[Exporters, Unit] =
    pushGateway(r, host, port, jobName, None, None, None)

  def pushGateway(
    r: CollectorRegistry,
    host: String,
    port: Int,
    jobName: String,
    user: String,
    password: String
  ): RIO[Exporters, Unit] =
    pushGateway(r, host, port, jobName, Some(user), Some(password), None)

  def pushGateway(
    r: CollectorRegistry,
    host: String,
    port: Int,
    jobName: String,
    httpConnectionFactory: HttpConnectionFactory
  ): RIO[Exporters, Unit] =
    pushGateway(r, host, port, jobName, None, None, Some(httpConnectionFactory))

  def pushGateway(
    r: CollectorRegistry,
    host: String,
    port: Int,
    jobName: String,
    user: Option[String],
    password: Option[String],
    httpConnectionFactory: Option[HttpConnectionFactory]
  ): RIO[Exporters, Unit] =
    RIO.accessM(_.get.pushGateway(r, host, port, jobName, user, password, httpConnectionFactory))

  def write004(r: CollectorRegistry): RIO[Exporters, String] =
    RIO.accessM(_.get.write004(r))

  def initializeDefaultExports(r: CollectorRegistry): RIO[Exporters, Unit] =
    RIO.accessM(_.get.initializeDefaultExports(r))

  def stopHttp(s: HTTPServer): RIO[Exporters, Unit] =
    Exporters.stopHttp(s)
}
