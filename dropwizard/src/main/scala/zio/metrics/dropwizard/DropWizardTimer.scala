package zio.metrics.dropwizard

import zio.Task
import zio.metrics.Timer
import com.codahale.metrics.{ Timer => DWTimer }

trait DropWizardTimer extends Timer {

  val timer = new Timer.Service[DWTimer, DWTimer.Context] {

    override def start(t: DWTimer): zio.Task[DWTimer.Context] =
      Task(t.time())

    override def stop(c: DWTimer.Context): Task[Long] =
      Task(c.stop())

  }
}

object DropWizardTimer extends DropWizardTimer
