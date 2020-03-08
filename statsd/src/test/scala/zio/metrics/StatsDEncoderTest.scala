package zio.metrics

import testz.{ assert, Harness, PureHarness }

import zio.{ RIO, Runtime }
import zio.console._
import zio.metrics.encoders._

object StatsDEncoderTest {

  val rt = Runtime.unsafeFromLayer(Encoder.statsd ++ Console.live)

  val encode: Metric => RIO[Encoder, Option[String]] = metric =>
    for {
      sde   <- RIO.environment[Encoder]
      coded <- sde.get.encode(metric)
    } yield coded

  val testCounter: RIO[Encoder, (Option[String], Option[String])] = for {
    enc1 <- encode(Counter("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Counter("foobar", 1.0, sampleRate = 0.5, Seq.empty[Tag]))
  } yield (enc1, enc2)

  val testGauge: RIO[Encoder, Option[String]] = for {
    enc <- encode(Gauge(name = "foobar", value = 1.0, Seq.empty[Tag]))
  } yield enc

  val testTimer: RIO[Encoder, Option[String]] = for {
    enc <- encode(Timer(name = "foobar", value = 1.0, sampleRate = 1.0, Seq.empty[Tag]))
  } yield enc

  val testMeter: RIO[Encoder, Option[String]] = for {
    enc <- encode(Meter(name = "foobar", value = 1.0, Seq.empty[Tag]))
  } yield enc

  val testSet: RIO[Encoder, Option[String]] = for {
    enc <- encode(Set(name = "foobar", value = "barfoo", Seq.empty[Tag]))
  } yield enc

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("StatsD Encoder encodes counters") { () =>
        val m = rt.unsafeRun(testCounter)
        assert(m._1 == Some("foobar:1|c") && m._2 == Some("foobar:1|c|@0.5"))
      },
      test("StatsD Encoder encodes gauges") { () =>
        val m = rt.unsafeRun(testGauge)
        assert(m == Some("foobar:1|g"))
      },
      test("StatsD Encoder encodes timers") { () =>
        val m = rt.unsafeRun(testTimer)
        assert(m == Some("foobar:1|ms"))
      },
      test("StatsD Encoder encodes meters") { () =>
        val m = rt.unsafeRun(testMeter)
        assert(m == Some("foobar:1|m"))
      },
      test("StatsD Encoder encodes sets") { () =>
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
