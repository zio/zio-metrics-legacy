package zio.metrics

import java.text.DecimalFormat
import java.time.Instant

import zio.{ Layer, Task, ZLayer }

object encoders {

  type Encoder = Encoder.Service[Metric]

  object Encoder {
    trait Service[M] {
      def encode(metric: M): Task[Option[String]]
    }

    val format = new DecimalFormat("0.################")

    private def getValue(m: Metric): String = m match {
      case nm: NumericMetric => format.format(nm.value)
      case sm: StringMetric  => sm.value
      case _                 => ""
    }

    private def getMetricType(m: Metric): String = m match {
      case _: Counter      => "c"
      case _: Gauge        => "g"
      case _: Meter        => "m"
      case _: Set          => "s"
      case _: Timer        => "ms"
      case _: Histogram    => "h"
      case _: Distribution => "d"
      case _               => ""
    }

    val statsd: Layer[Nothing, Encoder] = ZLayer.succeed(new Service[Metric] {

      private def encode(metric: Metric, sampleRate: Option[Double]): String =
        sampleRate.foldLeft(s"${metric.name}:${getValue(metric)}|${getMetricType(metric)}")(
          (acc, d) => if (d < 1.0) acc + s"|@${format.format(d)}" else acc
        )

      override def encode(metric: Metric): Task[Option[String]] = Task.succeed {
        metric match {
          case srm: SampledMetric =>
            Some(encode(metric, Some(srm.sampleRate)))

          case nm: NumericMetric if (nm.value.isInfinite || nm.value.isNaN) =>
            None

          case _: Metric =>
            Some(encode(metric, None))

          case null =>
            None
        }
      }
    })

    val dogstatsd: Layer[Nothing, Encoder] = ZLayer.succeed(new Service[Metric] {

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
        val name      = serviceCheck.name
        val status    = serviceCheck.status
        val timestamp = serviceCheck.timestamp.fold(s"|d:${Instant.now().toEpochMilli()}")(l => s"|d:$l")
        val hostname  = serviceCheck.hostname.fold("")(h => s"|h:$h")
        val tagString = if (serviceCheck.tags.isEmpty) "" else "|#" + serviceCheck.tags.mkString(",")
        val message   = serviceCheck.message.fold("")(m => s"|m:${m.replace("\n", "\\\\n")}")
        s"_sc|$name|$status$timestamp$hostname$tagString$message"
      }

      override def encode(metric: Metric): Task[Option[String]] = Task.succeed {
        metric match {
          case sc: ServiceCheck => Some(encodeSeviceCheck(sc))
          case evt: Event       => Some(encodeEvent(evt))

          case srm: SampledMetric =>
            Some(encode(metric, Some(srm.sampleRate), srm.tags))

          case nm: NumericMetric if (nm.value.isInfinite || nm.value.isNaN) =>
            None

          case _: Metric =>
            Some(encode(metric, None, metric.tags))

          case null =>
            None
        }
      }
    })
  }
}
