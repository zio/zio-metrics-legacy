package zio.metrics.dropwizard

import zio.{ RIO, Task }
import com.codahale.metrics.Snapshot

import scala.collection.JavaConverters._

import io.circe._
import io.circe.Json
import com.codahale.metrics.MetricRegistry

object DropwizardExtractor {

  implicit val jsonDWExtractor: Extractor[List, Json] =
    new Extractor[List, Json] {
      override val extractCounters: MetricRegistry => Filter => RIO[Registry, List[Json]] = registry =>
        (filter: Filter) => {
          val metricFilter = Registry.makeFilter(filter)
          Task(
            registry
              .getCounters(metricFilter)
              .asScala
              .map(entry => Json.obj((entry._1, Json.fromLong(entry._2.getCount))))
              .toList
          )
        }

      override val extractGauges: MetricRegistry => Filter => RIO[Registry, List[Json]] = registry =>
        (filter: Filter) => {
          val metricFilter = Registry.makeFilter(filter)
          Task(
            registry
              .getGauges(metricFilter)
              .asScala
              .map(entry => Json.obj((entry._1, Json.fromString(entry._2.getValue.toString))))
              .toList
          )
        }

      def extractSnapshot(name: String, snapshot: Snapshot): Json =
        Json.obj(
          (s"${name}_max", Json.fromLong(snapshot.getMax)),
          (s"${name}_min", Json.fromLong(snapshot.getMin)),
          (s"${name}_mean", Json.fromDoubleOrNull(snapshot.getMean)),
          (s"${name}_median", Json.fromDoubleOrNull(snapshot.getMedian)),
          (s"${name}_stdDev", Json.fromDoubleOrNull(snapshot.getStdDev)),
          (s"${name}_75th", Json.fromDoubleOrNull(snapshot.get75thPercentile())),
          (s"${name}_95th", Json.fromDoubleOrNull(snapshot.get95thPercentile())),
          (s"${name}_98th", Json.fromDoubleOrNull(snapshot.get98thPercentile())),
          (s"${name}_99th", Json.fromDoubleOrNull(snapshot.get99thPercentile())),
          (s"${name}_999th", Json.fromDoubleOrNull(snapshot.get999thPercentile()))
        )

      override val extractTimers: MetricRegistry => Filter => RIO[Registry, List[Json]] = registry =>
        (filter: Filter) => {
          val metricFilter = Registry.makeFilter(filter)
          Task(
            registry
              .getTimers(metricFilter)
              .asScala
              .map(entry => {
                val j1 = Json.obj(
                  (s"${entry._1}_count", Json.fromLong(entry._2.getCount)),
                  (s"${entry._1}_meanRate", Json.fromDoubleOrNull(entry._2.getMeanRate)),
                  (s"${entry._1}_oneMinRate", Json.fromDoubleOrNull(entry._2.getOneMinuteRate)),
                  (s"${entry._1}_fiveMinRate", Json.fromDoubleOrNull(entry._2.getFiveMinuteRate)),
                  (s"${entry._1}_fifteenMinRate", Json.fromDoubleOrNull(entry._2.getFifteenMinuteRate))
                ) //.deepmerge(extractSnapshot(entry._1, entry._2.getSnapshot))
                val j2 = extractSnapshot(entry._1, entry._2.getSnapshot)
                j1.deepMerge(j2)
              })
              .toList
          )
        }

      override val extractHistograms: MetricRegistry => Filter => RIO[Registry, List[Json]] = registry =>
        (filter: Filter) => {
          val metricFilter = Registry.makeFilter(filter)
          Task(
            registry
              .getHistograms(metricFilter)
              .asScala
              .map(entry => {
                val jObj: JsonObject = extractSnapshot(
                  entry._1,
                  entry._2.getSnapshot
                ).asObject.getOrElse(JsonObject.empty)

                Json.fromJsonObject((s"${entry._1}_count", Json.fromLong(entry._2.getCount)) +: jObj)
              })
              .toList
          )
        }

      override val extractMeters: MetricRegistry => Filter => RIO[Registry, List[Json]] = registry =>
        (filter: Filter) => {
          val metricFilter = Registry.makeFilter(filter)
          Task(
            registry
              .getMeters(metricFilter)
              .asScala
              .map(entry => {
                Json.obj(
                  (s"${entry._1}_count", Json.fromLong(entry._2.getCount)),
                  (s"${entry._1}_meanRate", Json.fromDoubleOrNull(entry._2.getMeanRate)),
                  (s"${entry._1}_oneMinRate", Json.fromDoubleOrNull(entry._2.getOneMinuteRate)),
                  (s"${entry._1}_fiveMinRate", Json.fromDoubleOrNull(entry._2.getFiveMinuteRate)),
                  (s"${entry._1}_fifteenMinRate", Json.fromDoubleOrNull(entry._2.getFifteenMinuteRate))
                )
              })
              .toList
          )
        }
    }

  import cats.instances.list._
  import zio.metrics.dropwizard.typeclasses._
  import zio.metrics.dropwizard.RegistryPrinter

  type Filter = Option[String]

  val writeJson: MetricRegistry => Filter => Task[Json] = registry =>
    filter =>
      for {
        j <- RegistryPrinter.report[List, Json](registry, filter)(
              (k: String, v: Json) => Json.obj((k, v))
            )
      } yield j

}
