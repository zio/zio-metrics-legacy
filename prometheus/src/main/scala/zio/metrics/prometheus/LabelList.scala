package zio.metrics.prometheus

/**
 * A list of strings whose size is statically known.
 *
 * This is used to have strongly typed labelled metrics.
 *
 * Inspired by shapeless `HList`.
 */
sealed trait LabelList extends Product with Serializable {
  def toList: List[String] = this match {
    case LabelList.LNil              => Nil
    case LabelList.LCons(head, tail) => head :: tail.toList
  }
}

object LabelList {
  case object LNil extends LabelList {
    def ::(s: String): LCons[LNil] = LCons(s, LNil)
  }
  final case class LCons[+T <: LabelList](head: String, tail: T) extends LabelList {
    def ::(s: String): LCons[LCons[T]] = LCons(s, this)
  }

  type LNil = LNil.type
  type L0   = LNil
  type L1   = LCons[L0]
  type L2   = LCons[L1]
  type L3   = LCons[L2]
  type L4   = LCons[L3]
  type L5   = LCons[L4]
  type L6   = LCons[L5]
  type L7   = LCons[L6]
  type L8   = LCons[L7]
  type L9   = LCons[L8]
  type L10  = LCons[L9]
  type L11  = LCons[L10]
  type L12  = LCons[L11]
  type L13  = LCons[L12]
  type L14  = LCons[L13]
  type L15  = LCons[L14]
  type L16  = LCons[L15]
  type L17  = LCons[L16]
  type L18  = LCons[L17]
  type L19  = LCons[L18]
  type L20  = LCons[L19]
  type L21  = LCons[L20]
  type L22  = LCons[L21]

  /** Returns a list the same size as `L` with the given string repeated each time. */
  def repeat[L <: LabelList](s: String)(implicit repeat: Repeat[L]): L = repeat(s)

  sealed trait Repeat[L <: LabelList] {
    def apply(s: String): L
  }
  object Repeat {
    implicit val repeatNil: Repeat[LNil] = new Repeat[LNil] {
      def apply(s: String): LNil = LNil
    }
    implicit def repeatCons[T <: LabelList](implicit tail: Repeat[T]): Repeat[LCons[T]] =
      new Repeat[LCons[T]] {
        def apply(s: String): LCons[T] = LCons(s, tail(s))
      }
  }
}
