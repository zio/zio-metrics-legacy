package zio.metrics.prometheus

import zio.metrics.Summary
import zio.Task
import io.prometheus.client.{ Summary => PSummary }

trait PrometheusSummary extends Summary {

  type SummaryTimer = PSummary.Timer

  val summary = new Summary.Service[PSummary, SummaryTimer] {
    override def observe(s: PSummary, amount: Double): Task[Unit] =
      Task(s.observe(amount))

    override def startTimer(s: PSummary): Task[SummaryTimer] =
      Task(s.startTimer())

    override def observeDuration(timer: SummaryTimer): Task[Double] =
      Task(timer.observeDuration())

    override def time(s: PSummary, f: () => Unit): Task[Double] =
      Task{
        val t = s.startTimer()
        f()
        t.observeDuration()
      }
  }
}

object PrometheusSummary extends PrometheusSummary
