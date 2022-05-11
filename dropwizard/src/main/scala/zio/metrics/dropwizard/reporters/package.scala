package zio.metrics.dropwizard

import zio.{ Layer, Task, ZIO, ZLayer }

import java.util.Locale
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import java.{ util => ju }
import java.io.File

package object reporters {

  import com.codahale.metrics.MetricRegistry
  import com.codahale.metrics.MetricFilter
  import com.codahale.metrics.graphite.Graphite
  import com.codahale.metrics.graphite.GraphiteReporter
  import com.codahale.metrics.ConsoleReporter
  import com.codahale.metrics.Slf4jReporter
  import com.codahale.metrics.CsvReporter
  import com.codahale.metrics.jmx.JmxReporter
  import com.codahale.metrics.Reporter

  type Reporters = Reporters.Service

  object Reporters {
    trait Service {
      def jmx(r: MetricRegistry): Task[JmxReporter]

      def console(r: MetricRegistry): Task[ConsoleReporter]

      def slf4j(r: MetricRegistry, duration: Int, unit: TimeUnit, loggerName: String): Task[Slf4jReporter]

      def csv(r: MetricRegistry, file: File, locale: Locale): Task[Reporter]

      def graphite(r: MetricRegistry, host: String, port: Int, prefix: String): Task[GraphiteReporter]
    }

    val live: Layer[Nothing, Reporters] = ZLayer.succeed(new Service {

      def jmx(r: MetricRegistry): zio.Task[JmxReporter] = ZIO.succeed(JmxReporter.forRegistry(r).build())

      def console(r: MetricRegistry): Task[ConsoleReporter] = ZIO.succeed(
        ConsoleReporter
          .forRegistry(r)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
      )

      def slf4j(r: MetricRegistry, duration: Int, unit: TimeUnit, loggerName: String): Task[Slf4jReporter] =
        ZIO.succeed(
          Slf4jReporter
            .forRegistry(r)
            .outputTo(LoggerFactory.getLogger(loggerName))
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build()
        )

      def csv(r: MetricRegistry, file: File, locale: ju.Locale): zio.Task[Reporter] = ZIO.succeed(
        CsvReporter
          .forRegistry(r)
          .formatFor(locale)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build(file)
      )

      def graphite(r: MetricRegistry, host: String, port: Int, prefix: String): zio.Task[GraphiteReporter] =
        ZIO.succeed {
          val graphite = new Graphite(new InetSocketAddress(host, port))
          GraphiteReporter
            .forRegistry(r)
            .prefixedWith(prefix)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .filter(MetricFilter.ALL)
            .build(graphite)
        }
    })
  }
}
