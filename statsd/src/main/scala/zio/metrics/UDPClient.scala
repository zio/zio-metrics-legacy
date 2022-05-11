package zio.metrics
/*
import zio._
import zio.nio.channels._
import zio.nio.core.SocketAddress
 */
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import zio.{ Scope, Task, ZIO }

class UDPClient(channel: DatagramChannel) {
  def send(data: String): Task[Int] = ZIO.succeed {
    val buf: ByteBuffer = ByteBuffer.allocate(512)
    buf.clear()
    buf.put(data.getBytes())
    buf.flip()

    channel.write(buf)
  }

  def close(): Task[Unit] =
    ZIO.succeed(channel.close())
}
object UDPClient {
  def apply(channel: DatagramChannel): ZIO[Scope, Throwable, UDPClient] =
    ZIO.acquireRelease(ZIO.succeed(new UDPClient(channel)))(_.close().orDie)

  def apply(): ZIO[Scope, Throwable, UDPClient] = apply("localhost", 8125)

  def apply(host: String, port: Int): ZIO[Scope, Throwable, UDPClient] =
    ZIO.acquireRelease(ZIO.succeed {
      val address = new InetSocketAddress(host, port)
      new UDPClient(DatagramChannel.open().connect(address))
    })(_.close().orDie)
}
/*
object UDPClient {
  val clientM: ZIO[Any, Exception, DatagramChannel] = clientM("localhost", 8125)

  def clientM(host: String, port: Int): ZIO[Any, Exception, DatagramChannel] =
    for {
      address  <- SocketAddress.inetSocketAddress(host, port)
      datagram <- DatagramChannel.connect(address)
    } yield datagram
}
 */
