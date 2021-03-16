---
id: essentials_dropwizard
title:  "Dropwizard ZIO WSrapper"
---

ZIO Metrics Dropwizard provides Dropwizard's 5 metrics plus a number of
reporters all connected through the `MetricRegistry`.

Required imports for presented snippets:

```scala mdoc:silent
import zio.{ RIO, Runtime, Task }
import com.codahale.metrics.{ MetricRegistry, Counter => DWCounter }
import zio.metrics.{ Label => ZLabel, Show }
import zio.metrics.dropwizard._
import zio.metrics.dropwizard.helpers._
import zio.metrics.dropwizard.reporters._
import com.codahale.metrics.UniformReservoir
import com.codahale.metrics.ExponentiallyDecayingReservoir
import com.codahale.metrics.SlidingTimeWindowArrayReservoir


// also for printing debug messages to the console
import zio.console.{ Console, putStrLn }

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import zio.duration.Duration
```

We will also provide our own `Runtime` which will use ZIOMetric Dropwizard's
`Registry` layer  plus its Reporters layer plus ZIO's `Console` layer:

```scala mdoc:silent
  val rt = Runtime.unsafeFromLayer(Registry.live ++ Reporters.live ++ Console.live)
```

We will assume the reader has working knowledge for Dropwizard already, if
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
  val testRegistry: RIO[Registry, (MetricRegistry, Counter)] = for {
    dwr <- RIO.environment[Registry]
    dwc <- dwr.get.registerCounter(ZLabel("DropwizardTests", Array("test", "counter"), "Just a counter for your consideration"))
    c   <- Task(new Counter(dwc))
    r   <- dwr.get.getCurrent()
  } yield (r, c)
```

All `register*` methods in `Registry` require a `Label` object  (some
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
  val testRegistryHelper: RIO[Registry, (MetricRegistry, DWCounter)] = for {
    c   <- registerCounter("RegistryHelper", Array("test", "counter"))
    r   <- getCurrentRegistry()
  } yield (r, c)
```

### Provide the MetricRegistry explicitly
If, for whatever reason, you already have a `MetricRegistry` in your app and
want to pass that to `zio-metrics-dropwizard`, you can use the `explicit` layer
intead of `live`. You just need to feed an `Option[MetricRegistry]` to the
`explicit` layer so:

```scala
 val myRegistry = MetricRegistry.defaultRegistry
  val preCounter = myRegistry.counter("PreExistingCounter")
  preCounter.inc(9)

  val myCustomLayer = ZLayer.succeed[Option[MetricRegistry]](Some(myRegistry)) >>> Registry.explicit
```

In this example we create a `MetricRegistry` external to `zio-metrics`
(`myRegistry`), add a counter and increase it to 9. The next step is to `lift`
`myRegistry` to a layer (i.e. create a Layer that takes `Nothing` and outputs
`Option[MetricRegistry]`) and compose it with `Registry.explicit`. The you
just need to use `myCustomLayer` wherever you would have used `Registry.live`,
i.e. `counter.register(name, Array("exporter")).provideLayer(myCustomLayer)` or `val rt = Runtime.unsafeFromLayer(myCustomLayer ++ Console.live)`

## Counter
Counter has methods to increase a counter by 1 or by an arbitrary double
passed as a parameter along with optional labels.

```scala mdoc:silent
  val testCounter: RIO[Registry, MetricRegistry] = for {
    c   <- counter.register("DropwizardCounter")
    _   <- c.inc()
    _   <- c.inc(2.0)
    r   <- getCurrentRegistry()
  } yield r
```

Or with labels:

```scala mdoc:silent
  val testLabeledCounter: RIO[Registry, MetricRegistry] = for {
    c   <- counter.register("DropwizardCounterHelper", Array("test", "counter"))
    _   <- c.inc()
    _   <- c.inc(2.0)
    r   <- getCurrentRegistry()
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
```

## Gauge
A Gauge is a metric that knows how to obtain the current value of a (usually
long-running) function `() => A` such as `def currentTemperature(): Double` or
`def currentNumberOfConcurrentUsers(): Long`, etc. For instance:

```scala mdoc:silent
  val tester: () => Long = () => System.nanoTime()
  
  val testGauge: RIO[Registry, (MetricRegistry, Long)] = for {
    g <- gauge.register("DropwizardGauge", Array("test", "gauge"), tester)
    r <- getCurrentRegistry()
    l <- g.getValue[Long]()
  } yield (r, l)
```

You can run and verify the results so:

```scala mdoc:silent
    val gaugeName = MetricRegistry.name("DropwizardGauge", Array("test", "gauge"): _*)
    val rGauge    = rt.unsafeRun(testGauge)
    val gs   = rGauge._1.getGauges()
    val g    = if (gs.get(gaugeName) == null) Long.MaxValue else gs.get(gaugeName).getValue().asInstanceOf[Long]
```

`ZIO-Metrics` offers a `JSON Registry Printer` that can also be used to verify
results:

```scala mdoc:silent
  val str = for {
    r <- getCurrentRegistry()
    j <- DropwizardExtractor.writeJson(r)(None)
    _ <- putStrLn(j.spaces2)
  } yield ()

  rt.unsafeRun(str)
```

Let's discuss `Reporters` next.

## Reporters

`Reporters` are the way that your application [exports all the measurements
being made by its
metrics](https://metrics.dropwizard.io/4.0.0/manual/core.html#reporters).

`Dropwizard` provides 5 basic reporters and ZIO Metrics adds a JSON Registry Printer:

1. JMX
2. Console
3. SLF4J
4. CSV
5. Graphite
6. JSON Registry Printer

Let's combine a couple of them:

```scala mdoc:silent
  val tests: RIO[
    Registry with Reporters,
    MetricRegistry
  ] =
    for {
      r   <- getCurrentRegistry()
      _   <- jmx(r)  // JMX reporter
      _   <- console(r, 2, TimeUnit.SECONDS)  // Console reporter
      c   <- counter.register("DropwizardTestsReporter", Array("test", "counter"))
      _   <- c.inc()
      _   <- c.inc(2.0)
      t   <- timer.register("DropwizardTimer", Array("test", "timer"))
      ctx <- t.start()
      _ <- RIO.foreach(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(ctx))
    } yield r

  def run(args: List[String]) = {
    val json = rt.unsafeRun(tests >>= (r =>
    DropwizardExtractor.writeJson(r)(None))) // JSON Registry Printer
    RIO.sleep(Duration.fromScala(60.seconds))
    putStrLn(json.spaces2).map(_ => 0)
  }
```

This program starts the JMX reporter, then reports to the console every 2
seconds, starts a counter and a timer, waits for you (60 seconds) to verify the JMX values (with VisualVM or JConsole or
whatever), and then prints the registry as JSON.

Also note that the second parameter of `writeJson` is a `filter` of type
`Option[String]`. `None` as we used here, means `no filter` and is equivalent to
Dropwizard's `MetricFilter.ALL`. You can use `Registry.makeFilter` to
defines different filters.

## Histogram

`Histogram` metrics allow you to measure not just easy things like the min,
mean, max, and standard deviation of values, but also quantiles like the median
or 95th percentile. `Dropwizard` offers [4 different types of
histograms](https://metrics.dropwizard.io/4.0.0/manual/core.html#histograms),
based on the `Reservoir` type with `Uniform Reservoir` being the default type.

```scala mdoc:silent
  val testHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register("DropwizardHistogram", Array("test", "histogram"))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- getCurrentRegistry()
  } yield r
```

Here, `histogram` is a helper method that allows us to omit some parameter in
favour of just using default values, in this example we omit the `Reservoir`. Of
course you can pass your customized `Reservoir`, here we specify a sample size
of 512:

```scala mdoc:silent
  val testUniformHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register("DropwizardUniformHistogram", Array("uniform", "histogram"), new UniformReservoir(512))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- getCurrentRegistry()
  } yield r
```

All dropwizard's `Reservoir`'s provide a default implementation, for
`ExponentiallyDecayingReservoir`, for instance, it sets the sample size to 1028
with an alpha factor of 0.015 which offers a 99.9% confidence level with a 5%
margin of error (assuming a normal distribution) and heavily biases the
reservoir to the past 5 minutes of measurements:

```scala mdoc:silent
  val testExponentialHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register("DropwizardExponentialHistogram", Array("exponential", "histogram"), new ExponentiallyDecayingReservoir)
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- getCurrentRegistry()
  } yield r
```

Here's an example of a customized  `SlidingTimeWindowArrayReservoir`:

```scala mdoc:silent
  val testSlidingTimeWindowHistogram: RIO[Registry, MetricRegistry] = for {
    h <- histogram.register("DropwizardSlidingHistogram", Array("sliding", "histogram"), new SlidingTimeWindowArrayReservoir(30, TimeUnit.SECONDS))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- getCurrentRegistry()
  } yield r
```

which stores only the emasurements made in the las 30 seconds.


## Meter

Measures the rate at which a set of events occur:

```scala mdoc:silent
  val testMeter: RIO[Registry, MetricRegistry] = for {
    m <- meter.register("DropwizardMeter", Array("test", "meter"))
    _ <- RIO.foreach(Seq(1L, 2L, 3L, 4L, 5L))(m.mark(_))
    r <- getCurrentRegistry()
  } yield r
```


## Timer

A `Timer` is basically a `histogram` of the duration of a type of event and a `meter`
of the rate of its occurrence.

```scala mdoc:silent
  val testTimer: RIO[Registry, (MetricRegistry, List[Long])] = for {
    r   <- getCurrentRegistry()
    t   <- timer.register("DropwizardTimer", Array("test", "timer"))
    ctx <- t.start()
    l <- RIO.foreach(
          List(
            Thread.sleep(1000L),
            Thread.sleep(1400L),
            Thread.sleep(1200L)
          )
        )(_ => t.stop(ctx))
  } yield (r, l)
```

As usual, you can verify its data directly:

```scala mdoc:silent
  val timerName = MetricRegistry.name("DropwizardTimer", Array("test", "timer"): _*)
  val rTimer    = rt.unsafeRun(testTimer)
  val meanRate = rTimer._1
    .getTimers()
    .get(timerName)
    .getMeanRate
```

Or with one of the reporters explained above, such as `DropwizardExtractor.writeJson(dwr)(None)`.

## Server

ZIO-Metrics Dropwizard provides an http4s-based server that can be used to
observe the registry in json format. Unfortunately, due to http4s [dropping
support for Scala 2.11 on 0.21.0-M6](https://http4s.org/versions/) and
incompatibility issues between `Cats` versions: earlier versions of http4s that
support Scala 2.11 use `Cats 1.x` but the current `zio-interop-cats` uses `Cats
2.0.0`. This means that, currently, the `Server` only works for Scala 2.12.

ZIo-Metrics defines the `Server.builder` method and a `MetricsService` module.
Between the two, you get a basic Http Server and the functionality to receive a
`filter` and obtain the curent contents of a `MetricRegistry` as JSON. All you
have to do is define a function that takes a `MetricRegistry` and
returns an http4s`Router` object:

```scala mdoc:silent
  import zio.interop.catz._
  import org.http4s.implicits._
  import org.http4s.server.Router
  import zio.metrics.dropwizard.Server._

  val httpApp =
    (registry: MetricRegistry) =>
      Router(
        "/metrics" -> Server.serveMetrics(registry)
      ).orNotFound
```

For measuring, we can reuse our testing function from the `Reporters` section:

```scala mdoc:silent
  val testServer: RIO[
    Registry with Reporters,
    MetricRegistry
  ] =
    for {
      r   <- getCurrentRegistry()
      _   <- jmx(r)
      _   <- helpers.console(r, 30, TimeUnit.SECONDS) // to differentiate from zio.console
      c   <- counter.register("DropwizardServerTests", Array("test", "counter"))
      _   <- c.inc()
      _   <- c.inc(2.0)
      t   <- timer.register("DropwizardTimer", Array("test", "timer"))
      ctx <- t.start()
      _ <- RIO.foreach(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(ctx))
    } yield r
```

finally, we just have to call `Server.builder` and provide the environment:

```scala mdoc:silent
  def runServer(args: List[String]) = {
    val kApp: Task[KleisliApp] = testServer
      .map(r => httpApp(r))
      .provideLayer(Registry.live ++ Reporters.live)

    val app: RIO[HttpEnvironment, Unit] = kApp >>= builder

    app
      .catchAll(t => putStrLn(s"$t"))
      .run
      .map(r => { println(s"Exiting $r"); 0})
  }
```

## Dependency Injection with ZLayers
Both `Prometheus` and `Dropwizard` zio-metrics implementation use `ZLayers` so
please refer to this section on [the Prometheus section](prometheus.md) for examples.
