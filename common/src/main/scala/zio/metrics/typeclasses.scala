package zio.metrics

import scala.math.Numeric
import scala.math.Numeric.Implicits._

final case class Label[A](name: A, labels: Array[String], help: String)

trait Show[A] {
  def show(value: A): String
}

object Show {

  def apply[A](implicit sh: Show[A]): Show[A] = sh

  def show[A: Show](a: A): String = Show[A].show(a)

  def fixClassName[A](c: Class[A]): String =
    c.getName().replaceAll("\\.", "_").replace("$", "")

  implicit class ShowSyntax[A: Show](a: A) {
    def show(): String = Show[A].show(a)
  }

  implicit val showString: Show[String] = s => s

  implicit def showClass[A]: Show[Class[A]] = (f: Class[A]) => fixClassName(f)
}

trait Semigroup[A] {
  def combine(x: A, y: A): A
}

object Semigroup {

  def apply[A](implicit sg: Semigroup[A]): Semigroup[A] = sg

  def combine[A: Semigroup](x: A, y: A): A = Semigroup[A].combine(x, y)

  implicit class SemigroupSyntax[A: Semigroup](x: A) { self =>
    def combine(y: A): A = Semigroup.combine(x, y)
    def |+|(y: A): A     = self.combine(y)
  }

  implicit def numericAddSG[N: Numeric[*]]: Semigroup[N] = _ + _

  implicit val strConcatSG: Semigroup[String] = _ + _
}
