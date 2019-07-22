package zio.metrics
import argonaut.Argonaut.{ jNumber, jSingleObject, jString }
import argonaut.{ Json, _ }
import com.codahale.metrics.Snapshot
import com.codahale.metrics.Timer.Context

import scala.collection.JavaConverters._

object DropwizardReporters {

  implicit val jsonDWReporter: Reporter[Context, DropwizardMetrics, List, Json] =
    new Reporter[Context, DropwizardMetrics, List, Json] {
      override val extractCounters: DropwizardMetrics => Filter => List[Json] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getCounters(metricFilter)
              .asScala
              .map(entry => jSingleObject(entry._1, jNumber(entry._2.getCount)))
              .toList
          }

      override val extractGauges: DropwizardMetrics => Filter => List[Json] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getGauges(metricFilter)
              .asScala
              .map(entry => jSingleObject(entry._1, jString(entry._2.getValue.toString)))
              .toList
          }

      def extractSnapshot(name: String, snapshot: Snapshot): Json =
        Json(
          s"${name}_max"    -> jNumber(snapshot.getMax),
          s"${name}_min"    -> jNumber(snapshot.getMin),
          s"${name}_mean"   -> jNumber(snapshot.getMean),
          s"${name}_median" -> jNumber(snapshot.getMedian),
          s"${name}_stdDev" -> jNumber(snapshot.getStdDev),
          s"${name}_75th"   -> jNumber(snapshot.get75thPercentile()),
          s"${name}_95th"   -> jNumber(snapshot.get95thPercentile()),
          s"${name}_98th"   -> jNumber(snapshot.get98thPercentile()),
          s"${name}_99th"   -> jNumber(snapshot.get99thPercentile()),
          s"${name}_999th"  -> jNumber(snapshot.get999thPercentile())
        )

      override val extractTimers: DropwizardMetrics => Filter => List[Json] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getTimers(metricFilter)
              .asScala
              .map(entry => {
                Json(
                  s"${entry._1}_count"          -> jNumber(entry._2.getCount),
                  s"${entry._1}_meanRate"       -> jNumber(entry._2.getMeanRate),
                  s"${entry._1}_oneMinRate"     -> jNumber(entry._2.getOneMinuteRate),
                  s"${entry._1}_fiveMinRate"    -> jNumber(entry._2.getFiveMinuteRate),
                  s"${entry._1}_fifteenMinRate" -> jNumber(entry._2.getFifteenMinuteRate)
                ).deepmerge(extractSnapshot(entry._1, entry._2.getSnapshot))
              })
              .toList
          }

      override val extractHistograms: DropwizardMetrics => Filter => List[Json] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getHistograms(metricFilter)
              .asScala
              .map(entry => {
                (s"${entry._1}_count" -> jNumber(entry._2.getCount)) ->:
                  extractSnapshot(entry._1, entry._2.getSnapshot)
              })
              .toList
          }

      override val extractMeters: DropwizardMetrics => Filter => List[Json] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getMeters(metricFilter)
              .asScala
              .map(entry => {
                Json(
                  s"${entry._1}_count"          -> jNumber(entry._2.getCount),
                  s"${entry._1}_meanRate"       -> jNumber(entry._2.getMeanRate),
                  s"${entry._1}_oneMinRate"     -> jNumber(entry._2.getOneMinuteRate),
                  s"${entry._1}_fiveMinRate"    -> jNumber(entry._2.getFiveMinuteRate),
                  s"${entry._1}_fifteenMinRate" -> jNumber(entry._2.getFifteenMinuteRate)
                )
              })
              .toList
          }
    }

  type MapEither = Either[Measurable, Map[String, Measurable]]

  implicit def mapReporter: Reporter[Context, DropwizardMetrics, Map[String, ?], MapEither] =
    new Reporter[Context, DropwizardMetrics, Map[String, ?], MapEither] {
      override val extractCounters: DropwizardMetrics => Filter => Map[String, MapEither] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getCounters(metricFilter)
              .asScala
              .map(entry => entry._1 -> Left(LongZ(entry._2.getCount)))
              .toMap
          }

      override val extractGauges: DropwizardMetrics => Filter => Map[String, MapEither] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getGauges(metricFilter)
              .asScala
              .map(entry => entry._1 -> Left(StringZ(entry._2.getValue.toString)))
              .toMap
          }

      def extractSnapshot(name: String, snapshot: Snapshot): Map[String, Measurable] =
        Map(
          s"${name}_max"    -> LongZ(snapshot.getMax),
          s"${name}_min"    -> LongZ(snapshot.getMin),
          s"${name}_mean"   -> DoubleZ(snapshot.getMean),
          s"${name}_median" -> DoubleZ(snapshot.getMedian),
          s"${name}_stdDev" -> DoubleZ(snapshot.getStdDev),
          s"${name}_75th"   -> DoubleZ(snapshot.get75thPercentile()),
          s"${name}_95th"   -> DoubleZ(snapshot.get95thPercentile()),
          s"${name}_98th"   -> DoubleZ(snapshot.get98thPercentile()),
          s"${name}_99th"   -> DoubleZ(snapshot.get99thPercentile()),
          s"${name}_999th"  -> DoubleZ(snapshot.get999thPercentile())
        )

      override val extractTimers: DropwizardMetrics => Filter => Map[String, MapEither] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getTimers(metricFilter)
              .asScala
              .map(
                entry =>
                  entry._1 -> Right(
                    Map(
                      s"${entry._1}_count"          -> LongZ(entry._2.getCount),
                      s"${entry._1}_meanRate"       -> DoubleZ(entry._2.getMeanRate),
                      s"${entry._1}_oneMinRate"     -> DoubleZ(entry._2.getOneMinuteRate),
                      s"${entry._1}_fiveMinRate"    -> DoubleZ(entry._2.getFiveMinuteRate),
                      s"${entry._1}_fifteenMinRate" -> DoubleZ(entry._2.getFifteenMinuteRate)
                    ) ++ extractSnapshot(entry._1, entry._2.getSnapshot)
                  )
              )
              .toMap
          }

      override val extractHistograms: DropwizardMetrics => Filter => Map[String, MapEither] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getHistograms(metricFilter)
              .asScala
              .map(
                entry =>
                  entry._1 -> Right(
                    Map(
                      s"${entry._1}_count" -> LongZ(entry._2.getCount)
                    ) ++ extractSnapshot(entry._1, entry._2.getSnapshot)
                  )
              )
              .toMap
          }

      override val extractMeters: DropwizardMetrics => Filter => Map[String, MapEither] =
        (metrics: DropwizardMetrics) =>
          (filter: Filter) => {
            val metricFilter = DropwizardMetrics.makeFilter(filter)
            metrics.registry
              .getMeters(metricFilter)
              .asScala
              .map(
                entry =>
                  entry._1 -> Right(
                    Map(
                      s"${entry._1}_count"          -> LongZ(entry._2.getCount),
                      s"${entry._1}_meanRate"       -> DoubleZ(entry._2.getMeanRate),
                      s"${entry._1}_oneMinRate"     -> DoubleZ(entry._2.getOneMinuteRate),
                      s"${entry._1}_fiveMinRate"    -> DoubleZ(entry._2.getFiveMinuteRate),
                      s"${entry._1}_fifteenMinRate" -> DoubleZ(entry._2.getFifteenMinuteRate)
                    )
                  )
              )
              .toMap
          }
    }
}
