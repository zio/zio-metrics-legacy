package zio.metrics

import testz.{ assert, Harness, PureHarness }

import zio.{RIO, Runtime }
import zio.console._
import zio.internal.PlatformLive

import zio.metrics.statsd._

object StatsdTest {

  val rt = Runtime(
    new StatsdEncoder with Console.Live,
    PlatformLive.Default
  )

  val testCounter: RIO[StatsdEncoder, (Option[String], Option[String])] = for {
    sde  <- RIO.environment[StatsdEncoder]
    enc1 <- sde.encoder.encode(Counter("foobar", 1.0, 1.0, Seq.empty[String]))
    enc2 <- sde.encoder.encode(Counter("foobar", 1.0, sampleRate = 0.5, Seq.empty[String]))
  } yield (enc1, enc2)


  val testGauge: RIO[StatsdEncoder, Option[String]] = for {
    sde  <- RIO.environment[StatsdEncoder]
    enc  <- sde.encoder.encode(Gauge(name = "foobar", value = 1.0, Seq.empty[String]))
  } yield enc

  val testTimer: RIO[StatsdEncoder, Option[String]] = for {
    sde  <- RIO.environment[StatsdEncoder]
    enc  <- sde.encoder.encode(Timer(name = "foobar", value = 1.0, sampleRate = 1.0, Seq.empty[String]))
  } yield enc

  val testMeter: RIO[StatsdEncoder, Option[String]] = for {
    sde  <- RIO.environment[StatsdEncoder]
    enc  <- sde.encoder.encode(Meter(name = "foobar", value = 1.0, Seq.empty[String]))
  } yield enc

  val testSet: RIO[StatsdEncoder, Option[String]] = for {
    sde  <- RIO.environment[StatsdEncoder]
    enc  <- sde.encoder.encode(Set(name = "foobar", value = "barfoo", Seq.empty[String]))
  } yield enc

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("Statsd Encoder encodes counters") { () =>
        val m = rt.unsafeRun(testCounter)
        assert(m._1 == Some("foobar:1|c") && m._2 == Some("foobar:1|c|@0.5"))
      },
      test("Statsd Encoder encodes gauges") { () =>
        val m = rt.unsafeRun(testGauge)
        assert(m == Some("foobar:1|g"))
      },
      test("Statsd Encoder encodes timers") { () =>
        val m = rt.unsafeRun(testTimer)
        assert(m == Some("foobar:1|ms"))
      },
      test("Statsd Encoder encodes meters") { () =>
        val m = rt.unsafeRun(testMeter)
        assert(m == Some("foobar:1|m"))
      },
      test("Statsd Encoder encodes sets") { () =>
        val m = rt.unsafeRun(testSet)
        assert(m == Some("foobar:barfoo|s"))
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
