package zio.metrics

import zio.{ Task, UIO }

trait Registry {
  val registry: Registry.Service[_, _]
}

object Registry {
  trait Service[M, R] {
    def getCurrent(): UIO[R]
    def registerCounter[L: Show](label: Label[L]): Task[M]
    def registerGauge[L: Show](label: Label[L]): Task[M]
    def registerHistogram[L: Show](label: Label[L], buckets: Buckets): Task[M]
    def registerSummary[L: Show](label: Label[L], quantiles: List[(Double, Double)]): Task[M]
  }
}
