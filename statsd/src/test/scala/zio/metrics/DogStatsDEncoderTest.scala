package zio.metrics

import testz.{ assert, Harness, PureHarness }

import zio.{ RIO, Runtime }
import zio.console._
import zio.internal.PlatformLive

import zio.metrics.dogstatsd._

object DogStatsDEncoderTest {

  type OptString = Option[String]

  val rt = Runtime(
    new DogStatsDEncoder with Console.Live,
    PlatformLive.Default
  )

  val gaugeTag        = Tag("metric", "gauge")
  val counterTag      = Tag("metric", "counter")
  val timerTag        = Tag("metric", "timer")
  val histogramTag    = Tag("metric", "histogram")
  val serviceCheckTag = Tag("metric", "serviceCheck")
  val eventTag        = Tag("metric", "event")

  val encode: Metric => RIO[DogStatsDEncoder, OptString] = metric =>
    for {
      sde   <- RIO.environment[DogStatsDEncoder]
      coded <- sde.encoder.encode(metric)
    } yield coded

  val testCounter: RIO[DogStatsDEncoder, (OptString, OptString)] = for {
    enc1 <- encode(Counter("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Counter("foobar", 1.0, 0.5, List(counterTag)))
  } yield (enc1, enc2)

  val testGauge: RIO[DogStatsDEncoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Gauge("foobar", 1.0, List(gaugeTag)))
    enc2 <- encode(Gauge("foobar", java.lang.Double.NEGATIVE_INFINITY, tags = List(gaugeTag)))
    enc3 <- encode(Gauge("foobar", java.lang.Double.POSITIVE_INFINITY, tags = List(gaugeTag)))
  } yield (enc1, enc2, enc3)

  val testTimer: RIO[DogStatsDEncoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Timer("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Timer("foobar", 1.0, 0.5, List(timerTag)))
    enc3 <- encode(Timer("foobar", 1.0, 1.001, List(timerTag)))
  } yield (enc1, enc2, enc3)

  val testHistogram: RIO[DogStatsDEncoder, (OptString, OptString, OptString)] = for {
    enc1 <- encode(Histogram("foobar", 1.0, 1.0, Seq.empty[Tag]))
    enc2 <- encode(Histogram("foobar", 1.0, 0.5, List(histogramTag)))
    enc3 <- encode(Histogram("foobar", 1.0, 1.001, List(histogramTag)))
  } yield (enc1, enc2, enc3)

  val testMeter: RIO[DogStatsDEncoder, OptString] = for {
    enc <- encode(Meter("foobar", 1.0, Seq.empty[Tag]))
  } yield enc

  val testSet: RIO[DogStatsDEncoder, OptString] = for {
    enc <- encode(Set("foobar", "barfoo", Seq.empty[Tag]))
  } yield enc

  val now = System.currentTimeMillis() / 1000
  val testServiceCheck: RIO[DogStatsDEncoder, (OptString, OptString)] = {
    val sc1 = ServiceCheck(
      "foobar",
      DogStatsDEncoder.SERVICE_CHECK_OK,
      tags = List(serviceCheckTag),
      hostname = Some("host"),
      timestamp = Some(now),
      message = Some("hello\nworld\nagain!")
    )
    val sc2 = ServiceCheck(
      "foobar",
      DogStatsDEncoder.SERVICE_CHECK_OK,
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

  val testEvent: RIO[DogStatsDEncoder, (OptString, OptString)] = {
    val ev1 = Event(
      "foobar", "derp derp derp", Some(now), Some("host"), Some("agg_key"),
      Some(DogStatsDEncoder.EVENT_PRIORITY_LOW), Some("user"),
      Some(DogStatsDEncoder.EVENT_ALERT_TYPE_ERROR), List(eventTag)
    )
    val ev2 = Event(
      "foobar", "derp derp\nderp", Some(now), Some("host"), Some("agg_key"),
      None, Some("user"), Some(DogStatsDEncoder.EVENT_ALERT_TYPE_ERROR),
      List(eventTag)
    )
    for {
      enc1 <- encode(ev1)
      enc2 <- encode(ev2)
    } yield (enc1, enc2)
  }

  def tests[T](harness: Harness[T]): T = {
    import harness._

    section(
      test("Statsd Encoder encodes counters") { () =>
        val m = rt.unsafeRun(testCounter)
        assert(m._1 == Some("foobar:1|c") && m._2 == Some("foobar:1|c|@0.5|#metric:counter"))
      },
      test("Statsd Encoder encodes gauges") { () =>
        val m = rt.unsafeRun(testGauge)
        assert(m._1 == Some("foobar:1|g|#metric:gauge") && m._2 == None && m._3 == None)
      },
      test("Statsd Encoder encodes timers") { () =>
        val m = rt.unsafeRun(testTimer)
        assert(
          m._1 == Some("foobar:1|ms") && m._2 == Some("foobar:1|ms|@0.5|#metric:timer")
            && m._3 == Some("foobar:1|ms|#metric:timer")
        )
      },
      test("Statsd Encoder encodes histograms") { () =>
        val m = rt.unsafeRun(testHistogram)
        assert(
          m._1 == Some("foobar:1|h") && m._2 == Some("foobar:1|h|@0.5|#metric:histogram")
            && m._3 == Some("foobar:1|h|#metric:histogram")
        )
      },
      test("Statsd Encoder encodes meters") { () =>
        val m = rt.unsafeRun(testMeter)
        assert(m == Some("foobar:1|m"))
      },
      test("Statsd Encoder encodes sets") { () =>
        val m = rt.unsafeRun(testSet)
        assert(m == Some("foobar:barfoo|s"))
      },
      test("Statsd Encoder encodes serviceChecks") { () =>
        val m = rt.unsafeRun(testServiceCheck)
        assert(
          m._1 == Some("_sc|foobar|0|d:%d|h:host|#metric:serviceCheck|m:hello\\\\nworld\\\\nagain!".format(now))
            &&
              m._2 == Some("_sc|foobar|0|d:%d|h:host|#metric:serviceCheck|m:wheeee!".format(now))
        )
      },
      test("Statsd Encoder encodes events") { () =>
        val m = rt.unsafeRun(testEvent)
        println(m)
        assert(
          m._1 == Some("_e{6,14}:foobar|derp derp derp|d:%d|h:host|k:agg_key|p:low|s:user|t:error|#metric:event".format(now))
            &&
              m._2 == Some("_e{6,14}:foobar|derp derp\\\\nderp|d:%d|h:fart|k:agg_key|p:normal|s:user|t:error|#metric:event".format(now))
        )
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
