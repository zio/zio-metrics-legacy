package zio

package object metrics {

  import scala.math.Numeric
  def some[A](a: A): Option[A] = Some(a)

  implicit class NumericSyntax[N: Numeric](n: N) {
    def some() = metrics.some(n)
  }
}
