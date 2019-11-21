package zio.metrics

import zio.{ RIO, Runtime }
import zio.internal.PlatformLive
import testz.{ assert, Harness, PureHarness }
import com.codahale.metrics.{ MetricRegistry}
import zio.metrics.dropwizard._

object DropWizardTests {

  val rt = Runtime(
    new DropWizardRegistry with DropWizardCounter with DropWizardGauge with DropWizardHistogram
      with DropWizardMeter with DropWizardTimer,
    PlatformLive.Default
  )

  val tester = () => System.nanoTime()

  val testCounter: RIO[DropWizardRegistry with DropWizardCounter, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    c   <- dwr.registry.registerCounter(Label(DropWizardTests.getClass(), Array("test", "counter")))
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

  val testHistogram: RIO[DropWizardRegistry with DropWizardHistogram, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    h   <- dwr.registry.registerHistogram(Label("DropWizardHistogram", Array("test", "histogram")))
    _   <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(histogram.update(h, _))
    r   <- dwr.registry.getCurrent()
  } yield r

  val testMeter: RIO[DropWizardRegistry with DropWizardMeter, MetricRegistry] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    m   <- dwr.registry.registerMeter(Label("DropWizardMeter", Array("test", "meter")))
    _   <- RIO.foreach(Seq(1L, 2L, 3L, 4L, 5L))(meter.mark(m, _))
    r   <- dwr.registry.getCurrent()
  } yield r

  val testTimer: RIO[DropWizardRegistry with DropWizardTimer, (MetricRegistry, List[Long])] = for {
    dwr <- RIO.environment[DropWizardRegistry]
    r   <- dwr.registry.getCurrent()
    t   <- dwr.registry.registerTimer(Label("DropWizardTimer", Array("test", "timer")))
    ctx <- timer.start(t)
    l <- RIO.foreach(
          List(
            Thread.sleep(1000L),
            Thread.sleep(1400L),
            Thread.sleep(1200L)
          )
        )(_ => timer.stop(ctx))
 } yield (r, l)

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("counter increases by `inc` amount") { () =>
        val name = MetricRegistry.name(DropWizardTests.getClass().getName(), Array.empty[String]: _*)
        val r    = rt.unsafeRun(testCounter)
        val cs   = r.getCounters()
        val c    = if (cs.get(name) == null) 0 else cs.get(name).getCount
        assert(c == 3d)
      },
      test("gauge increases in time") { () =>
        val name = MetricRegistry.name("DropWizardGauge", Array.empty[String]: _*)
        val r    = rt.unsafeRun(testGauge)
        val gs   = r.getGauges()
        val g    = if (gs.get(name) == null) Long.MaxValue else gs.get(name).getValue().asInstanceOf[Long]
        assert(g < tester())
      },
      test("histogram increases in time") { () =>
        val name = MetricRegistry.name("DropWizardHistogram", Array.empty[String]: _*)
        val r    = rt.unsafeRun(testHistogram)
        val perc75th = r
          .getHistograms()
          .get(MetricRegistry.name(name))
          .getSnapshot
          .get75thPercentile

        assert(perc75th == 53.5)
      },
      test("Meter count and mean rate are within bounds") { () =>
        val name = MetricRegistry.name("DropWizardMeter", Array.empty[String]: _*)
        val r   = rt.unsafeRun(testMeter)
        val count = r
          .getMeters()
          .get(MetricRegistry.name(name))
          .getCount

        val meanRate = r
          .getMeters()
          .get(MetricRegistry.name(name))
          .getMeanRate

        println(s"count: $count, meanRate: $meanRate")
        assert(count == 15 && meanRate > 300 && meanRate < 2000)
      },
      test("Timer called 3 times") { () =>
        val name = MetricRegistry.name("DropWizardTimer", Array.empty[String]: _*)
        val r   = rt.unsafeRun(testTimer)
        val count = r._1
          .getTimers()
          .get(MetricRegistry.name(name))
          .getCount

        println(r._2)

        assert(count == r._2.size && count == 3)
      },
      test("Timer mean rate for 6 calls within bounds") { () =>
        val name = MetricRegistry.name("DropWizardTimer", Array.empty[String]: _*)
        val r   = rt.unsafeRun(testTimer)
        val meanRate = r._1
          .getTimers()
          .get(MetricRegistry.name(name))
          .getMeanRate

        assert(meanRate > 0.78 && meanRate < 0.84)
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
