package zio.metrics.dropwizard

import cats.Monoid
import cats.Foldable
import cats.syntax.semigroup._
import zio.{ RIO, Task }
//import zio.metrics.dropwizard.typeclasses._
//import zio.metrics.dropwizard.typeclasses.{ Foldable, Monoid }

trait Extractor[F[_], A] {

  type Filter = Option[String]

  val extractCounters: Filter => RIO[Registry, F[A]]
  val extractGauges: Filter => RIO[Registry, F[A]]
  val extractTimers: Filter => RIO[Registry, F[A]]
  val extractHistograms: Filter => RIO[Registry, F[A]]
  val extractMeters: Filter => RIO[Registry, F[A]]

}

object RegistryPrinter {
  def report[F[_], A](filter: Option[String])(
    cons: (String, A) => A
  )(implicit M: Monoid[A], L: Foldable[F], E: Extractor[F, A]): Task[A] = {

    val fs = Seq(
      ("counters", E.extractCounters),
      ("gauges", E.extractGauges),
      ("timers", E.extractTimers),
      ("histograms", E.extractHistograms),
      ("meters", E.extractMeters)
    )

    fs.foldLeft(RIO(M.empty))(
      (accT, f) =>
        for {
          acc <- accT
          m   <- f._2(filter).provideLayer(Registry.live)
        } yield acc |+| L.foldMap(m)(a => cons(f._1, a))
    )
  }
}
