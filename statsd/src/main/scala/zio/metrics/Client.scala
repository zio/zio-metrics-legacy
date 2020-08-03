package zio.metrics

import java.util.concurrent.ThreadLocalRandom

import zio._
import zio.clock.Clock
import zio.duration.Duration.Finite
import zio.metrics.encoders._
import zio.stream.ZStream

class Client(val bufferSize: Long, val timeout: Long, host: Option[String], port: Option[Int])(
  private val queue: Queue[Metric]
) {

  type UDPQueue = ZQueue[Nothing, Any, Encoder, Throwable, Nothing, Metric]

  private val duration: Finite = Finite(timeout)

  private val udpClient: ZManaged[Any, Throwable, UDPClient] = (host, port) match {
    case (None, None)       => UDPClient()
    case (Some(h), Some(p)) => UDPClient(h, p)
    case (Some(h), None)    => UDPClient(h, 8125)
    case (None, Some(p))    => UDPClient("localhost", p)
  }

  private val sample: List[Metric] => Task[List[Metric]] = metrics =>
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

  private val udp: List[Metric] => RIO[Encoder, List[Int]] = metrics =>
    for {
      sde  <- RIO.environment[Encoder]
      flt  <- sample(metrics)
      msgs <- RIO.foreach(flt)(sde.get.encode(_))
      ints <- RIO.foreach(msgs.flatten)(s => udpClient.use(_.send(s)))
    } yield ints

  private def listen: URIO[Client.ClientEnv, Fiber[Throwable, Unit]] =
    listen[List, Int](udp)

  private def listen[F[_], A](
    f: List[Metric] => RIO[Encoder, F[A]]
  ): URIO[Client.ClientEnv, Fiber[Throwable, Unit]] =
    ZStream
      .fromQueue[Encoder, Throwable, Metric](queue)
      .groupedWithin(bufferSize, duration)
      .mapM(l => f(l))
      .runDrain
      .fork

  val send: Metric => UIO[Unit] =
    metric => queue.offer(metric).unit

  val sendAsync: Metric => UIO[Unit] =
    metric => queue.offer(metric).fork.unit

  def sendM(sync: Boolean): Metric => UIO[Unit] =
    if (sync) {
      send
    } else {
      sendAsync
    }

}

object Client {

  type ClientEnv = Encoder with Clock //with Console

  def apply(): ZManaged[ClientEnv, Throwable, Client] = apply(5, 5000, 100, None, None)

  def apply(bufferSize: Long, timeout: Long): ZManaged[ClientEnv, Throwable, Client] =
    apply(bufferSize, timeout, 100, None, None)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): ZManaged[ClientEnv, Throwable, Client] =
    apply(bufferSize, timeout, queueCapacity, None, None)

  def apply(
    bufferSize: Long,
    timeout: Long,
    queueCapacity: Int,
    host: Option[String],
    port: Option[Int]
  ): ZManaged[ClientEnv, Throwable, Client] =
    ZManaged.make {
      for {
        queue  <- ZQueue.bounded[Metric](queueCapacity)
        client = new Client(bufferSize, timeout, host, port)(queue)
        fiber  <- client.listen
      } yield (client, fiber)
    } { case (client, fiber) => client.queue.shutdown *> fiber.join.orDie }
      .map(_._1)

  def withListener[F[_], A](listener: List[Metric] => RIO[Encoder, F[A]]): ZManaged[ClientEnv, Throwable, Client] =
    withListener(5, 5000, 100, None, None)(listener)

  def withListener[F[_], A](
    bufferSize: Long,
    timeout: Long,
    queueCapacity: Int,
    host: Option[String],
    port: Option[Int]
  )(listener: List[Metric] => RIO[Encoder, F[A]]): ZManaged[ClientEnv, Throwable, Client] =
    ZManaged.make {
      for {
        queue  <- ZQueue.bounded[Metric](queueCapacity)
        client = new Client(bufferSize, timeout, host, port)(queue)
        fiber  <- client.listen(listener)
      } yield (client, fiber)
    } { case (client, fiber) => client.queue.shutdown *> fiber.join.orDie }
      .map(_._1)

}
