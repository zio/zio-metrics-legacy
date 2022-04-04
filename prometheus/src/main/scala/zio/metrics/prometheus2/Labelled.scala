package zio.metrics.prometheus2

import zio.ZIO

/**
 * Helper to create strongly typed Prometheus labelled metrics.
 *
 * Metrics are defined with a list of labels whose length is statically known.
 * Operations on the metric (increment a counter for instance), require to pass a list of label
 * values with the same length.
 */
trait LabelledMetric[R, E, M] {
  def unsafeLabelled(
    name: String,
    help: Option[String],
    labels: Seq[String]
  ): ZIO[R, E, Seq[String] => M]

  def apply(name: String, help: Option[String]): ZIO[R, E, M] =
    unsafeLabelled(name, help, Nil).map(f => f(Nil))
  def apply[L <: LabelList](name: String, help: Option[String], labels: L): ZIO[R, E, Labelled[L]] =
    unsafeLabelled(name, help, labels.toList).map(f => (l: L) => f(l.toList))

  type Labelled[L <: LabelList] = L => M
}

/**
 * Helper to create strongly typed Prometheus labelled metrics.
 *
 * Metrics are defined with a list of labels whose length is statically known.
 * Operations on the metric (increment a counter for instance), require to pass a list of label
 * values with the same length.
 */
trait LabelledMetricP[R, E, P, M] {
  protected[this] def unsafeLabelled(
    name: String,
    p: P,
    help: Option[String],
    labels: Seq[String]
  ): ZIO[R, E, Seq[String] => M]

  def apply(name: String, p: P, help: Option[String]): ZIO[R, E, M] =
    unsafeLabelled(name, p, help, Nil).map(f => f(Nil))
  def apply[L <: LabelList](
    name: String,
    p: P,
    help: Option[String],
    labels: L
  ): ZIO[R, E, Labelled[L]] =
    unsafeLabelled(name, p, help, labels.toList).map(f => (l: L) => f(l.toList))

  type Labelled[L <: LabelList] = L => M
}
