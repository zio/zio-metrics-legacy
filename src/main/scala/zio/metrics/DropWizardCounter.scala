package zio.metrics

import com.codahale.metrics.{Counter => DWCounter}
import zio.RIO

trait DropWizardCounter extends Counter {
  val counter = new Counter.Service[DropWizardRegistry] {

    private var dwCounter: RIO[DropWizardRegistry, Option[DWCounter]] = RIO(None)

    override def register[A: Show](label: Label[A]): zio.RIO[DropWizardRegistry,Unit] = {
      val name = Show[A].show(label.name)
      dwCounter = for {
        dr <- RIO.environment[DropWizardRegistry]
        mr <- dr.registry.build()
      } yield Some(mr.counter(name))

      RIO.unit
    }

    override def inc(): zio.RIO[DropWizardRegistry,Unit] =
      dwCounter.fold(_ => println("Error"), c => c.fold(println("No counter"))(_.inc()))

    override def inc(amount: Long): zio.RIO[DropWizardRegistry,Unit] =
      dwCounter.fold(_ => println("Error"), c => c.fold(println("No counter"))(_.inc(amount)))
  }
}
