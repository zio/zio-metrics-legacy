package zio.metrics

import zio.{ RIO, Runtime, Task }
import zio.clock.Clock
import zio.console._
import zio.metrics.encoders._

object ClientTest {

  val rt = Runtime.unsafeFromLayer(Encoder.statsd ++ Console.live ++ Clock.live)

  val myudp: List[Metric] => RIO[Encoder with Console, List[Int]] = msgs =>
    for {
      sde <- RIO.environment[Encoder]
      opt <- RIO.foreach(msgs)(sde.get.encode(_))
      _   <- putStrLn(s"udp: $opt")
      l   <- RIO.foreach(opt.flatten)(s => UDPClient().use(_.send(s)))
    } yield l

  val program = {
    val messages = List(1.0, 2.2, 3.4, 4.6, 5.1, 6.0, 7.9)
    val client   = Client()
    client.queue >>= (queue => {
      implicit val q = queue
      for {
        f <- client.listen[List, Int] { l =>
              myudp(l).provideSomeLayer[Encoder](Console.live)
            }
        _   <- putStrLn(s"implicit queue: $q")
        opt <- RIO.foreach(messages)(d => Task(Counter("clientbar", d, 1.0, Seq.empty[Tag])))
        _   <- RIO.foreach(opt)(m => client.sendAsync(q)(m))
        _   <- f.join
      } yield queue
    })
  }

  def main(args: Array[String]): Unit =
    rt.unsafeRun(program >>= (q => q.shutdown *> putStrLn("Bye bye").provideSomeLayer(Console.live)))

}
