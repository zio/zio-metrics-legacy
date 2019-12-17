package zio.metrics.prometheus

trait Buckets {}
case class DefaultBuckets(buckets: Seq[Double])                          extends Buckets
case class LinearBuckets(start: Double, width: Double, count: Int)       extends Buckets
case class ExponentialBuckets(start: Double, factor: Double, count: Int) extends Buckets
