package zio.metrics

import cats.Monoid
import argonaut.Json
import argonaut.Argonaut.jEmptyObject

object instances {
  implicit val jsonMonoid: Monoid[Json] = new Monoid[Json] {
    def combine(j1: Json, j2: Json) = j1.deepmerge(j2)

    def empty = jEmptyObject
  }
}
