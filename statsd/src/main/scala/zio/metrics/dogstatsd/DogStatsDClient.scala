package zio.metrics.dogstatsd

import zio.Task
import zio.metrics._

class DogStatsDClient(override val bufferSize: Long, override val timeout: Long, override val queueCapacity: Int)
    extends Client(bufferSize, timeout, queueCapacity) {

  def counter(name: String, value: Double): Task[Unit] =
    counter(name, value, 1.0, Seq.empty[Tag])

  def counter(name: String, value: Double, sampleRate: Double): Task[Unit] =
    counter(name, value, sampleRate, Seq.empty[Tag])

  def counter(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Counter(name, value, sampleRate, tags))
    } yield ()

  def increment(name: String): Task[Unit] =
    increment(name, 1.0, Seq.empty[Tag])

  def increment(name: String, sampleRate: Double): Task[Unit] =
    increment(name, sampleRate, Seq.empty[Tag])

  def increment(name: String, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Counter(name, 1.0, sampleRate, tags))
    } yield ()

  def decrement(name: String): Task[Unit] =
    decrement(name, 1.0, Seq.empty[Tag])

  def decrement(name: String, sampleRate: Double): Task[Unit] =
    decrement(name, sampleRate, Seq.empty[Tag])

  def decrement(name: String, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Counter(name, -1.0, sampleRate, tags))
    } yield ()

  def gauge(name: String, value: Double): Task[Unit] =
    gauge(name, value, Seq.empty[Tag])

  def gauge(name: String, value: Double, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Gauge(name, value, tags))
    } yield ()

  def meter(name: String, value: Double): Task[Unit] =
    meter(name, value, Seq.empty[Tag])

  def meter(name: String, value: Double, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Meter(name, value, tags))
    } yield ()

  def timer(name: String, value: Double): Task[Unit] =
    timer(name, value, 1.0, Seq.empty[Tag])

  def timer(name: String, value: Double, sampleRate: Double): Task[Unit] =
    timer(name, value, sampleRate, Seq.empty[Tag])

  def timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Timer(name, value, sampleRate, tags))
    } yield ()

  def set(name: String, value: String): Task[Unit] =
    set(name, value, Seq.empty[Tag])

  def set(name: String, value: String, tags: Seq[Tag]): Task[Unit] =
    for {
      q <- queue
      _ <- send(q)(Set(name, value, tags))
    } yield ()
}

object DogStatsDClient {

  def apply(): DogStatsDClient = new DogStatsDClient(5, 5, 1000)

  def apply(bufferSize: Long, timeout: Long): DogStatsDClient =
    new DogStatsDClient(bufferSize, timeout, 1000)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): DogStatsDClient =
    new DogStatsDClient(bufferSize, timeout, queueCapacity)

}
