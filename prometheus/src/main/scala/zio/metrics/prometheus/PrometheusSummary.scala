package zio.metrics.prometheus

import zio.metrics.Summary
import zio.Task
import io.prometheus.client.{ Summary => PSummary }

trait PrometheusSummary extends Summary {

  type SummaryTimer = PSummary.Timer

  val summary = new Summary.Service[PSummary, SummaryTimer] {
    override def observe(s: PSummary, amount: Double, labelNames: Array[String]): Task[Unit] =
      Task( if (labelNames.isEmpty) s.observe(amount) else s.labels(labelNames: _*).observe(amount) )

    override def startTimer(s: PSummary, labelNames: Array[String]): Task[SummaryTimer] =
      Task( if (labelNames.isEmpty) s.startTimer() else s.labels(labelNames: _*).startTimer )

    override def observeDuration(timer: SummaryTimer): Task[Double] =
      Task(timer.observeDuration())

    override def time(s: PSummary, f: () => Unit, labelNames: Array[String]): Task[Double] =
      Task{
        val t = if (labelNames.isEmpty) s.startTimer() else s.labels(labelNames: _*).startTimer()
        f()
        t.observeDuration()
      }
  }
}

object PrometheusSummary extends PrometheusSummary
