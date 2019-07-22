package zio.metrics

import scalaz._
import zio.Task

trait Reporter[Ctx, M <: Metrics[Task[?], Ctx], F[_], A] {

  type Filter    = Option[String]
  type MetriczIO = Metrics[Task[?], Ctx]

  def extractCounters: M => Filter => F[A]
  def extractGauges: M => Filter => F[A]
  def extractTimers: M => Filter => F[A]
  def extractHistograms: M => Filter => F[A]
  def extractMeters: M => Filter => F[A]

}

class ReportPrinter[Ctx, M <: Metrics[Task[?], Ctx]] {
  def report[F[_], A](metrics: M, filter: Option[String])(
    cons: (String, A) => A
  )(implicit M: Monoid[A], L: Foldable[F], R: Reporter[Ctx, M, F, A]): A = {
     import scalaz.syntax.semigroup._

        val fs = Seq(
          ("counters", R.extractCounters),
          ("gauges", R.extractGauges),
          ("timers", R.extractTimers),
          ("histograms", R.extractHistograms),
          ("meters", R.extractMeters)
        )

        fs.foldLeft(M.zero)((acc0, f) => {
          val m = f._2(metrics)(filter)
          acc0 |+| L.foldMap(m)(a => cons(f._1, a))
        })
  }
}

object ReportPrinter  {
  def apply[Ctx, M <: Metrics[Task[?], Ctx]]() =
    new ReportPrinter[Ctx, M]()
}
