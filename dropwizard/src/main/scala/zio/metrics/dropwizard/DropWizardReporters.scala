package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Reporters
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.MetricFilter
import com.codahale.metrics.graphite.Graphite
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.Slf4jReporter
import com.codahale.metrics.CsvReporter
import com.codahale.metrics.jmx.JmxReporter
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import com.codahale.metrics.Reporter
import java.{ util => ju }
import java.io.File

trait DropWizardReporters extends Reporters {
  val reporter = new Reporters.Service[MetricRegistry, Reporter] {
    override def jmx(r: MetricRegistry): zio.Task[JmxReporter] = Task(JmxReporter.forRegistry(r).build())

    override def console(r: MetricRegistry): Task[ConsoleReporter] = Task(
      ConsoleReporter
        .forRegistry(r)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build()
    )

    override def slf4j(r: MetricRegistry, duration: Int, unit: TimeUnit, loggerName: String): Task[Slf4jReporter] =
      Task(
        Slf4jReporter
          .forRegistry(r)
          .outputTo(LoggerFactory.getLogger(loggerName))
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
      )

    override def csv(r: MetricRegistry, file: File, locale: ju.Locale): zio.Task[Reporter] = Task(
      CsvReporter
        .forRegistry(r)
        .formatFor(locale)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build(file)
    )

    override def graphite(r: MetricRegistry, host: String, port: Int, prefix: String): zio.Task[GraphiteReporter] =
      Task {
        val graphite = new Graphite(new InetSocketAddress(host, port))
        GraphiteReporter
          .forRegistry(r)
          .prefixedWith(prefix)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .filter(MetricFilter.ALL)
          .build(graphite)
      }
  }
}

object DropWizardReporters extends DropWizardReporters
