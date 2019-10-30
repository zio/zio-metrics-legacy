package zio.metrics

//import javax.management.openmbean.OpenType
import zio.metrics.typeclasses._

trait Metrics[F[_], Ctx] {

  def counter[L: Show](label: Label[L]): F[Long => F[Unit]]

  def gauge[A, B: Semigroup, L: Show](label: Label[L])(f: Option[A] => B): F[Option[A] => F[Unit]]

  /*def histogram[A: Numeric, L: Show](
    label: Label[L],
    res: Reservoir[A] = Reservoir.ExponentiallyDecaying(None)
  )(
    implicit
    num: Numeric[A]
  ): F[A => F[Unit]]

  def timer[L: Show](label: Label[L]): F[Timer[F[?], Ctx]]*/

  def meter[L: Show](label: Label[L]): F[Double => F[Unit]]

  // TODO is this still needed is L is not fixed to the Metrics trait?
  //def contramap[L0, L: Show](f: L0 => L): Metrics[F, Ctx]
}
