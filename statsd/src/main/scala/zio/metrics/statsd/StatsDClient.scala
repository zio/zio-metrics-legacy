package zio.metrics.statsd

import zio.{ Queue, Task }
import zio.metrics._

class StatsDClient(override val bufferSize: Long, override val timeout: Long, override val queueCapacity: Int)
    extends Client(bufferSize, timeout, queueCapacity) {

  def counter(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    counter(name, value, 1.0, false)

  def counter(name: String, value: Double, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    counter(name, value, sampleRate, false)

  def counter(name: String, value: Double, sampleRate: Double, sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Counter(name, value, sampleRate, Seq.empty[Tag]))
  }

  def increment(name: String)(implicit queue: Queue[Metric]): Task[Unit] =
    increment(name, 1.0, false)

  def increment(name: String, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    increment(name, sampleRate, false)

  def increment(name: String, sampleRate: Double, sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Counter(name, 1.0, sampleRate, Seq.empty[Tag]))
  }

  def decrement(name: String)(implicit queue: Queue[Metric]): Task[Unit] =
    decrement(name, 1.0, false)

  def decrement(name: String, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    decrement(name, sampleRate, false)

  def decrement(name: String, sampleRate: Double, sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Counter(name, -1.0, sampleRate, Seq.empty[Tag]))
  }

  def gauge(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    gauge(name, value, false)

  def gauge(name: String, value: Double, sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Gauge(name, value, Seq.empty[Tag]))
  }

  def meter(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    meter(name, value, false)

  def meter(name: String, value: Double, sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Meter(name, value, Seq.empty[Tag]))
  }

  def timer(name: String, value: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    timer(name, value, 1.0, false)

  def timer(name: String, value: Double, sampleRate: Double)(implicit queue: Queue[Metric]): Task[Unit] =
    timer(name, value, sampleRate, false)

  def timer(name: String, value: Double, sampleRate: Double, sync: Boolean)(
    implicit queue: Queue[Metric]
  ): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Timer(name, value, sampleRate, Seq.empty[Tag]))
  }

  def set(name: String, value: String)(implicit queue: Queue[Metric]): Task[Unit] =
    set(name, value, false)

  def set(name: String, value: String, sync: Boolean)(implicit queue: Queue[Metric]): Task[Unit] = {
    val sendM = if (sync) send(queue) else sendAsync(queue)
    sendM(Set(name, value, Seq.empty[Tag]))
  }
}

object StatsDClient {
  def apply(): StatsDClient = apply(5, 5, 100)

  def apply(bufferSize: Long, timeout: Long): StatsDClient =
    apply(bufferSize, timeout, 100)

  def apply[R](bufferSize: Long, timeout: Long, queueCapacity: Int): StatsDClient =
    new StatsDClient(bufferSize, timeout, queueCapacity)
}
