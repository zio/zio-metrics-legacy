package zio.metrics.statsd

import zio.Task
import zio.metrics._

class StatsDClient(override val bufferSize: Long, override val timeout: Long, override val queueCapacity: Int)
    extends Client(bufferSize, timeout, queueCapacity) {

  def counter(name: String, value: Double): Task[Unit] =
    counter(name, value, 1.0)

  def counter(name: String, value: Double, sampleRate: Double): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Counter(name, value, sampleRate, Seq.empty[Tag]))
    } yield ()

  def increment(name: String): Task[Unit] =
    increment(name, 1.0)

  def increment(name: String, sampleRate: Double): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Counter(name, 1.0, sampleRate, Seq.empty[Tag]))
    } yield ()

  def decrement(name: String): Task[Unit] =
    decrement(name, 1.0)

  def decrement(name: String, sampleRate: Double): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Counter(name, -1.0, sampleRate, Seq.empty[Tag]))
    } yield ()

  def gauge(name: String, value: Double): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Gauge(name, value, Seq.empty[Tag]))
    } yield ()

  def meter(name: String, value: Double): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Meter(name, value, Seq.empty[Tag]))
    } yield ()

  def timer(name: String, value: Double): Task[Unit] =
    timer(name, value, 1.0)

  def timer(name: String, value: Double, sampleRate: Double): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Timer(name, value, sampleRate, Seq.empty[Tag]))
    } yield ()

  def set(name: String, value: String): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Set(name, value, Seq.empty[Tag]))
    } yield ()
}

object StatsDClient {
  def apply(): StatsDClient = new StatsDClient(5, 5, 1000)

  def apply(bufferSize: Long, timeout: Long): StatsDClient =
    new StatsDClient(bufferSize, timeout, 1000)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): StatsDClient =
    new StatsDClient(bufferSize, timeout, queueCapacity)

}
