package zio.metrics

import com.codahale.metrics.MetricRegistry.MetricSupplier
import com.codahale.metrics.Timer.Context
import com.codahale.metrics.{ Reservoir => DWReservoir, _ }
import zio.metrics.Label._
import zio.metrics.Reservoir._
import zio.{ Task, UIO, ZIO }
import scalaz.{ Semigroup, Show }

class DropwizardMetrics extends Metrics[Task[?], Context] {

  val registry: MetricRegistry = new MetricRegistry()

  override def counter[L: Show](label: Label[L]): Task[Long => UIO[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    ZIO.effect(
      (l: Long) => {
        ZIO.succeedLazy(registry.counter(lbl).inc(l))
      }
    )
  }

  override def gauge[A, B: Semigroup, L: Show](
    label: Label[L]
  )(f: Option[A] => B): Task[Option[A] => UIO[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    ZIO.effect(
      (op: Option[A]) =>
        ZIO.succeedLazy({
          registry.register(lbl, new Gauge[B]() {
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

  override def timer[L: Show](label: Label[L]): ZIO[Any, Nothing, IOTimer] = {
    val lbl = Show[Label[L]].shows(label)
    val iot = ZIO.succeed(registry.timer(lbl))
    val r   = iot.map(t => new IOTimer(t.time()))
    r
  }

  override def histogram[A: Numeric, L: Show](
    label: Label[L],
    res: Reservoir[A]
  )(
    implicit
    num: Numeric[A]
  ): Task[A => Task[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    val reservoir: DWReservoir = res match {
      case Uniform(config @ _)               => new UniformReservoir
      case ExponentiallyDecaying(config @ _) => new ExponentiallyDecayingReservoir
      case Bounded(window, unit)             => new SlidingTimeWindowReservoir(window, unit)
    }
    val supplier = new MetricSupplier[Histogram] {
      override def newMetric(): Histogram = new Histogram(reservoir)
    }

    ZIO.effect((a: A) => ZIO.effect(registry.histogram(lbl, supplier).update(num.toLong(a))))
  }

  override def meter[L: Show](label: Label[L]): Task[Double => Task[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    ZIO.effect(d => ZIO.succeed(registry.meter(lbl)).map(m => m.mark(d.toLong)))
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
