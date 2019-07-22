package zio.metrics

import java.io.IOException

import io.prometheus.client._
import zio.metrics.Label._
import zio.metrics.Reservoir.{ Bounded, Config, ExponentiallyDecaying, Uniform }
import zio.{ IO, Task, UIO }
import scalaz.{ Semigroup, Show }

class PrometheusMetrics extends Metrics[Task[?], Summary.Timer] {

  val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

  override def counter[L: Show](label: Label[L]): Task[Long => UIO[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    val c = Counter
      .build()
      .name(lbl)
      //.labelNames(label.labels.map(Show[L].shows(_)): _*)
      .help(s"$lbl counter")
      .register()
    IO.effect { l: Long =>
      IO.succeedLazy(c.inc(l.toDouble))
    }
  }

  override def gauge[A, B: Semigroup, L: Show](label: Label[L])(
    f: Option[A] => B
  ): Task[Option[A] => UIO[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    val g = Gauge
      .build()
      .name(lbl)
      .help(s"$lbl gauge")
      .register()
    IO.effect(
      (op: Option[A]) =>
        IO.succeedLazy(f(op) match {
          case l: Long   => g.inc(l.toDouble)
          case d: Double => g.inc(d)
          case _         => ()
        })
    )
  }

  override def histogram[A: scala.Numeric, L: Show](label: Label[L], res: Reservoir[A])(
    implicit num: scala.Numeric[A]
  ): Task[A => Task[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    val h = Histogram
      .build()
      .name(lbl)
      .help(s"$lbl histogram")
      .register()
    IO.effect((a: A) => IO.effect(h.observe(num.toDouble(a))))
  }

  def processConfig(config: Option[Config], values: Tuple3[String, String, String]): Tuple3[Double, Double, Int] =
    config match {
      case None => (1.0, 1.0, 1)
      case Some(m) =>
        val d1 = m.getOrElse(values._1, DoubleZ(1.0)) match {
          case DoubleZ(d) => d
          case _          => 1.0
        }

        val d2: Double = m.getOrElse(values._2, DoubleZ(1.0)) match {
          case DoubleZ(d) => d
          case _          => 1.0
        }

        val i1: Int = m.getOrElse(values._3, IntegerZ(1)) match {
          case IntegerZ(i) => i
          case _           => 1
        }
        (d1, d2, i1)
    }

  def histogramTimer[A: scala.Numeric, L: Show](
    label: Label[L],
    res: Reservoir[A] = Reservoir.ExponentiallyDecaying(None)
  ): Task[() => Task[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    val hb = Histogram
      .build()
      .name(lbl)
      .help(s"$lbl histogram")

    val builder = res match {
      case Uniform(config) =>
        val c = processConfig(config, ("start", "width", "count"))
        hb.linearBuckets(c._1, c._2, c._3)
      case ExponentiallyDecaying(config) =>
        val c = processConfig(config, ("start", "factor", "count"))
        hb.exponentialBuckets(c._1, c._2, c._3)
      case Bounded(window @ _, unit @ _) => hb
    }

    val h = builder.register()

    IO.effect({
      val timer = h.startTimer()
      () =>
        IO.effect({
          timer.observeDuration()
          ()
        })
    })
  }

  type SummaryTimer = Summary.Timer

  class IOTimer(val ctx: SummaryTimer) extends Timer[Task[?], SummaryTimer] {
    override val a: SummaryTimer           = ctx
    override def start: Task[SummaryTimer] = IO.succeed(a)
    override def stop(io: Task[SummaryTimer]): Task[Double] =
      io.map(c => c.observeDuration())
  }

  override def timer[L: Show](label: Label[L]): IO[IOException, Timer[Task[?], SummaryTimer]] = {
    val lbl = Show[Label[L]].shows(label)
    val iot = IO.succeed(
      Summary
        .build()
        .name(lbl)
        .help(s"$lbl timer")
        .register()
    )
    val r = iot.map(s => new IOTimer(s.startTimer()))
    r
  }

  override def meter[L: Show](label: Label[L]): Task[Double => Task[Unit]] = {
    val lbl = Show[Label[L]].shows(label)
    val iot = IO.succeed(
      Summary
        .build()
        .name(lbl)
        .help(s"$lbl timer")
        .register()
    )
    IO.effect((d: Double) => iot.map(s => s.observe(d)))
  }

}

object PrometheusMetrics {
  implicit def DoubleSemigroup: Semigroup[Double] = Semigroup.instance((a, b) => a + b)
}
