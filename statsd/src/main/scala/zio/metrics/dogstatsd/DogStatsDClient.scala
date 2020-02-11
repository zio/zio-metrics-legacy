package zio.metrics.dogstatsd

import zio.{ Queue, Task }
import zio.metrics._

class DogStatsDClient(
  override val bufferSize: Long,
  override val timeout: Long,
  override val queueCapacity: Int,
  host: Option[String],
  port: Option[Int]
) extends Client(bufferSize, timeout, queueCapacity, host, port) {

  def counter(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    counter(name, value, 1.0, Seq.empty[Tag], false)

  def counter(name: String, value: Double, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    counter(name, value, sampleRate, Seq.empty[Tag], false)

  def counter(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])(
    implicit queue: Queue[Metric]
  ): Task[Unit] =
    counter(name, value, sampleRate, tags, false)

  def counter(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Counter(name, value, sampleRate, tags))
  }

  def increment(name: String)(implicit queue: Queue[Metric]): Task[Unit] =
    increment(name, 1.0, Seq.empty[Tag], false)

  def increment(name: String, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    increment(name, sampleRate, Seq.empty[Tag], false)

  def increment(name: String, sampleRate: Double, tags: Seq[Tag])(implicit queue: Queue[Metric]): Task[Unit] =
    increment(name, sampleRate, tags, false)

  def increment(name: String, sampleRate: Double, tags: Seq[Tag], sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Counter(name, 1.0, sampleRate, tags))
  }

  def decrement(name: String)(implicit queue: Queue[Metric]): Task[Unit] =
    decrement(name, 1.0, Seq.empty[Tag], false)

  def decrement(name: String, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    decrement(name, sampleRate, Seq.empty[Tag], false)

  def decrement(name: String, sampleRate: Double, tags: Seq[Tag])(implicit queue: Queue[Metric]): Task[Unit] =
    decrement(name, sampleRate, tags, false)

  def decrement(name: String, sampleRate: Double, tags: Seq[Tag], sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Counter(name, -1.0, sampleRate, tags))
  }

  def gauge(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    gauge(name, value, Seq.empty[Tag], false)

  def gauge(name: String, value: Double, tags: Seq[Tag])(implicit queue: Queue[Metric]): Task[Unit] =
    gauge(name, value, tags, false)

  def gauge(name: String, value: Double, tags: Seq[Tag], sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Gauge(name, value, tags))
  }

  def meter(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    meter(name, value, Seq.empty[Tag], false)

  def meter(name: String, value: Double, tags: Seq[Tag])(implicit queue: Queue[Metric]): Task[Unit] =
    meter(name, value, tags, false)

  def meter(name: String, value: Double, tags: Seq[Tag], sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Meter(name, value, tags))
  }

  def timer(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    timer(name, value, 1.0, Seq.empty[Tag], false)

  def timer(name: String, value: Double, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    timer(name, value, sampleRate, Seq.empty[Tag], false)

  def timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])(
    implicit queue: Queue[Metric]
  ): Task[Unit] =
    timer(name, value, sampleRate, tags, false)

  def timer(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Timer(name, value, sampleRate, tags))
  }

  def set(name: String, value: String)(implicit queue: Queue[Metric]): Task[Unit] =
    set(name, value, Seq.empty[Tag], false)

  def set(name: String, value: String, tags: Seq[Tag])(implicit queue: Queue[Metric]): Task[Unit] =
    set(name, value, tags, false)

  def set(name: String, value: String, tags: Seq[Tag], sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Set(name, value, tags))
  }

  def histogram(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    histogram(name, value, 1.0, Seq.empty[Tag], false)

  def histogram(name: String, value: Double, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    histogram(name, value, sampleRate, Seq.empty[Tag], false)

  def histogram(name: String, value: Double, sampleRate: Double, tags: Seq[Tag])(
    implicit queue: Queue[Metric]
  ): Task[Unit] =
    histogram(name, value, sampleRate, tags, false)

  def histogram(name: String, value: Double, sampleRate: Double, tags: Seq[Tag], sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Histogram(name, value, sampleRate, tags))
  }

  def serviceCheck(name: String, status: ServiceCheckStatus)(implicit queue: Queue[Metric]): Task[Unit] =
    serviceCheck(name, status, None, None, None, Seq.empty[Tag], false)

  def serviceCheck(
    name: String,
    status: ServiceCheckStatus,
    timestamp: Option[Long],
    hostname: Option[String],
    message: Option[String],
    tags: Seq[Tag],
    sync: Boolean
  )(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(ServiceCheck(name, status, timestamp, hostname, message, tags))
  }

  def event(name: String, text: String)(implicit queue: Queue[Metric]): Task[Unit] =
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
  )(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Event(name, text, timestamp, hostname, aggregationKey, priority, sourceTypeName, alertType, tags))
  }
}

object DogStatsDClient {

  def apply(): DogStatsDClient = apply(5, 5000, 100, None, None)

  def apply(bufferSize: Long, timeout: Long): DogStatsDClient =
    apply(bufferSize, timeout, 100, None, None)

  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): DogStatsDClient =
    apply(bufferSize, timeout, queueCapacity, None, None)

  def apply(
    bufferSize: Long,
    timeout: Long,
    queueCapacity: Int,
    host: Option[String],
    port: Option[Int]
  ): DogStatsDClient =
    new DogStatsDClient(bufferSize, timeout, queueCapacity, host, port)

}
