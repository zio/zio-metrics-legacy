package zio.metrics

import zio.Task

trait Timer {
  val timer: Timer.Service[Nothing, _]
}

object Timer {
  trait Service[-R, C] {

    def start(t: R): Task[C]

    def stop(c: C): Task[Long]

  }
}
