package zio.metrics

import zio._
import zio.nio.channels._
import zio.nio.core.SocketAddress

object UDPClient {
  val clientM: Managed[Exception, DatagramChannel] = DatagramChannel().mapM { client =>
    for {
      address <- SocketAddress.inetSocketAddress("localhost", 8125)
      _       <- client.connect(address)
    } yield client
  }
}
