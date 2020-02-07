package zio.metrics

import zio._
import zio.nio.channels._
import zio.nio.core.SocketAddress

object UDPClient {
  val clientM: Managed[Exception, DatagramChannel] = clientM("localhost", 8125)

  def clientM(host: String, port: Int): Managed[Exception, DatagramChannel] = DatagramChannel().mapM { client =>
    for {
      address <- SocketAddress.inetSocketAddress(host, port)
      _       <- client.connect(address)
    } yield client
  }
}
