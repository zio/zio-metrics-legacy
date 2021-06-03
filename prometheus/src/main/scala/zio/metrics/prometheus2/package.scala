package zio.metrics

import io.prometheus.{ client => jp }

import zio._

import java.io.StringWriter
import java.{ util => ju }

package object prometheus2 {
  type Registry = Has[Registry.Service]

  object Registry {
    trait Service {
      def collectorRegistry: UIO[jp.CollectorRegistry]
      def updateRegistry[A](f: jp.CollectorRegistry => Task[A]): Task[A]
      def collect: UIO[ju.Enumeration[jp.Collector.MetricFamilySamples]]
      def string004: UIO[String] = collect >>= { sampled =>
        ZIO.effectTotal {
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
        ZIO.effectTotal(registry.metricFamilySamples())
    }
    private object ServiceImpl {
      def makeWith(registry: jp.CollectorRegistry): UIO[ServiceImpl] =
        Semaphore
          .make(permits = 1)
          .map(new ServiceImpl(registry, _))
    }

    def live: ULayer[Registry] = ServiceImpl.makeWith(new jp.CollectorRegistry()).toLayer

    def default: ULayer[Registry] = ServiceImpl.makeWith(jp.CollectorRegistry.defaultRegistry).toLayer

    def provided: URLayer[jp.CollectorRegistry, Registry] = ZLayer.fromFunctionM(ServiceImpl.makeWith)

    def defaultMetrics: RLayer[Registry, Registry] = ZLayer.fromServiceM { registry =>
      registry
        .updateRegistry(r => ZIO.effect(jp.hotspot.DefaultExports.register(r)))
        .as(registry)
    }

    def liveWithDefaultMetrics: TaskLayer[Registry] = live >>> defaultMetrics
  }

  def collectorRegistry: RIO[Registry, jp.CollectorRegistry] =
    ZIO.serviceWith(_.collectorRegistry)
  def updateRegistry[A](f: jp.CollectorRegistry => Task[A]): RIO[Registry, A] =
    ZIO.serviceWith(_.updateRegistry(f))
  def collect: RIO[Registry, ju.Enumeration[jp.Collector.MetricFamilySamples]] =
    ZIO.serviceWith(_.collect)
  def string004: RIO[Registry, String] =
    ZIO.serviceWith(_.string004)
}
