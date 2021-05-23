package zio.metrics.prometheus

sealed trait Buckets

final case class DefaultBuckets(buckets: Seq[Double])                          extends Buckets
final case class LinearBuckets(start: Double, width: Double, count: Int)       extends Buckets
final case class ExponentialBuckets(start: Double, factor: Double, count: Int) extends Buckets
