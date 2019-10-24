package zio.metrics

import zio.{ Runtime, RIO }
import zio.internal.PlatformLive
import testz.{ assert, Harness, PureHarness }
import com.codahale.metrics.{MetricRegistry, Counter => DWCounter}
import zio.metrics.dropwizard._

object DropwizardTests {

  object counter {
    def register[A: Show](registry: MetricRegistry, label: Label[A]): RIO[DropWizardCounter, DWCounter] =
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
    _   <- dwc.counter.inc(c)
    _   <- dwc.counter.inc(c, 2.0)
  } yield r

  //val rt: Runtime[DropWizardEnvironment] = Runtime(MetricRegistry with DWCounter, PlatformLive.Default)

  def tests[T](harness: Harness[T]): T = {
    import harness._
    section(
      test("counter increases by `inc` amount") { () => {
        /*val rt = ZIO.runtime[DropWizardEnvironment].provideSome[DropWizardEnvironment] { base =>
          new MetricRegistry with DWCounter {
            override val registry = base.registry
            override val counter = base.counter
          }
        }*/
        val name = MetricRegistry.name(DropwizardTests.getClass().getName(), Array("test", "counter"): _*)
        val r = rt.unsafeRun(testCounter)
        val cs = r.getCounters()
        println(cs.values().forEach(println))
        val c = cs.get(name).getCount
        println(c)
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
