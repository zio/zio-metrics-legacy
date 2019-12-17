package zio.metrics.dropwizard.typeclasses

//import cats.Monoid
import argonaut.Json
import argonaut.Argonaut.jEmptyObject

trait Monoid[A] { //extends Semigroup[A] {
  def empty: A

  def combine(x: A, y: A): A
}
object Monoid {

  def apply[A](implicit m: Monoid[A]) = m

  def empty[A: Monoid]: A = Monoid[A].empty

  def combine[A: Monoid](x: A, y: A): A = Monoid[A].combine(x, y)

  implicit class MonoidSyntax[A: Monoid](x: A) { self => // Ops is somtimes called *syntax
    def combine(y: A): A = Monoid.combine(x, y)
    def |+|(y: A): A     = self.combine(y)
  }

  implicit val jsonMonoid: Monoid[Json] = new Monoid[Json] {
    def combine(j1: Json, j2: Json) = j1.deepmerge(j2)

    def empty = jEmptyObject
  }

  implicit val stringMonoid: Monoid[String] = new Monoid[String] {
    def combine(j1: String, j2: String) = s"$j1\n$j2"

    def empty = ""
  }
}

trait Foldable[F[_]] {
  def foldMap[A, B](fa: F[A])(f: A => B)(implicit F: Monoid[B]): B // map-reduce
  def fold[A](fa: F[A])(z: A)(f: (A, A) => A): A
  /*def foldLeft[A, B](fa: F[A], z: B)(f: (A, B) => B): B
  def foldRight[A, B](fa: F[A], z: ⇒ B)(f: (A, => B) => B): B*/
}

object Foldable {

  def apply[F[_]](implicit f: Foldable[F]) = f

  implicit val listFoldable = new Foldable[List] {
    def fold[A](fa: List[A])(z: A)(f: (A, A) => A): A = fa.fold(z)(f)

    def foldMap[A, B](fa: List[A])(f: A => B)(implicit F: Monoid[B]): B =
      fa.map(f).fold(Monoid[B].empty)(Monoid[B].combine)
  }

  implicit val arrayFoldable = new Foldable[Array] {
    def fold[A](fa: Array[A])(z: A)(f: (A, A) => A): A = fa.fold(z)(f)

    def foldMap[A, B](fa: Array[A])(f: A => B)(implicit F: Monoid[B]): B =
      fa.map(f).fold(Monoid[B].empty)(Monoid[B].combine)
  }
}
