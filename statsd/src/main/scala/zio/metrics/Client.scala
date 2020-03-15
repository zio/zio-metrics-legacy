package zio.metrics

import zio.{ Chunk, Fiber, Queue, RIO, Task, URIO }
import zio.clock.Clock
//import zio.console._
import zio.stream.ZStream
import zio.duration.Duration.Finite
import zio.metrics.encoders._

import java.util.concurrent.ThreadLocalRandom

class Client(val bufferSize: Long, val timeout: Long, val queueCapacity: Int, host: Option[String], port: Option[Int]) {

  val queue                    = Queue.bounded[Metric](queueCapacity)
  private val duration: Finite = Finite(timeout)

  val udpClient = (host, port) match {
    case (None, None)       => UDPClient.clientM
    case (Some(h), Some(p)) => UDPClient.clientM(h, p)
    case (Some(h), None)    => UDPClient.clientM(h, 8125)
    case (None, Some(p))    => UDPClient.clientM("localhost", p)
  }

  val sample: List[Metric] => Task[List[Metric]] = metrics =>
    Task(
      metrics.filter(
        m =>
          m match {
            case sm: SampledMetric =>
              if (sm.sampleRate >= 1.0 || ThreadLocalRandom.current.nextDouble <= sm.sampleRate) true else false
            case _ => true
          }
      )
    )

  val udp: List[Metric] => RIO[Encoder, List[Long]] = metrics =>
    for {
      sde  <- RIO.environment[Encoder]
      flt  <- sample(metrics)
      msgs <- RIO.foreach(flt)(sde.get.encode(_))
      lngs <- RIO.collectAll(msgs.flatten.map(s => udpClient.use(_.write(Chunk.fromArray(s.getBytes())))))
    } yield lngs

  def listen(implicit queue: Queue[Metric]): URIO[Client.ClientEnv, Fiber[Throwable, Unit]] =
    listen[List, Long](udp)

  def listen[F[_], A](
    f: List[Metric] => RIO[Encoder, F[A]]
  )(implicit queue: Queue[Metric]): URIO[Client.ClientEnv, Fiber[Throwable, Unit]] =
    ZStream
      .fromQueue(queue)
      .groupedWithin(bufferSize, duration)
      //.tap(l => putStrLn(s"Selected: $l"))
      .mapM(l => f(l))
      .runDrain
      .fork

  val send: Queue[Metric] => Metric => Task[Unit] = q =>
    metric =>
      for {
        _ <- q.offer(metric)
      } yield ()

  val sendAsync: Queue[Metric] => Metric => Task[Unit] = q =>
    metric =>
      for {
        _ <- q.offer(metric).fork
      } yield ()
}

object Client {

  type ClientEnv = Encoder with Clock //with Console

  def apply(): Client = apply(5, 5000, 100, None, None)

  def apply(bufferSize: Long, timeout: Long): Client =
    apply(bufferSize, timeout, 100, None, None)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): Client =
    apply(bufferSize, timeout, queueCapacity, None, None)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int, host: Option[String], port: Option[Int]): Client =
    new Client(bufferSize, timeout, queueCapacity, host, port)

}
