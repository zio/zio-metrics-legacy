package zio.metrics

import java.io.IOException

import io.prometheus.client._
import zio.metrics.Reservoir.{ Bounded, Config, ExponentiallyDecaying, Uniform }
import zio.{ IO, Task, UIO }

class PrometheusMetrics extends Metrics[Task[?], Summary.Timer] {

  val registry: CollectorRegistry = CollectorRegistry.defaultRegistry

  override def counter[A: Show](label: Label[A]): Task[Long => UIO[Unit]] = {
    val name = Show[A].show(label.name)
    val c = Counter
      .build()
      .name(name)
      .labelNames(label.labels: _*)
      .help(s"$name counter")
      .register()
    IO.effect { l: Long =>
      {
        IO.effectTotal(c.labels(label.labels: _*).inc(l.toDouble))
      }
    }
  }

  override def gauge[A, B: Semigroup, L: Show](label: Label[L])(
    f: Option[A] => B
  ): Task[Option[A] => UIO[Unit]] = {
    val name = Show[L].show(label.name)
    val g = Gauge
      .build()
      .name(name)
      .labelNames(label.labels: _*)
      .help(s"$name gauge")
      .register()
    IO.effect(
      (op: Option[A]) =>
        IO.effectTotal(f(op) match {
          case l: Long   => g.labels(label.labels: _*).inc(l.toDouble)
          case d: Double => g.labels(label.labels: _*).inc(d)
          case _         => ()
        })
    )
  }

  override def histogram[A: scala.Numeric, L: Show](label: Label[L], res: Reservoir[A])(
    implicit num: scala.Numeric[A]
  ): Task[A => Task[Unit]] = {
    val name = Show[L].show(label.name)
    val h = Histogram
      .build()
      .name(name)
      .labelNames(label.labels: _*)
      .help(s"$name histogram")
      .register()
    IO.effect((a: A) => IO.effect(h.labels(label.labels: _*).observe(num.toDouble(a))))
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
    val name = Show[L].show(label.name)
    val hb = Histogram
      .build()
      .name(name)
      .labelNames(label.labels: _*)
      .help(s"$name histogram")

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
    val name = Show[L].show(label.name)
    val iot = IO.succeed(
      Summary
        .build()
        .name(name)
        .labelNames(label.labels: _*)
        .help(s"$name timer")
        .register()
    )
    val r = iot.map(s => new IOTimer(s.labels(label.labels: _*).startTimer()))
    r
  }

  override def meter[L: Show](label: Label[L]): Task[Double => Task[Unit]] = {
    val name = Show[L].show(label.name)
    val iot = IO.succeed(
      Summary
        .build()
        .name(name)
        .labelNames(label.labels: _*)
        .help(s"$name timer")
        .register()
    )
    IO.effect((d: Double) => iot.map(s => s.labels(label.labels: _*).observe(d)))
  }

}
