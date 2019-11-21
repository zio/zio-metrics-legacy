package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Gauge
import com.codahale.metrics.{ Gauge => DWGauge }

trait DropWizardGauge extends Gauge {
  val gauge = new Gauge.Service[DWGauge[_]] {

    override def getValue[A](g: DWGauge[_]): Task[A] =
      Task(g.getValue().asInstanceOf[A])

  }
}

object DropWizardGauge extends DropWizardGauge
