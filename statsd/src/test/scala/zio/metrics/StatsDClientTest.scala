package zio.metrics

import zio.ZIO
import zio.clock.Clock
import zio.metrics.encoders._
import zio.metrics.statsd._
import zio.test._
import zio.test.Assertion._
import zio.duration._
import zio.test.TestAspect.timeout
import zio.ZManaged

object StatsDClientTest extends DefaultRunnableSpec {
  private val port = 8922

  override def spec =
    suite("StatsDClient")(
      testM("sends correct metrics via UDP") {
        val clientWithAgent: ZManaged[Client.ClientEnv, Throwable, (StatsDClient, UDPAgent)] = for {
          u <- UDPAgent(port)
          d <- StatsDClient(500, 5000, 100, Some("localhost"), Some(port))
        } yield (d, u)

        clientWithAgent.use {
          case (client, agent) =>
            for {
              _            <- ZIO.sleep(10.seconds)
              _            <- client.increment("TestCounter", 0.9)
              clientMetric <- agent.nextReceivedMetric
              _            <- client.timer("TestTimer", 0.44, 0.9)
              timerMetric  <- agent.nextReceivedMetric
            } yield {
              assert(clientMetric)(equalTo("TestCounter:1|c|@0.9")) &&
              assert(timerMetric)(equalTo("TestTimer:0.44|ms|@0.9"))
            }
        }
      }
    ).provideCustomLayer(Encoder.statsd ++ Clock.live) @@ timeout(180.seconds)

}
