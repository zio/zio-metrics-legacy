package zio.metrics
import io.prometheus.{ client => jp }
import zio._

import java.io.StringWriter
import java.{ util => ju }

package object prometheus2 {
  type Registry = Registry.Service

  object Registry {
    trait Service {
      def collectorRegistry: UIO[jp.CollectorRegistry]
      def updateRegistry[A](f: jp.CollectorRegistry => Task[A]): Task[A]
      def collect: UIO[ju.Enumeration[jp.Collector.MetricFamilySamples]]
      def string004: UIO[String] = collect flatMap { sampled =>
        ZIO.succeed {
          val writer = new StringWriter
          jp.exporter.common.TextFormat.write004(writer, sampled)
          writer.toString
        }
      }
    }

    private final class ServiceImpl(registry: jp.CollectorRegistry, lock: Semaphore) extends Service {

      def collectorRegistry: UIO[jp.CollectorRegistry] = ZIO.succeed(registry)

      def updateRegistry[A](f: jp.CollectorRegistry => Task[A]): Task[A] = lock.withPermit {
        f(registry)
      }

      def collect: zio.UIO[ju.Enumeration[jp.Collector.MetricFamilySamples]] =
        ZIO.succeed(registry.metricFamilySamples())
    }
    private object ServiceImpl {
      def makeWith(registry: jp.CollectorRegistry): UIO[ServiceImpl] =
        Semaphore
          .make(permits = 1)
          .map(new ServiceImpl(registry, _))
    }

    def live: ULayer[Registry] = ZLayer.fromZIO(ServiceImpl.makeWith(new jp.CollectorRegistry()))

    def default: ULayer[Registry] = ZLayer.fromZIO(ServiceImpl.makeWith(jp.CollectorRegistry.defaultRegistry))

    def provided: URLayer[jp.CollectorRegistry, Registry] =
      ZLayer.fromZIO(ZIO.serviceWithZIO(ServiceImpl.makeWith))

    def defaultMetrics: RLayer[Registry, Registry] =
      ZLayer.fromZIO {
        ZIO
          .serviceWithZIO[Registry] { registry =>
            registry
              .updateRegistry(r => ZIO.attempt(jp.hotspot.DefaultExports.register(r)))
              .as(registry)
          }
      }

    def liveWithDefaultMetrics: TaskLayer[Registry] = live >>> defaultMetrics
  }

  def collectorRegistry: RIO[Registry, jp.CollectorRegistry] =
    ZIO.serviceWithZIO(_.collectorRegistry)
  def updateRegistry[A](f: jp.CollectorRegistry => Task[A]): RIO[Registry, A] =
    ZIO.serviceWithZIO(_.updateRegistry(f))
  def collect: RIO[Registry, ju.Enumeration[jp.Collector.MetricFamilySamples]] =
    ZIO.serviceWithZIO(_.collect)
  def string004: RIO[Registry, String] =
    ZIO.serviceWithZIO(_.string004)
}
