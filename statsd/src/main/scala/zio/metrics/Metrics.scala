package zio.metrics

case class Tag(key: String, value: String) {
  override def toString() = s"$key:$value"
}

sealed trait ServiceCheckStatus {
  val value: Int

  override def toString(): String = value.toString()
}
case object ServiceCheckOk extends ServiceCheckStatus {
  val value: Int = 0
}
case object ServiceCheckWarning extends ServiceCheckStatus {
  val value: Int = 1
}
case object ServiceCheckCritical extends ServiceCheckStatus {
  val value: Int = 2
}
case object ServiceCheckUnknown extends ServiceCheckStatus {
  val value: Int = 3
}

sealed trait EventPriority {
  val value: String

  override def toString(): String = value
}
case object EventPriorityLow extends EventPriority {
  val value = "low"
}
case object EventPriorityNormal extends EventPriority {
  val value = "normal"
}

sealed trait EventAlertType {
  val value: String

  override def toString(): String = value
}
case object EventAlertError extends EventAlertType {
  val value = "error"
}
case object EventAlertInfo extends EventAlertType {
  val value = "info"
}
case object EventAlertSuccess extends EventAlertType {
  val value = "success"
}
case object EventAlertWarning extends EventAlertType {
  val value = "warning"
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
  priority: Option[EventPriority],
  sourceTypeName: Option[String],
  alertType: Option[EventAlertType],
  tags: Seq[Tag]
) extends Metric

case class Gauge(name: String, value: Double, tags: Seq[Tag]) extends Metric with NumericMetric

case class Histogram(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])
    extends Metric
    with NumericMetric
    with SampledMetric

case class Meter(name: String, value: Double, tags: Seq[Tag]) extends Metric with NumericMetric

case class ServiceCheck(
  name: String,
  status: ServiceCheckStatus,
  timestamp: Option[Long],
  hostname: Option[String],
  message: Option[String],
  tags: Seq[Tag]
) extends Metric

case class Set(name: String, value: String, tags: Seq[Tag]) extends Metric with StringMetric

case class Timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])
    extends Metric
    with NumericMetric
    with SampledMetric

case class Distribution(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])
    extends Metric
    with NumericMetric
    with SampledMetric
