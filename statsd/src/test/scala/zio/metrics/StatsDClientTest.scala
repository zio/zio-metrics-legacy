package zio.metrics

import zio.{ Queue, RIO, Runtime, Schedule }
import zio.clock.Clock
import zio.console._
import zio.metrics.statsd._
import java.util.concurrent.TimeUnit
import zio.duration.Duration

object StatsDClientTest {

  val rt = Runtime.unsafeFromLayer(Encoder.statsd ++ Console.live ++ Clock.live)

  val schd = Schedule.recurs(10)

  val client = StatsDClient()

  def program(r: Long)(implicit queue: Queue[Metric]) =
    for {
      clock <- RIO.environment[Clock]
      _     <- client.listen
      t1    <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _     <- client.increment("zmetrics.counter", 0.9)
      _     <- putStrLn(s"waiting for $r ms") *> clock.get.sleep(Duration(r, TimeUnit.MILLISECONDS))
      t2    <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _     <- client.timer("zmetrics.timer", (t2 - t1).toDouble, 0.9)
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
