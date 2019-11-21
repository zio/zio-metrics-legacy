package zio.metrics

import zio.Task
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.Locale

trait Reporters {
  val reporter: Reporters.Service[Nothing, Any]
}

object Reporters {
  trait Service[-M, +R] {
    def jmx(r: M): Task[R]

    def console(r: M): Task[R]

    def slf4j(r: M, duration: Int, unit: TimeUnit, loggerName: String): Task[R]

    def csv(r: M, file: File, locale: Locale): Task[R]

    def graphite(r: M, host: String, port: Int, prefix: String): Task[R]
  }
}
