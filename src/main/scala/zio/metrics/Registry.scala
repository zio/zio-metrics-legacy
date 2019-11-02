package zio.metrics

import zio.{ Task, UIO }
import zio.metrics.typeclasses._

trait Registry {
  val registry: Registry.Service[_, _]
}

object Registry {
  trait Service[M, R] {
    def getCurrent(): UIO[R]
    def registerCounter[A: Show](label: Label[A]): Task[M]
    def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[M]
    /*def registerHistogram[A: Show](label: Label[A]): Task[M]
    def registerSummary[A: Show](label: Label[A]): Task[M]
    def registerTimer[A: Show](label: Label[A]): Task[M]
    def registerMeter[A: Show](label: Label[A]): Task[M]*/
  }
}
