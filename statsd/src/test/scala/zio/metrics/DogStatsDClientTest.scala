package zio.metrics

import zio.Clock
import zio.metrics.dogstatsd._
import zio.metrics.encoders.Encoder
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{ flaky, forked, timeout }

import zio._
import zio.test.ZIOSpecDefault

object DogStatsDClientTest extends ZIOSpecDefault {
  val rand         = new scala.util.Random()
  val r            = rand.nextInt(100)
  private val port = 8900 + (if (r < 10) r + 10 else r)

  override def spec =
    suite("DogStatsDClient")(
      test("Sends correct information via UDP") {
        val clientWithAgent = for {
          d <- DogStatsDClient(500, 5000, 100, Some("localhost"), Some(port), Some("zio"))
          u <- UDPAgent(port)
        } yield (d, u)
        ZIO.scoped {
          clientWithAgent.flatMap {
            case (client, agent) =>
              for {
                _                  <- client.timer("TestTimer", 12)
                timerMetric        <- agent.nextReceivedMetric
                _                  <- client.increment("TestCounter", 0.9)
                counterMetric      <- agent.nextReceivedMetric
                _                  <- client.histogram("TestHistogram", 1)
                histMetric         <- agent.nextReceivedMetric
                _                  <- client.distribution("TestDistribution", 20)
                distributionMetric <- agent.nextReceivedMetric
                _                  <- client.serviceCheck("TestServiceCheck", ServiceCheckOk)
                serviceCheckMetric <- agent.nextReceivedMetric
                _                  <- client.event("TestEvent", "something amazing happened")
                eventMetric        <- agent.nextReceivedMetric
              } yield {
                assert(timerMetric)(equalTo("zio.TestTimer:12|ms")) &&
                assert(counterMetric)(equalTo("zio.TestCounter:1|c|@0.9")) &&
                assert(histMetric)(equalTo("zio.TestHistogram:1|h0.9")) &&
                assert(distributionMetric)(equalTo("zio.TestDistribution:20|d")) &&
                assert(serviceCheckMetric)(containsString("zio.TestServiceCheck|0|d")) &&
                assert(eventMetric)(containsString("_e{13,26}:zio.TestEvent|something amazing happened|d:"))
              }
          }
        }
      }
    ).provideCustomLayer(Encoder.dogstatsd ++ Clock.live) @@ forked @@ timeout(10.seconds) @@ flaky(5)
}
