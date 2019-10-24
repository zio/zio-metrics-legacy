package zio.metrics

//import java.util

import zio.DefaultRuntime //, RIO, Task }
//import testz.{ assert, Harness, PureHarness, Result }

object PrometheusTests extends DefaultRuntime {

  /*object counter extends Registry with Counter {
    def register[A: Show](label: Label[A]): RIO[PrometheusRegistry with PrometheusCounter, Counter.Service[PrometheusRegistry]] =
      RIO.accessM(_.counter.register(label))

    def inc(): RIO[PrometheusRegistry with PrometheusCounter, Unit] = RIO.accessM(_.counter.inc())

    def inc(amount: Long): RIO[PrometheusRegistry with PrometheusCounter, Unit] = RIO.accessM(_.counter.inc(amount))
  }

  val testCounter: Task[Unit] = for {
    f <- counter(Label("simple_counter", Array("test", "prod")))
    _ <- f(1)
    b <- f(2)
  } yield b

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
        unsafeRun(testCounter)
        val set: util.Set[String] = new util.HashSet[String]()
        set.add("simple_counter")
        val counter = prometheusMetrics.registry
          .filteredMetricFamilySamples(set)
          .nextElement()
          .samples
          .get(0)
          .value
        assert(counter == 3.0)
      },
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
    tests(harness)((), Nil).print()*/
}
