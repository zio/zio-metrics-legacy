package zio.metrics

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

  implicit def showClass[A]: Show[Class[_]] = (f: Class[_]) => fixClassName(f)
}
