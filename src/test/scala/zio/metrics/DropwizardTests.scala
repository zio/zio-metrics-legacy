package zio.metrics

import zio.{ RIO, Runtime }
import zio.internal.PlatformLive
import testz.{ assert, Harness, PureHarness }
import com.codahale.metrics.{ MetricRegistry, Counter => DWCounter, Gauge => DWGauge }
import zio.metrics.dropwizard._

object DropwizardTests {

  object counter {
    def register[A: Show](
      registry: MetricRegistry,
      label: Label[A]
    ): RIO[DropWizardCounter, (MetricRegistry, DWCounter)] =
      RIO.accessM(_.counter.register(registry, label))

    def inc(dwCounter: DWCounter): RIO[DropWizardCounter, Unit] = RIO.accessM(_.counter.inc(dwCounter))

    def inc(dwCounter: DWCounter, amount: Double): RIO[DropWizardCounter, Unit] =
      RIO.accessM(_.counter.inc(dwCounter, amount))
  }

  object gauge {
    type RegistryGaugeT[B] = (MetricRegistry, B)
    def register[L: Show, A, B](
      registry: MetricRegistry,
      label: Label[L],
      f: A => B
    ): RIO[DropWizardGauge, (MetricRegistry, A => DWGauge[B])] =
      RIO.accessM(_.gauge.register(registry, label, f))

    def inc[A, B](g: A => DWGauge[B], a: A): RIO[DropWizardGauge, B] =
      RIO.accessM(_.gauge.inc(g, a))
  }

  val rt = Runtime(
    new DropWizardRegistry with DropWizardCounter with DropWizardGauge,
    PlatformLive.Default
  )

  val tester = (_: Long) => System.nanoTime()

  val testCounter: RIO[DropWizardRegistry with DropWizardCounter, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    r   <- dwr.registry.build()
    c   <- counter.register(r, Label(DropwizardTests.getClass(), Array("test", "counter")))
    _   <- counter.inc(c._2)
    _   <- counter.inc(c._2, 2.0)
  } yield c._1

  val testGauge: RIO[DropWizardRegistry with DropWizardGauge, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    r   <- dwr.registry.build()
    g   <- gauge.register(r, Label(DropwizardTests.getClass(), Array("test", "gauge")), tester)
    _   <- gauge.inc[Long, Long](g._2, 3)
  } yield g._1

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () =>
        {
          val name = MetricRegistry.name(DropwizardTests.getClass().getName(), Array.empty[String]: _*)
          val r    = rt.unsafeRun(testCounter)
          val cs   = r.getCounters()
          val c    = if (cs.get(name) == null) 0 else cs.get(name).getCount
          assert(c == 3d)
        }
      },
      test("gauge increases in time") { () =>
        {
          val name = MetricRegistry.name(DropwizardTests.getClass().getName(), Array.empty[String]: _*)
          val r    = rt.unsafeRun(testGauge)
          val gs   = r.getGauges()
          val g    = if (gs.get(name) == null) 0 else gs.get(name).getValue().asInstanceOf[Long]
          assert(g < tester(0L))
        }
      }
    )
  }

  val harness: Harness[PureHarness.Uses[Unit]] =
    PureHarness.makeFromPrinter((result, name) => {
      println(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result")
    })

  def main(args: Array[String]): Unit =
    tests(harness)((), Nil).print()
}
