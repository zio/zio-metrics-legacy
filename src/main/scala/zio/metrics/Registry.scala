package zio.metrics

import zio.{ Task, UIO }

trait Registry {
  val registry: Registry.Service[_, _]
}

object Registry {
  trait Service[M, R] {
    def getCurrent(): UIO[R]
    def registerCounter[L: Show](label: Label[L]): Task[M]
    def registerGauge[L: Show, A](label: Label[L], f: () => A): Task[M]
    def registerHistogram[L: Show](label: Label[L]): Task[M]
    /* def registerSummary[A: Show](label: Label[A]): Task[M]
    def registerTimer[A: Show](label: Label[A]): Task[M]
    def registerMeter[A: Show](label: Label[A]): Task[M]*/
  }
}
