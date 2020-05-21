package zio.metrics

import zio.{ Fiber, Queue, RIO, Task, UIO, URIO, ZManaged, ZQueue }
import zio.clock.Clock
import zio.stream.ZStream
import zio.duration.Duration.Finite
import zio.metrics.encoders._
import java.util.concurrent.ThreadLocalRandom

class Client(val bufferSize: Long, val timeout: Long, val queueCapacity: Int, host: Option[String], port: Option[Int]) {

  type UDPQueue = ZQueue[Nothing, Any, Encoder, Throwable, Nothing, Metric]

  val queue: UIO[Queue[Metric]] = ZQueue.bounded[Metric](queueCapacity)
  private val duration: Finite  = Finite(timeout)

  val udpClient: ZManaged[Any, Throwable, UDPClient] = (host, port) match {
    case (None, None)       => UDPClient()
    case (Some(h), Some(p)) => UDPClient(h, p)
    case (Some(h), None)    => UDPClient(h, 8125)
    case (None, Some(p))    => UDPClient("localhost", p)
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

  val udp: List[Metric] => RIO[Encoder, List[Int]] = metrics =>
    for {
      sde  <- RIO.environment[Encoder]
      flt  <- sample(metrics)
      msgs <- RIO.foreach(flt)(sde.get.encode(_))
      ints <- RIO.foreach(msgs.flatten)(s => udpClient.use(_.send(s)))
    } yield ints

  def listen(implicit queue: UDPQueue): URIO[Client.ClientEnv, Fiber[Throwable, Unit]] =
    listen[List, Int](udp)

  def listen[F[_], A](
    f: List[Metric] => RIO[Encoder, F[A]]
  )(implicit queue: UDPQueue): URIO[Client.ClientEnv, Fiber[Throwable, Unit]] =
    ZStream
      .fromQueue[Encoder, Throwable, Metric](queue)
      .groupedWithin(bufferSize, duration)
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
