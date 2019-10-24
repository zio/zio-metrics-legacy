package zio.metrics

import zio.{ Runtime, RIO }
import zio.internal.PlatformLive
import testz.{ assert, Harness, PureHarness }
import com.codahale.metrics.{MetricRegistry, Counter => DWCounter}
import zio.metrics.dropwizard._

object DropwizardTests {

  object counter {
    def register[A: Show](registry: MetricRegistry, label: Label[A]): RIO[DropWizardCounter, (MetricRegistry, DWCounter)] =
      RIO.accessM(_.counter.register(registry, label))

    def inc(dwCounter: DWCounter): RIO[DropWizardCounter, Unit] = RIO.accessM(_.counter.inc(dwCounter))

    def inc(dwCounter: DWCounter, amount: Double): RIO[DropWizardCounter, Unit] = RIO.accessM(_.counter.inc(dwCounter, amount))
  }

  val rt = Runtime(
    new DropWizardRegistry with DropWizardCounter,
    PlatformLive.Default
  )

  val testCounter: RIO[DropWizardRegistry with DropWizardCounter, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    dwc <- RIO.environment[DropWizardCounter]
    r   <- dwr.registry.build()
    c   <- dwc.counter.register(r, Label(DropwizardTests.getClass(), Array("test", "counter")))
    _   <- dwc.counter.inc(c._2)
    _   <- dwc.counter.inc(c._2, 2.0)
  } yield c._1

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () => {
        val name = MetricRegistry.name(DropwizardTests.getClass().getName(), Array.empty[String]: _*)
        val r = rt.unsafeRun(testCounter)
        val cs = r.getCounters()
        val c = if (cs.get(name) == null) 0 else cs.get(name).getCount
        assert(c == 3D)
      }
      },
    )
  }

  val harness: Harness[PureHarness.Uses[Unit]] =
    PureHarness.makeFromPrinter((result, name) => {
      println(s"${name.reverse.mkString("[\"", "\"->\"", "\"]:")} $result")
    })

  def main(args: Array[String]): Unit =
    tests(harness)((), Nil).print()
}
