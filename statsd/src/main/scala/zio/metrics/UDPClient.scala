package zio.metrics

import zio._
import zio.nio.channels._
import zio.nio.core.SocketAddress

object UDPClient {
  val clientM: ZManaged[Any, Exception, DatagramChannel] = clientM("localhost", 8125)

  def clientM(host: String, port: Int): ZManaged[Any, Exception, DatagramChannel]  = for {
    address <- SocketAddress.inetSocketAddress(host, port).toManaged_
    datagram <- DatagramChannel.connect(address)
  } yield datagram
}
