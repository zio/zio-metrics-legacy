package zio.metrics

import zio.{ Managed, Task, ZManaged }

import java.net.{ DatagramPacket, DatagramSocket }

final class UDPAgent(port: Int) {
  private val buffer      = new Array[Byte](200)
  private lazy val socket = new DatagramSocket(port)

  def nextReceivedMetric: Task[String] = Task {
    val packet = new DatagramPacket(buffer, 0, buffer.length)
    socket.receive(packet)

    packet.getData.filter(_ != 0).map(_.toChar).mkString
  }

  def close: Task[Unit] = Task(socket.close())
}

object UDPAgent {
  def apply(port: Int): Managed[Throwable, UDPAgent] =
    ZManaged.acquireReleaseWith(Task(new UDPAgent(port)))(_.close.orDie)
}
