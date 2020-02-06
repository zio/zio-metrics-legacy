package zio.metrics

import zio.{ Chunk, Fiber, Queue, RIO, Schedule, Task, URIO }
import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.duration.DurationSyntax
import zio.stream.{ Sink, ZStream }
import java.util.concurrent.ThreadLocalRandom

class Client(val bufferSize: Long, val timeout: Long, val queueCapacity: Int) {

  val queue     = Queue.bounded[Metric](queueCapacity)
  val everyNsec = Schedule.spaced(new DurationSyntax(timeout).seconds)
  val sink      = Sink.collectAllN[Metric](bufferSize)

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
      msgs <- RIO.traverse(flt)(sde.encoder.encode(_))
      lngs <- RIO.sequence(msgs.flatten.map(s => UDPClient.clientM.use(_.write(Chunk.fromArray(s.getBytes())))))
    } yield lngs

  def listen(implicit queue: Queue[Metric]): URIO[Client.ClientEnv, Fiber[Throwable, Unit]] = {
    println(s"listen: $queue")
    ZStream
      .fromQueue(queue)
      .aggregateAsyncWithin(sink, everyNsec)
      .tap(l => putStrLn(s"Selected: $l"))
      .mapM(l => udp(l))
      .runDrain
      .fork
  }

  val send: Queue[Metric] => Metric => Task[Unit] = q =>
    metric =>
      for {
        _ <- q.offer(metric) //.fork
      } yield println(s"Sending: ${metric} to queue: $q")

  val sendAsync: Queue[Metric] => Metric => Task[Unit] = q =>
    metric =>
      for {
        _ <- q.offer(metric).fork
      } yield println(s"Sending: ${metric}")

  val sendM: Metric => Task[Unit] = metric =>
    for {
      q <- queue
      _ <- send(q)(metric)
    } yield ()
}

object Client {

  type ClientEnv = Encoder with Clock with Console

  def apply(): Client = apply(5, 5, 1000)

  def apply(bufferSize: Long, timeout: Long): Client =
    apply(bufferSize, timeout, 1000)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): Client =
    new Client(bufferSize, timeout, queueCapacity)

}