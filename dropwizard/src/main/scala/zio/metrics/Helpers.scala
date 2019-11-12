package zio.metrics

import zio.RIO
import zio.metrics.dropwizard._
import com.codahale.metrics.{ Counter => DWCounter, Gauge => DWGauge }
import com.codahale.metrics.{ Histogram => DWHistogram, Meter => DWMeter }
import com.codahale.metrics.{ Timer => DWTimer }

object counter {
  def inc(c: DWCounter): RIO[DropWizardCounter, Unit] = RIO.accessM(_.counter.inc(c))

  def inc(c: DWCounter, amount: Double): RIO[DropWizardCounter, Unit] =
    RIO.accessM(_.counter.inc(c, amount))
}

object gauge {
  def getValue(g: DWGauge[Long]): RIO[DropWizardGauge, Long] =
    RIO.accessM(
      r =>
        for {
          l <- r.gauge.getValue[Long](g)
        } yield l
    )
}

object histogram {
  def update(h: DWHistogram, amount: Double): RIO[DropWizardHistogram, Unit] =
    RIO.accessM(_.histogram.update(h, amount))
}

object meter {
  def mark(m: DWMeter, amount: Long): RIO[DropWizardMeter, Unit] =
    RIO.accessM(_.meter.mark(m, amount))
}

object timer {
  def start(t: DWTimer): RIO[DropWizardTimer, DWTimer.Context] =
    RIO.accessM(_.timer.start(t))

  def stop(ctx: DWTimer.Context): RIO[DropWizardTimer, Long] =
    RIO.accessM(_.timer.stop(ctx))
}
