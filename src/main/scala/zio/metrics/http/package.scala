package zio.metrics

import argonaut.Argonaut.jEmptyObject
import argonaut.Json
import scalaz.Monoid

package object http {

  implicit val jsonMonoid: Monoid[Json] = new Monoid[Json] {
    override def zero: Json                          = jEmptyObject
    override def append(j1: Json, j2: => Json): Json = j1.deepmerge(j2)
  }

}
