package zio.metrics

import com.codahale.metrics.MetricRegistry.MetricSupplier
import com.codahale.metrics.Timer.Context
import com.codahale.metrics.{ Reservoir => DWReservoir, _ }
import zio.metrics.Reservoir._
import zio.{ Task, UIO, ZIO }
import scalaz.{ Semigroup, Show }

class DropwizardMetrics extends Metrics[Task[?], Context] {

  val registry: MetricRegistry = new MetricRegistry()

  override def counter[A: Show](label: Label[A]): Task[Long => UIO[Unit]] = {
    val name = MetricRegistry.name(Show[A].shows(label.name), label.labels:_*)
    ZIO.effect(
      (l: Long) => {
        ZIO.succeedLazy(registry.counter(name).inc(l))
      }
    )
  }

  override def gauge[A, B: Semigroup, S: Show](
    label: Label[S]
  )(f: Option[A] => B): Task[Option[A] => UIO[Unit]] = {
    val name = MetricRegistry.name(Show[S].shows(label.name), label.labels:_*)
    ZIO.effect(
      (op: Option[A]) =>
        ZIO.succeedLazy({
          registry.register(name, new Gauge[B]() {
            override def getValue: B = f(op)
          })
          ()
        })
    )
  }

  class IOTimer(val ctx: Context) extends Timer[Task[?], Context] {
    override val a: Context           = ctx
    override def start: Task[Context] = ZIO.succeed(a)
    override def stop(io: Task[Context]): Task[Double] =
      io.map(c => c.stop().toDouble)
  }

  override def timer[A: Show](label: Label[A]): ZIO[Any, Nothing, IOTimer] = {
    val name = MetricRegistry.name(Show[A].shows(label.name), label.labels:_*)
    val iot = ZIO.succeed(registry.timer(name))
    val r   = iot.map(t => new IOTimer(t.time()))
    r
  }

  override def histogram[A: Numeric, S: Show](
    label: Label[S],
    res: Reservoir[A]
  )(
    implicit
    num: Numeric[A]
  ): Task[A => Task[Unit]] = {
    val name = MetricRegistry.name(Show[S].shows(label.name), label.labels:_*)
    val reservoir: DWReservoir = res match {
      case Uniform(config @ _)               => new UniformReservoir
      case ExponentiallyDecaying(config @ _) => new ExponentiallyDecayingReservoir
      case Bounded(window, unit)             => new SlidingTimeWindowReservoir(window, unit)
    }
    val supplier = new MetricSupplier[Histogram] {
      override def newMetric(): Histogram = new Histogram(reservoir)
    }

    ZIO.effect((a: A) => ZIO.effect(registry.histogram(name, supplier).update(num.toLong(a))))
  }

  override def meter[A: Show](label: Label[A]): Task[Double => Task[Unit]] = {
    val name = MetricRegistry.name(Show[A].shows(label.name), label.labels:_*)
    ZIO.effect(d => ZIO.succeed(registry.meter(name)).map(m => m.mark(d.toLong)))
  }
}

object DropwizardMetrics {
  def makeFilter(filter: Option[String]): MetricFilter = filter match {
    case Some(s) =>
      s.charAt(0) match {
        case '+' => MetricFilter.startsWith(s.substring(1))
        case '-' => MetricFilter.endsWith(s.substring(1))
        case _   => MetricFilter.contains(s)
      }
    case _ => MetricFilter.ALL
  }
}
