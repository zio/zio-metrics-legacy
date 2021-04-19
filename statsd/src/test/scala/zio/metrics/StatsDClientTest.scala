package zio.metrics

import zio.clock.Clock
import zio.metrics.encoders._
import zio.metrics.statsd._
import zio.test._
import zio.test.Assertion._

object StatsDClientTest extends DefaultRunnableSpec {
  private val port = 8922

  override def spec =
    suite("StatsDClient")(
      testM("sends correct metrics via UDP") {
        val clientWithAgent = for {
          d <- StatsDClient(500, 5000, 100, Some("localhost"), Some(port))
          u <- UDPAgent(port)
        } yield (d, u)

        clientWithAgent.use {
          case (client, agent) =>
            for {
              _            <- client.increment("TestCounter", 0.9)
              clientMetric <- agent.nextReceivedMetric
              _            <- client.timer("TestTimer", 0.44, 0.9)
              timerMetric  <- agent.nextReceivedMetric
            } yield {
              assert(clientMetric)(equalTo("TestCounter:1|c|@0,9")) &&
              assert(timerMetric)(equalTo("TestTimer:0,44|ms|@0,9"))
            }
        }
      }
    ).provideCustomLayer(Encoder.statsd ++ Clock.live)

}
