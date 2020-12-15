package zio.metrics
/*
import zio._
import zio.nio.channels._
import zio.nio.core.SocketAddress
 */
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import zio.{ IO, Task, ZManaged }

class UDPClient(channel: DatagramChannel) {
  def send(data: String): Task[Int] = IO {
    val buf: ByteBuffer = ByteBuffer.allocate(512)
    buf.clear()
    buf.put(data.getBytes())
    buf.flip()

    channel.write(buf)
  }

  def close(): Task[Unit] =
    IO(channel.close())
}
object UDPClient {
  def apply(channel: DatagramChannel): ZManaged[Any, Throwable, UDPClient] =
    ZManaged.make(Task(new UDPClient(channel)))(_.close().orDie)

  def apply(): ZManaged[Any, Throwable, UDPClient] = apply("localhost", 8125)

  def apply(host: String, port: Int): ZManaged[Any, Throwable, UDPClient] =
    ZManaged.make(Task {
      val address = new InetSocketAddress(host, port)
      new UDPClient(DatagramChannel.open().connect(address))
    })(_.close().orDie)
}
/*
object UDPClient {
  val clientM: ZManaged[Any, Exception, DatagramChannel] = clientM("localhost", 8125)

  def clientM(host: String, port: Int): ZManaged[Any, Exception, DatagramChannel] =
    for {
      address  <- SocketAddress.inetSocketAddress(host, port).toManaged_
      datagram <- DatagramChannel.connect(address)
    } yield datagram
}
 */
