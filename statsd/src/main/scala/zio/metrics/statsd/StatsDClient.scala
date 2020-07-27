package zio.metrics.statsd

import zio.Task
import zio.metrics._

class StatsDClient(client: Client) {

  def counter(name: String, value: Double): Task[Unit] =
    counter(name, value, 1.0, false)

  def counter(name: String, value: Double, sampleRate: Double): Task[Unit] =
    counter(name, value, sampleRate, false)

  def counter(name: String, value: Double, sampleRate: Double, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Counter(name, value, sampleRate, Seq.empty[Tag]))
  }

  def increment(name: String): Task[Unit] =
    increment(name, 1.0, false)

  def increment(name: String, sampleRate: Double): Task[Unit] =
    increment(name, sampleRate, false)

  def increment(name: String, sampleRate: Double, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Counter(name, 1.0, sampleRate, Seq.empty[Tag]))
  }

  def decrement(name: String): Task[Unit] =
    decrement(name, 1.0, false)

  def decrement(name: String, sampleRate: Double): Task[Unit] =
    decrement(name, sampleRate, false)

  def decrement(name: String, sampleRate: Double, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Counter(name, -1.0, sampleRate, Seq.empty[Tag]))
  }

  def gauge(name: String, value: Double): Task[Unit] =
    gauge(name, value, false)

  def gauge(name: String, value: Double, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Gauge(name, value, Seq.empty[Tag]))
  }

  def meter(name: String, value: Double): Task[Unit] =
    meter(name, value, false)

  def meter(name: String, value: Double, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Meter(name, value, Seq.empty[Tag]))
  }

  def timer(name: String, value: Double): Task[Unit] =
    timer(name, value, 1.0, false)

  def timer(name: String, value: Double, sampleRate: Double): Task[Unit] =
    timer(name, value, sampleRate, false)

  def timer(name: String, value: Double, sampleRate: Double, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Timer(name, value, sampleRate, Seq.empty[Tag]))
  }

  def set(name: String, value: String): Task[Unit] =
    set(name, value, false)

  def set(name: String, value: String, sync: Boolean): Task[Unit] = {
    val sendM = if (sync) client.send else client.sendAsync
    sendM(Set(name, value, Seq.empty[Tag]))
  }
}

object StatsDClient {
//  def apply(): StatsDClient = apply(5, 5000, 100, None, None)
//
//  def apply(bufferSize: Long, timeout: Long): StatsDClient =
//    apply(bufferSize, timeout, 100, None, None)
//
//  def apply(bufferSize: Long, timeout: Long, queueCapacity: Int): StatsDClient =
//    apply(bufferSize, timeout, queueCapacity, None, None)
//
//  def apply(
//    bufferSize: Long,
//    timeout: Long,
//    queueCapacity: Int,
//    host: Option[String],
//    port: Option[Int]
//  ): StatsDClient =
//    new StatsDClient(bufferSize, timeout, queueCapacity, host, port)
}
