package zio.metrics

final case class Label[A](name: A, labels: Array[String], help: String)





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

  implicit val strConcatSG: Semigroup[String] = _ + _
}
