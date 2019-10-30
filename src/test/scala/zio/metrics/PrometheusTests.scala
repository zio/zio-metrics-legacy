package zio.metrics

import java.util

import zio.{ RIO, Runtime }
import testz.{ assert, Harness, PureHarness }
import io.prometheus.client.{ CollectorRegistry, Counter => PCounter }
import zio.internal.PlatformLive
import zio.metrics.typeclasses._
import zio.metrics.prometheus._

import java.io.StringWriter
import io.prometheus.client.exporter.common.TextFormat

object PrometheusTests {

  object counter {
    def inc(pCounter: PCounter): RIO[PrometheusCounter, Unit] = RIO.accessM(_.counter.inc(pCounter))

    def inc(pCounter: PCounter, amount: Double): RIO[PrometheusCounter, Unit] =
      RIO.accessM(_.counter.inc(pCounter, amount))
  }

  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter,
    PlatformLive.Default
  )

  def write004(r: CollectorRegistry): String = {
    val writer = new StringWriter
    TextFormat.write004(writer, r.metricFamilySamples)
    writer.toString
  }

  val testCounter: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(Label("simple_counter", Array.empty[String]))
    _  <- counter.inc(c)
    _  <- counter.inc(c, 2.0)
    r  <- pr.registry.getCurrent()
  } yield r

  /*val testGauge: (Option[Double] => Double) => Task[Unit] = (f: Option[Double] => Double) =>
    for {
      g <- prometheusMetrics.gauge(Label("simple_gauge", Array.empty[String]))(f)
      _ <- g(5.0.some)
      b <- g((-3.0).some)
    } yield b*/

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_counter")
        val r = rt.unsafeRun(testCounter)
        println(s"registry: ${write004(r)}")
        val counter = r
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        assert(counter == 3.0)
      }
      /*test("gauge returns latest value") { () =>
        val tester: Option[Double] => Double = (op: Option[Double]) => op.getOrElse(0.0)
        unsafeRun(testGauge(tester))
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_gauge")
        val a1 = prometheusMetrics.registry
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value

        assert(a1 == 2.0)
      }*/
    )
  }

  val harness: Harness[PureHarness.Uses[Unit]] =
    PureHarness.makeFromPrinter((result, name) => {
      println(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result")
    })

  def main(args: Array[String]): Unit =
    tests(harness)((), Nil).print()
}
