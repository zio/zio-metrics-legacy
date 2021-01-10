package zio.metrics

import io.prometheus.{ client => jp }

import zio._

import java.io.StringWriter
import java.{ util => ju }

package object prometheus {
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

    private def createService(registry: jp.CollectorRegistry, lock: Semaphore): Service = new Service {
      def collectorRegistry: UIO[jp.CollectorRegistry] = ZIO.succeed(registry)
      def updateRegistry[A](f: jp.CollectorRegistry => zio.Task[A]): zio.Task[A] = lock.withPermit {
        f(registry)
      }
      def collect: zio.UIO[ju.Enumeration[jp.Collector.MetricFamilySamples]] =
        ZIO.effectTotal(registry.metricFamilySamples())
    }

    def live: ULayer[Registry] =
      Semaphore.make(1).map(createService(new jp.CollectorRegistry(), _)).toLayer

    def default: ULayer[Registry] =
      Semaphore.make(1).map(createService(jp.CollectorRegistry.defaultRegistry, _)).toLayer

    def defaultMetrics: ZLayer[Registry, Throwable, Registry] = ZLayer.fromServiceM { registry =>
      registry.updateRegistry(r => ZIO.effect(jp.hotspot.DefaultExports.register(r))).as(registry)
    }

    def liveWithDefaultMetrics: TaskLayer[Registry] = live >>> defaultMetrics

    def importRegistry: jp.CollectorRegistry => ZLayer[Any, Nothing, Registry] =
      collectorRegistry => Semaphore.make(1).map(createService(collectorRegistry, _)).toLayer
  }

  def collectorRegistry: RIO[Registry, jp.CollectorRegistry] =
    ZIO.accessM(_.get.collectorRegistry)
  def updateRegistry[A](f: jp.CollectorRegistry => Task[A]): RIO[Registry, A] =
    ZIO.accessM(_.get.updateRegistry(f))
  def collect: RIO[Registry, ju.Enumeration[jp.Collector.MetricFamilySamples]] =
    ZIO.accessM(_.get.collect)
  def string004: RIO[Registry, String] =
    ZIO.accessM(_.get.string004)
}
