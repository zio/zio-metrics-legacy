package zio.metrics

import zio.{ RIO, ZIO }
import zio.metrics.encoders._
import zio.test._
import zio.test.Assertion._
import zio.test.ZIOSpecDefault

object StatsDEncoderTest extends ZIOSpecDefault {

  override def spec =
    suite("StatsDEncoder")(
      test("StatsD Encoder encodes counters") {
        testCounter.map(
          tup =>
            assert(tup._1)(isSome(equalTo("foobar:1|c"))) &&
              assert(tup._2)(isSome(equalTo("foobar:1|c|@0.5")))
        )
      },
      test("StatsD Encoder encodes gauges") {
        testGauge.map(g => assert(g)(isSome(equalTo("foobar:1|g"))))
      },
      test("StatsD Encoder encodes timers") {
        testTimer.map(t => assert(t)(isSome(equalTo("foobar:1|ms"))))
      },
      test("StatsD Encoder encodes meters") {
        testMeter.map(m => assert(m)(isSome(equalTo("foobar:1|m"))))
      },
      test("StatsD Encoder encodes sets") {
        testSet.map(s => assert(s)(isSome(equalTo("foobar:barfoo|s"))))
      }
    ).provideLayer(Encoder.statsd)

  val encode: Metric => RIO[Encoder, Option[String]] = metric =>
    for {
      sde   <- ZIO.environment[Encoder]
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

}
