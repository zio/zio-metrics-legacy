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

  val udp: List[String] => RIO[Console, Unit] = msgs =>
    for {
      l <- RIO.traverse(msgs)(s => UDPClient.clientM.use(_.write(Chunk.fromArray(s.getBytes()))))
      _ <- putStrLn(s"udp: $l").provideSome[Console](_ => Console.Live)
    } yield ()

  val program = {
    val messages = List(1.0, 2.2, 3.4, 4.6, 5.1, 6.0, 7.9)
    val client   = Client()
    client.queue >>= (queue => {
      implicit val q = queue
      for {
        //q   <- client.queue
        z   <- client.listen //(udp)
        _   <- putStrLn(s"implicit queue: $q")
        opt <- RIO.traverse(messages)(d => Task(Counter("clientbar", d, 1.0, Seq.empty[Tag])))
        _   <- RIO.sequence(opt.map(m => client.send(q)(m)))
      } yield z
    })
  }

  def main(args: Array[String]): Unit = {
    rt.unsafeRun(program >>= (lst => putStrLn(s"Main: $lst").provideSome(_ => Console.Live)))

    Thread.sleep(20000)
  }

}
