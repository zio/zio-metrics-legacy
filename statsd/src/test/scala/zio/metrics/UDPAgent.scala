package zio.metrics

import zio.{ Scope, Task, ZIO }

import java.net.{ DatagramPacket, DatagramSocket }

final class UDPAgent(port: Int) {
  private val buffer      = new Array[Byte](200)
  private lazy val socket = new DatagramSocket(port)

  def nextReceivedMetric: Task[String] = Task.attempt {
    val packet = new DatagramPacket(buffer, 0, buffer.length)
    socket.receive(packet)

    packet.getData.filter(_ != 0).map(_.toChar).mkString
  }

  def close: Task[Unit] = Task.attempt {
    socket.close()
  }
}

object UDPAgent {
  def apply(port: Int): ZIO[Scope, Throwable, UDPAgent] =
    ZIO.acquireRelease(Task.succeed(new UDPAgent(port)))(_.close.orDie)
}
