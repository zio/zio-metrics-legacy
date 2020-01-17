package zio.metrics.statsd

import java.text.DecimalFormat

import zio.Task

trait StatsdEncoder extends Encoder {

  val format = new DecimalFormat("0.################")

  val encoder = new Encoder.Service[Metric] {
    private def getValue(m: Metric): String = m match {
      case nm: NumericMetric => format.format(nm.value)
      case sm: StringMetric  => sm.value
      case _                 => ""
    }

    private def getMetricType(m: Metric): String = m match {
      case _: Counter => "c"
      case _: Gauge   => "g"
      case _: Meter   => "m"
      case _: Set     => "s"
      case _: Timer   => "ms"
      case _          => ""
    }

    private def encode(metric: Metric, sampleRate: Option[Double]): String =
      sampleRate.foldLeft(s"${metric.name}:${getValue(metric)}|${getMetricType(metric)}")(
        (acc, d) => if (d < 1.0) acc + s"|@${format.format(d)}" else acc
      )

    override def encode(metric: Metric): Task[Option[String]] = Task {
      metric match {
        case srm: SampledMetric =>
          Some(encode(metric, Some(srm.sampleRate)))

        case nm: NumericMetric if (nm.value.isInfinite || nm.value.isNaN) =>
          None

        case _: Metric =>
          Some(encode(metric, None))

        case _ =>
          None
      }
    }
  }
}

object StatsdEncoder extends StatsdEncoder
