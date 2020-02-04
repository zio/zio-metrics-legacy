package zio.metrics.dogstatsd

import java.text.DecimalFormat

import zio.Task
import zio.metrics._
import _root_.java.time.Instant

/** A Metric to String encoder for DogStatsD protocol.
 * @see See [[http://docs.datadoghq.com/guides/dogstatsd/#datagram-format]] for full spec
 */
trait DogStatsDEncoder extends Encoder {

  val format = new DecimalFormat("0.################")

  val encoder = new Encoder.Service[Metric] {
    private def getValue(m: Metric): String = m match {
      case nm: NumericMetric => format.format(nm.value)
      case sm: StringMetric  => sm.value
      case _                 => ""
    }

    private def getMetricType(m: Metric): String = m match {
      case _: Counter   => "c"
      case _: Gauge     => "g"
      case _: Meter     => "m"
      case _: Set       => "s"
      case _: Timer     => "ms"
      case _: Histogram => "h"
      case _            => ""
    }

    private def encode(metric: Metric, sampleRate: Option[Double], tags: Seq[Tag]): String = {
      val tagString = if (tags.isEmpty) "" else "|#" + tags.mkString(",")
      sampleRate.foldLeft(s"${metric.name}:${getValue(metric)}|${getMetricType(metric)}")(
        (acc, d) => if (d < 1.0) acc + s"|@${format.format(d)}" else acc
      ) + tagString
    }

    private def encodeEvent(event: Event): String = {
      val timestamp   = event.timestamp.fold(s"|d:${Instant.now().toEpochMilli()}")(l => s"|d:$l")
      val hostname    = event.hostname.fold("")(h => s"|h:$h")
      val aggKey      = event.aggregationKey.fold("")(k => s"|k:$k")
      val priority    = event.priority.fold("|p:normal")(s => s"|p:$s")
      val sourceType  = event.sourceTypeName.fold("")(s => s"|s:$s")
      val alertType   = event.alertType.fold("|t:info")(s => s"|t:$s")
      val tagString   = if (event.tags.isEmpty) "" else "|#" + event.tags.mkString(",")
      val encodedText = event.text.replace("\n", "\\\\n")
      s"_e{${event.name.size},${event.text.size}}:${event.name}|${encodedText}$timestamp$hostname$aggKey$priority$sourceType$alertType" + tagString
    }

    private def encodeSeviceCheck(serviceCheck: ServiceCheck): String = {
      val name = serviceCheck.name
      val status = serviceCheck.status
      val timestamp = serviceCheck.timestamp.fold(s"|d:${Instant.now().toEpochMilli()}")(l => s"|d:$l")
      val hostname = serviceCheck.hostname.fold("")(h => s"|h:$h")
      val tagString   = if (serviceCheck.tags.isEmpty) "" else "|#" + serviceCheck.tags.mkString(",")
      val message = serviceCheck.message.fold("")(m => s"|m:${m.replace("\n","\\\\n")}")
      s"_sc|$name|$status$timestamp$hostname$tagString$message"
    }

    override def encode(metric: Metric): Task[Option[String]] = Task {
      metric match {
        case sc: ServiceCheck => Some(encodeSeviceCheck(sc))
        case evt: Event => Some(encodeEvent(evt))

        case srm: SampledMetric =>
          Some(encode(metric, Some(srm.sampleRate), srm.tags))

        case nm: NumericMetric if (nm.value.isInfinite || nm.value.isNaN) =>
          None

        case _: Metric =>
          Some(encode(metric, None, metric.tags))

        case _ =>
          None
      }
    }
  }
}

object DogStatsDEncoder extends DogStatsDEncoder {

  val SERVICE_CHECK_OK = 0
  val SERVICE_CHECK_WARNING = 1
  val SERVICE_CHECK_CRITICAL = 2
  val SERVICE_CHECK_UNKNOWN = 3
  val EVENT_PRIORITY_LOW = "low"
  val EVENT_PRIORITY_NORMAL = "normal"
  val EVENT_ALERT_TYPE_ERROR = "error"
  val EVENT_ALERT_TYPE_INFO = "info"
  val EVENT_ALERT_TYPE_SUCCESS = "success"
  val EVENT_ALERT_TYPE_WARNING = "warning"

  def encode(metric: Metric): Task[Option[String]] = encoder.encode(metric)
}
