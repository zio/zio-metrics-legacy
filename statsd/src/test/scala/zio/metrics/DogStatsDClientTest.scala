package zio.metrics

import zio.{ Queue, RIO, Runtime, Schedule }
import zio.clock.Clock
import zio.console._
import java.util.concurrent.TimeUnit
import zio.duration.Duration
import zio.metrics.dogstatsd._

object DogStatsDClientTest {

  val rt = Runtime.unsafeFromLayer(Encoder.dogstatsd ++ Console.live ++ Clock.live)

  val schd = Schedule.recurs(10)

  val client = DogStatsDClient()

  def program(r: Long)(implicit queue: Queue[Metric]) =
    for {
      clock <- RIO.environment[Clock]
      _     <- client.listen
      t1    <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _     <- client.increment("zmetrics.dog.counter", 0.9)
      _     <- putStrLn(s"waiting for $r ms") *> clock.get.sleep(Duration(r, TimeUnit.MILLISECONDS))
      t2    <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      d     = (t2 - t1).toDouble
      _     <- client.timer("zmetrics.dog.timer", d, 0.9)
      _     <- client.histogram("zmetrics.dog.hist", d)
      _     <- client.serviceCheck("zmetrics.dog.check", ServiceCheckOk)
      _     <- client.event("zmetrics.dog.event", "something amazing happened")
    } yield ()

  def main(args: Array[String]): Unit = {
    val timeouts = Seq(34L, 76L, 52L)
    rt.unsafeRun(
      client.queue >>= (
        q =>
          RIO
            .foreach(timeouts)(l => program(l)(q))
            .repeat(schd)
        )
    )
    Thread.sleep(10000)
  }

}
