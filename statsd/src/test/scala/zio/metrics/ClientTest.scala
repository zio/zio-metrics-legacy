package zio.metrics

import zio.metrics.encoders._
import zio.{ Chunk, RIO }
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{ flaky, forked, timeout }

import zio._
import zio.test.ZIOSpecDefault

object ClientTest extends ZIOSpecDefault {
  private val port = 8126

  override def spec: Spec[TestEnvironment, Any] =
    suite("ClientTest") {
      test("client with sendAsync works") {
        val messages = List(1.0, 2.2, 3.4, 4.6)
        val expectedSentMetricsSet = List(
          "clientbar:1|c",
          "clientbar:2.2|c",
          "clientbar:3.4|c",
          "clientbar:4.6|c"
        ).toSet

        val createClient = Client.withListener[Chunk, Int] { (l: Chunk[Metric]) =>
          myudp(l)
        }

        val clientWithAgent = for {
          c <- createClient
          a <- UDPAgent(port)
        } yield (c, a)

        ZIO.scoped {
          clientWithAgent.flatMap {
            case (client, agent) =>
              for {
                opt <- ZIO.foreach(messages)(
                        d => ZIO.succeed(Counter("clientbar", d, 1.0, Seq.empty[zio.metrics.Tag]))
                      )
                _       <- ZIO.foreachDiscard(opt)(m => client.sendAsync(m))
                metrics <- ZIO.foreach(opt)(_ => agent.nextReceivedMetric)
              } yield assert(metrics.toSet)(hasSameElements(expectedSentMetricsSet))
          }
        }

      }
    }.provideLayer(Encoder.statsd) @@ forked @@ timeout(5.seconds) @@ flaky(5) @@ TestAspect.withLiveClock

  private val myudp: Chunk[Metric] => RIO[Encoder, Chunk[Int]] = msgs =>
    for {
      sde <- ZIO.environment[Encoder]
      opt <- ZIO.foreach(msgs)(sde.get.encode(_))
      l <- ZIO.foreach(opt.collect { case Some(msg) => msg })(
            s => ZIO.scoped(UDPClient("localhost", port).flatMap(_.send(s)))
          )
    } yield l

}
