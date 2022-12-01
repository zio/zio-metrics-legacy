package zio.metrics

import zio.{ RIO, ZIO }
import zio.metrics.encoders._
import zio.test._
import zio.test.Assertion._
import zio.test.ZIOSpecDefault

object DogStatsDEncoderTest extends ZIOSpecDefault {

  override def spec =
    suite("DogStatsDEncoder")(
      test("DogStatsD Encoder encodes counters") {
        testCounter.map(tup => assert(tup)(equalTo((Some("foobar:1|c"), Some("foobar:1|c|@0.5|#metric:counter")))))
      },
      test("DogStatsD Encoder encodes gauges") {
        testGauge.map(tup => assert(tup)(equalTo((Some("foobar:1|g|#metric:gauge"), None, None))))
      },
      test("DogStatsD Encoder encodes timers") {
        testTimer.map(
          tup =>
            assert(tup)(
              equalTo((Some("foobar:1|ms"), Some("foobar:1|ms|@0.5|#metric:timer"), Some("foobar:1|ms|#metric:timer")))
            )
        )
      },
      test("DogStatsD Encoder encodes histograms") {
        for {
          res <- testHistogram
        } yield {
          assert(res._1)(isSome(equalTo("foobar:1|h"))) &&
          assert(res._2)(isSome(equalTo("foobar:1|h|@0.5|#metric:histogram"))) &&
          assert(res._3)(isSome(equalTo("foobar:1|h|#metric:histogram")))
        }
      },
      test("DogStatsD Encoder encodes meters") {
        testMeter.map(encoded => assert(encoded)(isSome(equalTo("foobar:1|m"))))
      },
      test("DogStatsD Encoder encodes sets") {
        testSet.map(encoded => assert(encoded)(isSome(equalTo("foobar:barfoo|s"))))
      },
      test("DogStatsD Encoder encodes serviceChecks") {
        for {
          tup <- testServiceCheck
        } yield {
          assert(tup._1)(
            isSome(equalTo("_sc|foobar|0|d:%d|h:host|#metric:serviceCheck|m:hello\\\\nworld\\\\nagain!".format(now)))
          ) &&
          assert(tup._2)(isSome(equalTo("_sc|foobar|1|d:%d|h:host|#metric:serviceCheck|m:wheeee!".format(now))))
        }
      },
      test("DogStatsD Encoder encodes events") {
        for {
          tup <- testEvent
        } yield {
          assert(tup._1)(
            isSome(
              equalTo(
                "_e{6,14}:foobar|derp derp derp|d:%d|h:host|k:agg_key|p:low|s:user|t:error|#metric:event".format(now)
              )
            )
          ) &&
          assert(tup._2)(
            isSome(
              equalTo(
                "_e{6,14}:foobar|derp derp\\\\nderp|d:%d|h:host|k:agg_key|p:normal|s:user|t:warning|#metric:event"
                  .format(now)
              )
            )
          )
        }
      },
      test("DogStatsD Encoder encodes distribution") {
        for {
          tup <- testDistribution
        } yield {
          assert(tup._1)(isSome(equalTo("foobar:1|d"))) &&
          assert(tup._2)(isSome(equalTo("foobar:1|d|@0.5|#metric:distribution"))) &&
          assert(tup._3)(isSome(equalTo("foobar:1|d|#metric:distribution")))
        }
      }
    ).provideLayer(Encoder.dogstatsd)

  type OptString = Option[String]

  val gaugeTag        = Tag("metric", "gauge")
  val counterTag      = Tag("metric", "counter")
  val timerTag        = Tag("metric", "timer")
  val histogramTag    = Tag("metric", "histogram")
  val serviceCheckTag = Tag("metric", "serviceCheck")
  val eventTag        = Tag("metric", "event")
  val distributeTag   = Tag("metric", "distribution")

  val encode: Metric => RIO[Encoder, OptString] = metric =>
    for {
      sde   <- ZIO.environment[Encoder]
      coded <- sde.get.encode(metric)
    } yield coded

  val testCounter: RIO[Encoder, (OptString, OptString)] = for {
    enc1 <- encode(Counter("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Counter("foobar", 1.0, 0.5, List(counterTag)))
  } yield (enc1, enc2)

  val testGauge: RIO[Encoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Gauge("foobar", 1.0, List(gaugeTag)))
    enc2 <- encode(Gauge("foobar", java.lang.Double.NEGATIVE_INFINITY, tags = List(gaugeTag)))
    enc3 <- encode(Gauge("foobar", java.lang.Double.POSITIVE_INFINITY, tags = List(gaugeTag)))
  } yield (enc1, enc2, enc3)

  val testTimer: RIO[Encoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Timer("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Timer("foobar", 1.0, 0.5, List(timerTag)))
    enc3 <- encode(Timer("foobar", 1.0, 1.001, List(timerTag)))
  } yield (enc1, enc2, enc3)

  val testHistogram: RIO[Encoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Histogram("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Histogram("foobar", 1.0, 0.5, List(histogramTag)))
    enc3 <- encode(Histogram("foobar", 1.0, 1.001, List(histogramTag)))
  } yield (enc1, enc2, enc3)

  val testDistribution: RIO[Encoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Distribution("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Distribution("foobar", 1.0, 0.5, List(distributeTag)))
    enc3 <- encode(Distribution("foobar", 1.0, 1.001, List(distributeTag)))
  } yield (enc1, enc2, enc3)

  val testMeter: RIO[Encoder, OptString] = for {
    enc <- encode(Meter("foobar", 1.0, Seq.empty[Tag]))
  } yield enc

  val testSet: RIO[Encoder, OptString] = for {
    enc <- encode(Set("foobar", "barfoo", Seq.empty[Tag]))
  } yield enc

  private val now = System.currentTimeMillis() / 1000

  val testServiceCheck: RIO[Encoder, (OptString, OptString)] = {
    val sc1 = ServiceCheck(
      "foobar",
      ServiceCheckOk,
      tags = List(serviceCheckTag),
      hostname = Some("host"),
      timestamp = Some(now),
      message = Some("hello\nworld\nagain!")
    )
    val sc2 = ServiceCheck(
      "foobar",
      ServiceCheckWarning,
      tags = List(serviceCheckTag),
      hostname = Some("host"),
      timestamp = Some(now),
      message = Some("wheeee!")
    )
    for {
      enc1 <- encode(sc1)
      enc2 <- encode(sc2)
    } yield (enc1, enc2)
  }

  val testEvent: RIO[Encoder, (OptString, OptString)] = {
    val ev1 = Event(
      "foobar",
      "derp derp derp",
      Some(now),
      Some("host"),
      Some("agg_key"),
      Some(EventPriorityLow),
      Some("user"),
      Some(EventAlertError),
      List(eventTag)
    )
    val ev2 = Event(
      "foobar",
      "derp derp\nderp",
      Some(now),
      Some("host"),
      Some("agg_key"),
      None,
      Some("user"),
      Some(EventAlertWarning),
      List(eventTag)
    )
    for {
      enc1 <- encode(ev1)
      enc2 <- encode(ev2)
    } yield (enc1, enc2)
  }

}
