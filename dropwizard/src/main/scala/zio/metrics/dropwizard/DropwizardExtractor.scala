package zio.metrics.dropwizard

import zio.metrics.Extractor
import zio.Task
import argonaut.Argonaut.{ jNumber, jSingleObject, jString }
import argonaut.Json
import com.codahale.metrics.Snapshot

import scala.collection.JavaConverters._

object DropwizardExtractor {

  implicit val jsonDWExtractor: Extractor[DropwizardRegistry, List, Json] =
    new Extractor[DropwizardRegistry, List, Json] {
      override val extractCounters: DropwizardRegistry => Filter => Task[List[Json]] =
        (registry: DropwizardRegistry) =>
          (filter: Filter) => {
            val metricFilter = DropwizardRegistry.makeFilter(filter)
            for {
              r <- registry.registry.getCurrent()
            } yield
              r.getCounters(metricFilter)
                .asScala
                .map(entry => jSingleObject(entry._1, jNumber(entry._2.getCount)))
                .toList
          }

      override val extractGauges: DropwizardRegistry => Filter => Task[List[Json]] =
        (registry: DropwizardRegistry) =>
          (filter: Filter) => {
            val metricFilter = DropwizardRegistry.makeFilter(filter)
            for {
              r <- registry.registry.getCurrent()
            } yield
              r.getGauges(metricFilter)
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

      override val extractTimers: DropwizardRegistry => Filter => Task[List[Json]] =
        (registry: DropwizardRegistry) =>
          (filter: Filter) => {
            val metricFilter = DropwizardRegistry.makeFilter(filter)
            for {
              r <- registry.registry.getCurrent()
            } yield
              r.getTimers(metricFilter)
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

      override val extractHistograms: DropwizardRegistry => Filter => Task[List[Json]] =
        (registry: DropwizardRegistry) =>
          (filter: Filter) => {
            val metricFilter = DropwizardRegistry.makeFilter(filter)
            for {
              r <- registry.registry.getCurrent()
            } yield
              r.getHistograms(metricFilter)
                .asScala
                .map(entry => {
                  (s"${entry._1}_count" -> jNumber(entry._2.getCount)) ->:
                    extractSnapshot(entry._1, entry._2.getSnapshot)
                })
                .toList
          }

      override val extractMeters: DropwizardRegistry => Filter => Task[List[Json]] =
        (registry: DropwizardRegistry) =>
          (filter: Filter) => {
            val metricFilter = DropwizardRegistry.makeFilter(filter)
            for {
              r <- registry.registry.getCurrent()
            } yield
              r.getMeters(metricFilter)
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
}
