package zio.metrics.dropwizard

import cats.Monoid
import cats.Foldable
import cats.syntax.semigroup._
import zio.{ RIO, Task }
import com.codahale.metrics.MetricRegistry
//import zio.metrics.dropwizard.typeclasses._
//import zio.metrics.dropwizard.typeclasses.{ Foldable, Monoid }

trait Extractor[F[_], A] {

  type Filter = Option[String]

  val extractCounters: MetricRegistry => Filter => RIO[Registry, F[A]]
  val extractGauges: MetricRegistry => Filter => RIO[Registry, F[A]]
  val extractTimers: MetricRegistry => Filter => RIO[Registry, F[A]]
  val extractHistograms: MetricRegistry => Filter => RIO[Registry, F[A]]
  val extractMeters: MetricRegistry => Filter => RIO[Registry, F[A]]

}

object RegistryPrinter {
  def report[F[_], A](r: MetricRegistry, filter: Option[String])(
    cons: (String, A) => A
  )(implicit M: Monoid[A], L: Foldable[F], E: Extractor[F, A]): Task[A] = {

    val fs = Seq(
      ("counters", E.extractCounters(r)(filter)),
      ("gauges", E.extractGauges(r)(filter)),
      ("timers", E.extractTimers(r)(filter)),
      ("histograms", E.extractHistograms(r)(filter)),
      ("meters", E.extractMeters(r)(filter))
    )

    fs.foldLeft(RIO(M.empty))(
      (accT, f) =>
        for {
          acc <- accT
          m   <- f._2.provideLayer(Registry.live)
        } yield acc |+| L.foldMap(m)(a => cons(f._1, a))
    )
  }
}
