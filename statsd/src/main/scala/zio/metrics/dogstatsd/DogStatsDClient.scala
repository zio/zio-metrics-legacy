package zio.metrics.dogstatsd

import zio.metrics.Client.ClientEnv
import zio.{ Task, ZManaged }
import zio.metrics._

class DogStatsDClient(client: Client) {

  def counter(name: String, value: Double): Task[Unit] =
    counter(name, value, 1.0, Seq.empty[Tag], false)

  def counter(name: String, value: Double, sampleRate: Double): Task[Unit] =
    counter(name, value, sampleRate, Seq.empty[Tag], false)

  def counter(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    counter(name, value, sampleRate, tags, false)

  def counter(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Counter(name, value, sampleRate, tags))

  def increment(name: String): Task[Unit] =
    increment(name, 1.0, Seq.empty[Tag], false)

  def increment(name: String, sampleRate: Double): Task[Unit] =
    increment(name, sampleRate, Seq.empty[Tag], false)

  def increment(name: String, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    increment(name, sampleRate, tags, false)

  def increment(name: String, sampleRate: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Counter(name, 1.0, sampleRate, tags))

  def decrement(name: String): Task[Unit] =
    decrement(name, 1.0, Seq.empty[Tag], false)

  def decrement(name: String, sampleRate: Double): Task[Unit] =
    decrement(name, sampleRate, Seq.empty[Tag], false)

  def decrement(name: String, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    decrement(name, sampleRate, tags, false)

  def decrement(name: String, sampleRate: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Counter(name, -1.0, sampleRate, tags))

  def gauge(name: String, value: Double): Task[Unit] =
    gauge(name, value, Seq.empty[Tag], false)

  def gauge(name: String, value: Double, tags: Seq[Tag]): Task[Unit] =
    gauge(name, value, tags, false)

  def gauge(name: String, value: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Gauge(name, value, tags))

  def meter(name: String, value: Double): Task[Unit] =
    meter(name, value, Seq.empty[Tag], false)

  def meter(name: String, value: Double, tags: Seq[Tag]): Task[Unit] =
    meter(name, value, tags, false)

  def meter(name: String, value: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Meter(name, value, tags))

  def timer(name: String, value: Double): Task[Unit] =
    timer(name, value, 1.0, Seq.empty[Tag], false)

  def timer(name: String, value: Double, sampleRate: Double): Task[Unit] =
    timer(name, value, sampleRate, Seq.empty[Tag], false)

  def timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    timer(name, value, sampleRate, tags, false)

  def timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Timer(name, value, sampleRate, tags))

  def set(name: String, value: String): Task[Unit] =
    set(name, value, Seq.empty[Tag], false)

  def set(name: String, value: String, tags: Seq[Tag]): Task[Unit] =
    set(name, value, tags, false)

  def set(name: String, value: String, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Set(name, value, tags))

  def histogram(name: String, value: Double): Task[Unit] =
    histogram(name, value, 1.0, Seq.empty[Tag], false)

  def histogram(name: String, value: Double, sampleRate: Double): Task[Unit] =
    histogram(name, value, sampleRate, Seq.empty[Tag], false)

  def histogram(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    histogram(name, value, sampleRate, tags, false)

  def histogram(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Histogram(name, value, sampleRate, tags))

  def serviceCheck(name: String, status: ServiceCheckStatus): Task[Unit] =
    serviceCheck(name, status, None, None, None, Seq.empty[Tag], false)

  def serviceCheck(
    name: String,
    status: ServiceCheckStatus,
    timestamp: Option[Long],
    hostname: Option[String],
    message: Option[String],
    tags: Seq[Tag],
    sync: Boolean
  ): Task[Unit] =
    client.sendM(sync)(ServiceCheck(name, status, timestamp, hostname, message, tags))

  def event(name: String, text: String): Task[Unit] =
    event(name, text, None, None, None, None, None, None, Seq.empty[Tag], false)

  def event(
    name: String,
    text: String,
    timestamp: Option[Long],
    hostname: Option[String],
    aggregationKey: Option[String],
    priority: Option[EventPriority],
    sourceTypeName: Option[String],
    alertType: Option[EventAlertType],
    tags: Seq[Tag],
    sync: Boolean
  ): Task[Unit] =
    client.sendM(sync)(
      Event(name, text, timestamp, hostname, aggregationKey, priority, sourceTypeName, alertType, tags)
    )

  def distribution(name: String, value: Double): Task[Unit] =
    distribution(name, value, 1.0, Seq.empty[Tag], false)

  def distribution(name: String, value: Double, sampleRate: Double): Task[Unit] =
    distribution(name, value, sampleRate, Seq.empty[Tag], false)

  def distribution(name: String, value: Double, sampleRate: Double, tags: Seq[Tag]): Task[Unit] =
    distribution(name, value, sampleRate, tags, false)

  def distribution(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean): Task[Unit] =
    client.sendM(sync)(Distribution(name, value, sampleRate, tags))

}

object DogStatsDClient {

  def apply(): ZManaged[ClientEnv, Throwable, DogStatsDClient] = apply(5, 5000, 100, None, None)

  def apply(bufferSize: Int, timeout: Long): ZManaged[ClientEnv, Throwable, DogStatsDClient] =
    apply(bufferSize, timeout, 100, None, None)

  def apply(bufferSize: Int, timeout: Long, queueCapacity: Int): ZManaged[ClientEnv, Throwable, DogStatsDClient] =
    apply(bufferSize, timeout, queueCapacity, None, None)

  def apply(
    bufferSize: Int,
    timeout: Long,
    queueCapacity: Int,
    host: Option[String],
    port: Option[Int]
  ): ZManaged[ClientEnv, Throwable, DogStatsDClient] =
    Client(bufferSize, timeout, queueCapacity, host, port).map { new DogStatsDClient(_) }

}
