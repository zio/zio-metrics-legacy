package zio.metrics.dropwizard

import zio.Task
import com.codahale.metrics.Snapshot

import scala.jdk.CollectionConverters._

import io.circe._
import io.circe.Json

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
                .map(entry => Json.obj((entry._1, Json.fromLong(entry._2.getCount))))
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
                .map(entry => Json.obj((entry._1, Json.fromString(entry._2.getValue.toString))))
                .toList
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
                  val jObj: JsonObject = extractSnapshot(
                    entry._1,
                    entry._2.getSnapshot
                  ).asObject.getOrElse(JsonObject.empty)

                  Json.fromJsonObject((s"${entry._1}_count", Json.fromLong(entry._2.getCount)) +: jObj)
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
                  Json.obj(
                    (s"${entry._1}_count", Json.fromLong(entry._2.getCount)),
                    (s"${entry._1}_meanRate", Json.fromDoubleOrNull(entry._2.getMeanRate)),
                    (s"${entry._1}_oneMinRate", Json.fromDoubleOrNull(entry._2.getOneMinuteRate)),
                    (s"${entry._1}_fiveMinRate", Json.fromDoubleOrNull(entry._2.getFiveMinuteRate)),
                    (s"${entry._1}_fifteenMinRate", Json.fromDoubleOrNull(entry._2.getFifteenMinuteRate))
                  )
                })
                .toList
          }
    }

  import cats.instances.list._
  import zio.metrics.dropwizard.typeclasses._
  import zio.metrics.dropwizard.RegistryPrinter

  type Filter = Option[String]

  val writeJson: DropwizardRegistry => Filter => Task[Json] =
    dwr =>
      filter =>
        for {
          j <- RegistryPrinter.report[DropwizardRegistry, List, Json](dwr, filter)(
                (k: String, v: Json) => Json.obj((k, v))
              )
        } yield j

}
