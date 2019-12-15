package zio.metrics

/*import cats.Monoid
import cats.Foldable
import cats.syntax.semigroup._*/
import zio.Task
import zio.metrics.typeclasses.Monoid.MonoidSyntax
import zio.metrics.typeclasses.{ Foldable, Monoid }

trait Extractor[R <: Registry, F[_], A] {

  type Filter = Option[String]

  def extractCounters: R => Filter => Task[F[A]]
  def extractGauges: R => Filter => Task[F[A]]
  def extractTimers: R => Filter => Task[F[A]]
  def extractHistograms: R => Filter => Task[F[A]]
  def extractMeters: R => Filter => Task[F[A]]

}

object RegistryPrinter {
  def report[R <: Registry, F[_], A](r: R, filter: Option[String])(
    cons: (String, A) => A
  )(implicit M: Monoid[A], L: Foldable[F], E: Extractor[R, F, A]): Task[A] = {

    val fs = Seq(
      ("counters", E.extractCounters),
      ("gauges", E.extractGauges),
      ("timers", E.extractTimers),
      ("histograms", E.extractHistograms),
      ("meters", E.extractMeters)
    )

    fs.foldLeft(Task(M.empty))(
      (accT, f) =>
        for {
          acc <- accT
          m   <- f._2(r)(filter)
        } yield acc |+| L.foldMap(m)(a => cons(f._1, a))
    )
  }
}
