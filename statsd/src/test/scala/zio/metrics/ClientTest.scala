package zio.metrics

import zio.Clock

import zio.metrics.encoders._
import zio.{ Chunk, RIO, Task }
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{ flaky, forked, timeout }

import zio._
import zio.test.ZIOSpecDefault

object ClientTest extends ZIOSpecDefault {
  private val port = 8126

  override def spec: ZSpec[TestEnvironment, Any] =
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
          myudp(l).provideSomeLayer[Encoder](Console.live)
        }

        val clientWithAgent = for {
          c <- createClient
          a <- UDPAgent(port)
        } yield (c, a)

        clientWithAgent.use {
          case (client, agent) =>
            for {
              opt     <- RIO.foreach(messages)(d => Task(Counter("clientbar", d, 1.0, Seq.empty[zio.metrics.Tag])))
              _       <- RIO.foreachDiscard(opt)(m => client.sendAsync(m))
              metrics <- RIO.foreach(opt)(_ => agent.nextReceivedMetric)
            } yield assert(metrics.toSet)(hasSameElements(expectedSentMetricsSet))
        }

      }
    }.provideCustomLayer(Clock.live ++ Encoder.statsd) @@ forked @@ timeout(5.seconds) @@ flaky(5)

  private val myudp: Chunk[Metric] => RIO[Encoder, Chunk[Int]] = msgs =>
    for {
      sde <- RIO.environment[Encoder]
      opt <- RIO.foreach(msgs)(sde.get.encode(_))
      l   <- RIO.foreach(opt.collect { case Some(msg) => msg })(s => UDPClient("localhost", port).use(_.send(s)))
    } yield l

}
