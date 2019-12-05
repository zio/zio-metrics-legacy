---
id: essentials_sockets
title:  "Dropwizard ZIO WSrapper"
---

ZIO Metrics Dropwizard provides Dropwizard's 5 metrics plus a number of
reporters all connected through the `MetricRegistry`.

Required imports for presented snippets:

```scala mdoc:silent
import zio.{ RIO, Runtime, Task }
import zio.internal.PlatformLive
import com.codahale.metrics.MetricRegistry
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
```

We will also provide our own `Runtime`:

```scala mdoc:silent
  val rt = Runtime(
    new DropwizardRegistry with DropwizardReporters,
    PlatformLive.Default
  )
```

We will assume the reader has working knowledge for Prometheus already, if
not then head over to [Dropwizard Metrics
Core](https://metrics.dropwizard.io/4.0.0/manual/core.html).

## Registry
All metrics register themselves with the registry and all reporters need access
to the registry in order to be able to make their respective reports.
ZIO-Metrics expose it via [Ref](https://zio.dev/docs/datatypes/datatypes_ref)
providing methods to register every metric type plus one `getCurrent` method to
obtain the current `CollectorRegistry`

You can access the registry module using either environmental effects or
zio-metrics
[Helper](https://github.com/zio/zio-metrics/blob/master/dropwizard/src/main/scala/zio/metrics/dropwizard/Helpers.scala)
methods. We'll start using environmental effects until the `Helper` methods are introduced:

```scala mdoc:silent
  val testRegistry: RIO[DropwizardRegistry, (MetricRegistry, Counter)] = for {
    dwr <- RIO.environment[DropwizardRegistry]
    dwc <- dwr.registry.registerCounter(Label(DropwizardTests.getClass(), Array("test", "counter")))
    c   <- Task(new Counter(dwc))
    r   <- dwr.registry.getCurrent()
  } yield (r, c)
```

All `register*` methods in `DropwizardRegistry` require a `Label` object  (some
may require more parameters). A label is composed of a name and an array of
labels which may be empty in the case where no labels are required.

```scala mdoc:silent
case class Label[A: Show](name: A, labels: Array[String])
```

Note that zio-metrics does not depend on either cats or scalaz so this Show is
defined on ``typeclasses` with instances for String and Class[A]. 
Besides the `register*` functions, we also have `getCurrent()` that simply 
returns the `MetricsRegistry` which is needed by all `Reporters`.

Using the registry helper the above function becomes:
```scala mdoc:silent
  val testRegistryHelper: RIO[DropwizardRegistry, (MetricRegistry, Counter)] = for {
    c   <- registry.registerCounter("DropwizardRegistryHelper", Array("test", "counter"))
    r   <- registry.getCurrent()
  } yield (r, c)
```

## Counter
Counter has methods to increase a counter by 1 or by an arbitrary double
passed as a parameter along with optional labels.

```scala mdoc:silent
  val testCounter: RIO[DropwizardRegistry, MetricRegistry] = for {
    c   <- counter.register("DropwizardCounter")
    _   <- c.inc()
    _   <- c.inc(2.0)
    r   <- registry.getCurrent()
  } yield r
```

Or with labels:

```scala mdoc:silent
  val testLabeledCounter: RIO[DropwizardRegistry, MetricRegistry] = for {
    c   <- counter.register("DropwizardCounterHelper", Array("test", "counter"))
    _   <- c.inc()
    _   <- c.inc(2.0)
    r   <- registry.getCurrent()
  } yield r
```

Note that unlike with `Prometheus`, we don't need to add labels to our method
calls (i.e. `c.inc()`). This is because `Dropwizard` doesn't really support the
concept of labels, it just add them to the name so that
`counter.register("DropwizardCounterHelper", Array("test", "counter"))` becomes
`zio_metrics_DropwizardTests.test.counter`.

You can run and verify the results so:

```scala mdoc:silent
    val name = MetricRegistry.name("DropwizardCounter", Array("test", "counter"): _*)
    val r    = rt.unsafeRun(testCounter)
    val cs   = r.getCounters()
    val c    = if (cs.get(name) == null) 0 else cs.get(name).getCount
    assert(c == 3d)
```

## Gauge
A Gauge is a metric that knows how to obtain the current value of a (usually
long-running) function `() => A` such as `def currentTemperature(): Double` or
`def currentNumberOfConcurrentUsers(): Long`, etc. For instance:

```scala mdoc:silent
  val tester: () => Long = () => System.nanoTime()
  
  val testGauge: RIO[DropwizardRegistry, (MetricRegistry, Long)] = for {
    g <- gauge.register("DropwizardGauge", Array("test", "gauge"), tester)
    r <- registry.getCurrent()
    l <- g.getValue[Long]()
  } yield (r, l)
```
