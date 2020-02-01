package zio.metrics

case class Tag(key: String, value: String) {
  override def toString() = s"$key:$value"
}

sealed trait Metric {
  def name: String

  def tags: Seq[Tag]
}

trait NumericMetric {
  def value: Double
}

sealed trait SampledMetric {
  def sampleRate: Double
}

sealed trait StringMetric {
  def value: String
}

case class Counter(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])
    extends Metric
    with NumericMetric
    with SampledMetric

case class Event(
  name: String,
  text: String,
  timestamp: Option[Long],
  hostname: Option[String],
  aggregationKey: Option[String],
  priority: Option[String],
  sourceTypeName: Option[String],
  alertType: Option[String],
  tags: Seq[Tag]
) extends Metric

case class Gauge(name: String, value: Double, tags: Seq[Tag]) extends Metric with NumericMetric

case class Histogram(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]) extends Metric with NumericMetric with SampledMetric

case class Meter(name: String, value: Double, tags: Seq[Tag]) extends Metric with NumericMetric

case class ServiceCheck(name: String, status: Int, timestamp: Option[Long], hostname: Option[String], message: Option[String], tags: Seq[Tag]) extends Metric

case class Set(name: String, value: String, tags: Seq[Tag]) extends Metric with StringMetric

case class Timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]) extends Metric with NumericMetric with SampledMetric
