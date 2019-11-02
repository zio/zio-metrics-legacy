package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Gauge
import com.codahale.metrics.{ Gauge => DWGauge }

trait DropWizardGauge extends Gauge {
  val gauge = new Gauge.Service[DWGauge[_]] {

    override def inc[A](g: DWGauge[_]): Task[Either[Unit, A]] =
      Task(Right(g.getValue().asInstanceOf[A]))
  }
}

object DropWizardGauge extends DropWizardGauge
