package zio.metrics

//import javax.management.openmbean.OpenType
import scalaz.{ Semigroup, Show }
import scala.concurrent.duration.TimeUnit

sealed trait Reservoir[+A]

object Reservoir {
  type Config = Map[String, Measurable]
  case class Uniform(config: Option[Config])               extends Reservoir[Nothing]
  case class Bounded[A](window: Long, unit: TimeUnit)      extends Reservoir[A]
  case class ExponentiallyDecaying(config: Option[Config]) extends Reservoir[Nothing]
}

trait Timer[F[_], A] {
  type Config = Map[String, A]
  val a: A
  def start: F[A]
  def stop(io: F[A]): F[Double]
}

trait HtmlRender[A] {
  def render(a: A): String
}

case class Label[A: Show](name: A, labels: Array[A], val sep: String)

object Label {
  def apply[A: Show](name: A, arr: Array[A], sep: String = ".") =
    new Label(name, arr, sep)

  implicit def labelShow[A: Show]: Show[Label[A]] = new Show[Label[A]] {
    override def shows(l: Label[A]): String = s"${l.name}"
  }
}

trait Metrics[F[_], Ctx] {

  def counter[L: Show](label: Label[L]): F[Long => F[Unit]]

  def gauge[A, B: Semigroup, L: Show](label: Label[L])(f: Option[A] => B): F[Option[A] => F[Unit]]

  def histogram[A: Numeric, L: Show](
    label: Label[L],
    res: Reservoir[A] = Reservoir.ExponentiallyDecaying(None)
  )(
    implicit
    num: Numeric[A]
  ): F[A => F[Unit]]

  def timer[L: Show](label: Label[L]): F[Timer[F[?], Ctx]]

  def meter[L: Show](label: Label[L]): F[Double => F[Unit]]

  // TODO is this still needed is L is not fixed to the Metrics trait?
  //def contramap[L0, L: Show](f: L0 => L): Metrics[F, Ctx]
}
