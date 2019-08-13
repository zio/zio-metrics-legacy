package zio.metrics

import scalaz.Scalaz._
import zio.{ App, IO, Task }

import scala.math.Numeric.IntIsIntegral

object DropwizardMetricsSpec extends App {

  val dropwizardMetrics = new DropwizardMetrics

  val tester: Option[Unit] => Long = (_: Option[Unit]) => System.nanoTime()

  def performTests: Task[Unit] =
    for {
      f  <- dropwizardMetrics.counter(Label("simple_counter", Array("test", "counter")))
      _  <- f(1)
      _  <- f(2)
      g  <- dropwizardMetrics.gauge(Label("simple_gauge", Array("test", "gauge")))(tester)
      _  <- g(None)
      t  <- dropwizardMetrics.timer(Label("simple_timer", Array("test", "timer")))
      t1 = t.start
      l <- IO.foreach(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(t1))
      h <- dropwizardMetrics.histogram(Label("simple_histogram", Array("test", "histogram")))
      _ <- IO.foreach(List(h(10), h(25), h(50), h(57), h(19)))(_.unit)
      m <- dropwizardMetrics.meter(Label("simple_meter", Array("test", "meter")))
      _ <- IO.foreach(1 to 5)(_ => IO.succeed(m(1)))
    } yield { println(s"time $l ns"); () }

  def run(args: List[String]) =
    performTests.either
      .map(ei => {
        printMetrics()
        ei.fold(_ => 1, _ => 0)
      })

  def printMetrics(): Unit = {
    println(
      dropwizardMetrics.registry
        .getCounters()
        .get("simple_counter")
        .getCount
    )
    println(
      dropwizardMetrics.registry
        .getGauges()
        .get("simple_gauge")
        .getValue
    )
    println(
      dropwizardMetrics.registry
        .getTimers()
        .get("simple_timer")
        .getCount
    )
    println(
      dropwizardMetrics.registry
        .getTimers()
        .get("simple_timer")
        .getMeanRate
    )
    println(
      dropwizardMetrics.registry
        .getHistograms()
        .get("simple_histogram")
        .getSnapshot
        .get75thPercentile
    )
    println(
      dropwizardMetrics.registry
        .getHistograms()
        .get("simple_histogram")
        .getSnapshot
        .get99thPercentile
    )
    println(
      dropwizardMetrics.registry
        .getHistograms()
        .get("simple_histogram")
        .getSnapshot
        .getMean
    )
    println(
      dropwizardMetrics.registry
        .getMeters()
        .get("simple_meter")
        .getMeanRate
    )
    println(
      dropwizardMetrics.registry
        .getMeters()
        .get("simple_meter")
        .getCount
    )
  }
}
