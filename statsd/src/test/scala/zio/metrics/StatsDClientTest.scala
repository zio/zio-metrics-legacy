package zio.metrics

import zio.clock.Clock
import zio.console.putStrLn
import zio.metrics.encoders._
import zio.metrics.statsd._
import zio.test._
import zio.test.Assertion._
import zio.duration._
import zio.test.TestAspect.{ flaky, timeout }
import zio.URIO

object StatsDClientTest extends DefaultRunnableSpec {
  private val port = 8922

  override def spec =
    suite("StatsDClient")(
      testM("sends correct metrics via UDP") {
        val clientWithAgent = (
          for {
            u <- UDPAgent(port)
            d <- StatsDClient(500, 5000, 100, Some("localhost"), Some(port))
            _ <- putStrLn("Make is ready").toManaged(_ => URIO.unit)
          } yield (d, u)
        )

        clientWithAgent.use {
          case (client, agent) =>
            for {
              _            <- putStrLn("Starting use")
              _            <- client.increment("TestCounter", 0.9, false)
              clientMetric <- agent.nextReceivedMetric
              _            <- putStrLn("Testing timer")
              _            <- client.timer("TestTimer", 0.44, 0.9)
              timerMetric  <- agent.nextReceivedMetric
              _            <- putStrLn("Finished use")
            } yield {
              assert(clientMetric)(equalTo("TestCounter:1|c|@0.9")) &&
              assert(timerMetric)(equalTo("TestTimer:0.44|ms|@0.9"))
            }
        }
      }.provideCustomLayer(Encoder.statsd ++ Clock.live) @@ timeout(30.seconds) @@ flaky
    )

}
