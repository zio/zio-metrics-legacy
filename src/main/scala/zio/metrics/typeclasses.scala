package zio.metrics

import scala.math.Numeric
import scala.math.Numeric.Implicits._

trait Show[A] {
  def show(value: A): String
}

object Show {

  def apply[A](implicit sh: Show[A]): Show[A] = sh

  def show[A: Show](a: A): String = Show[A].show(a)

  implicit class ShowSyntax[A: Show](a: A) {
    def show() = Show[A].show(a)
  }

  implicit val showString = new Show[String] {
    def show(value: String): String = value
  }

  implicit def showClass[A] = new Show[Class[A]] {
    override def show(f: Class[A]): String = f.getName()
  }
}

trait Semigroup[A] {
  def combine(x: A, y: A): A
}

object Semigroup {

  def apply[A](implicit sg: Semigroup[A]) = sg

  def combine[A: Semigroup](x: A, y: A): A = Semigroup[A].combine(x, y)

  implicit class SemigroupSyntax[A: Semigroup](x: A) { self =>
    def combine(y: A): A = Semigroup.combine(x, y)
    def |+|(y: A): A     = self.combine(y)
  }

  implicit def numericAddSG[N: Numeric[?]] = new Semigroup[N] {
    def combine(x: N, y: N): N = x + y
  }

  implicit val strConcatSG = new Semigroup[String] {
    def combine(x: String, y: String): String = x + y
  }
}
