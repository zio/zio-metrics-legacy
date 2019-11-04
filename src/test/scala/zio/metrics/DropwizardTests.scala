package zio.metrics

import zio.{ RIO, Runtime }
import zio.internal.PlatformLive
import testz.{ assert, Harness, PureHarness }
import com.codahale.metrics.{ MetricRegistry, Counter => DWCounter, Gauge => DWGauge }
import zio.metrics.typeclasses._
import zio.metrics.dropwizard._

object DropwizardTests {

  object counter {
    def inc(dwCounter: DWCounter): RIO[DropWizardCounter, Unit] = RIO.accessM(_.counter.inc(dwCounter))

    def inc(dwCounter: DWCounter, amount: Double): RIO[DropWizardCounter, Unit] =
      RIO.accessM(_.counter.inc(dwCounter, amount))
  }

  object gauge {
    def getValue(g: DWGauge[Long]): RIO[DropWizardGauge, Long] = {
      RIO.accessM(r => for {
        l <- r.gauge.getValue[Long](g)
      } yield l)
    }
  }

  val rt = Runtime(
    new DropWizardRegistry with DropWizardCounter with DropWizardGauge,
    PlatformLive.Default
  )

  val tester = () => System.nanoTime()

  val testCounter: RIO[DropWizardRegistry with DropWizardCounter, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    c   <- dwr.registry.registerCounter(Label(DropwizardTests.getClass(), Array("test", "counter")))
    _   <- counter.inc(c)
    _   <- counter.inc(c, 2.0)
    r   <- dwr.registry.getCurrent()
  } yield r

  val testGauge: RIO[DropWizardRegistry with DropWizardGauge, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    g   <- dwr.registry.registerGauge(Label("DropWizardGauge", Array("test", "gauge")), tester)
    _   <- gauge.getValue(g)
    r   <- dwr.registry.getCurrent()
  } yield r

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
          val name = MetricRegistry.name("DropWizardGauge", Array.empty[String]: _*)
          val r    = rt.unsafeRun(testGauge)
          val gs   = r.getGauges()
          val g    = if (gs.get(name) == null) Long.MaxValue else gs.get(name).getValue().asInstanceOf[Long]
          assert(g < tester())
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
