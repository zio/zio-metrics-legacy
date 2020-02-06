package zio.metrics

import zio.{ RIO, Runtime, Task }
import zio.clock.Clock
import zio.console._
import zio.internal.PlatformLive
import zio.metrics.statsd._
import zio.Chunk
import zio.metrics.statsd.StatsDEncoder

object ClientTest {

  val rt = Runtime(
    new StatsDEncoder with Console.Live with Clock.Live,
    PlatformLive.Default
  )

  val myudp: List[Metric] => RIO[Encoder with Console, List[Long]] = msgs =>
    for {
      sde  <- RIO.environment[Encoder]
      opt  <- RIO.traverse(msgs)(sde.encoder.encode(_))
      _    <- putStrLn(s"udp: $opt")
      l    <- RIO.sequence(opt.flatten.map(s => UDPClient.clientM.use(_.write(Chunk.fromArray(s.getBytes())))))
    } yield l

  val program = {
    val messages = List(1.0, 2.2, 3.4, 4.6, 5.1, 6.0, 7.9)
    val client   = Client()
    client.queue >>= (queue => {
      implicit val q = queue
      for {
        z   <- client.listen[List, Long](myudp(_).provideSome[Encoder](env => new StatsDEncoder with Console.Live {
          override val encoder = env.encoder
        }))
        _   <- putStrLn(s"implicit queue: $q")
        opt <- RIO.traverse(messages)(d => Task(Counter("clientbar", d, 1.0, Seq.empty[Tag])))
        _   <- RIO.sequence(opt.map(m => client.send(q)(m)))
      } yield z
    })
  }

  def main(args: Array[String]): Unit = {
    rt.unsafeRun(program >>= (lst => putStrLn(s"Main: $lst").provideSome(_ => Console.Live)))

    Thread.sleep(10000)
  }

}
