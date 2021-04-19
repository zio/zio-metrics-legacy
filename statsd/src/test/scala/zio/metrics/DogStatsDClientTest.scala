package zio.metrics

import zio.clock.Clock
import zio.metrics.dogstatsd._
import zio.metrics.encoders.Encoder
import zio.test._
import zio.test.Assertion._

object DogStatsDClientTest extends DefaultRunnableSpec {
  private val port = 8900

  override def spec =
    suite("DogStatsDClient")(
      testM("Sends correct information via UDP") {
        val clientWithAgent = for {
          d <- DogStatsDClient(500, 5000, 100, Some("localhost"), Some(port))
          u <- UDPAgent(port)
        } yield (d, u)

        clientWithAgent.use {
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
              assert(timerMetric)(equalTo("TestTimer:12|ms")) &&
              assert(counterMetric)(equalTo("TestCounter:1|c|@0,9")) &&
              assert(histMetric)(equalTo("TestHistogram:1|h0,9")) &&
              assert(distributionMetric)(equalTo("TestDistribution:20|d")) &&
              assert(serviceCheckMetric)(containsString("TestServiceCheck|0|d")) &&
              assert(eventMetric)(containsString("_e{9,26}:TestEvent|something amazing happened|d:"))
            }
        }

      }
    ).provideCustomLayer(Encoder.dogstatsd ++ Clock.live)
}
