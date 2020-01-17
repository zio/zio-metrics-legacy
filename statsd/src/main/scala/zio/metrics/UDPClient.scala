package zio.metrics

/*import java.net.{InetSocketAddress,SocketException}
import java.nio.ByteBuffer
import java.nio.channels.UnresolvedAddressException
 */
import zio._
/*
import zio.clock._
import zio.console._
import zio.nio._
 */
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
