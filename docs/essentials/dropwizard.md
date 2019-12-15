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

You can run and verify the results so:

```scala mdoc:silent
  test("gauge increases in time") { () =>
    val name = MetricRegistry.name("DropwizardGauge", Array("test", "gauge"): _*)
    val r    = rt.unsafeRun(testGauge)
    val gs   = r._1.getGauges()
    val g    = if (gs.get(name) == null) Long.MaxValue else gs.get(name).getValue().asInstanceOf[Long]
    assert(r._2 < g && g < tester())
  },
  test("histogram increases in time") { () =>
    val name = MetricRegistry.name("DropwizardHistogram", Array("test", "histogram"): _*)
    val r    = rt.unsafeRun(testHistogram)
    val perc75th = r
```

`ZIO-Metrics` offers a `JSON Registry Printer` that can also be used to verify
results:

```scala mdoc:silent
  val str = for {
    dwr <- RIO.environment[DropwizardRegistry]
    j   <- DropwizardExtractor.writeJson(dwr)(None)
  } yield j.spaces2

  rt.unsafeRun(str >>= putStrLn)
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
    DropwizardRegistry with DropwizardReporters,
    DropwizardRegistry
  ] =
    for {
      dwr <- RIO.environment[DropwizardRegistry]
      r   <- dwr.registry.getCurrent()
      _   <- reporters.jmx(r)  // JMX reporter
      _   <- reporters.console(r, 2, TimeUnit.SECONDS)  // Console reporter
      c   <- counter.register(Show.fixClassName(DropwizardTests.getClass()), Array("test", "counter"))
      _   <- c.inc()
      _   <- c.inc(2.0)
      t   <- timer.register("DropwizardTimer", Array("test", "timer"))
      ctx <- t.start()
      l <- RIO.foreach(
            List(
              Thread.sleep(1000L),
              Thread.sleep(1400L),
              Thread.sleep(1200L)
            )
          )(_ => t.stop(ctx))
    } yield dwr

  override def run(args: List[String]) = {
    println("Starting tests")
    val json = rt.unsafeRun(tests >>= (dwr =>
    DropwizardExtractor.writeJson(dwr)(None))) // JSON Registry Printer
    RIO.sleep(Duration.fromScala(60.seconds))
    putStrLn(json.spaces2).map(_ => 0)
  }
```

This program starts the JMX reporter, then reports to the console every 2
seconds, starts a counter and a timer, waits for you (60 seconds) to verify the JMX values (with VisualVM or JConsole or
whatever), and then prints the registry as JSON.

Also note that the second parameter of `writeJson` is a `filter` of type
`Option[String]`. `None` as we used here, means `no filter` and is equivalent to
Dropwizard's `MetricFilter.ALL`. You can use `DropwizardRegistry.makeFilter` to
defines different filters.

## Histogram

`Histogram` metrics allow you to measure not just easy things like the min,
mean, max, and standard deviation of values, but also quantiles like the median
or 95th percentile. `Dropwizard` offers [4 different types of
histograms](https://metrics.dropwizard.io/4.0.0/manual/core.html#histograms),
based on the `Reservoir` type with `Uniform Reservoir` being the default type.

```scala mdoc:silent
  val testHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register("DropwizardHistogram", Array("test", "histogram"))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r
```

Here, `histogram` is a helper method that allows us to omit some parameter in
favour of just using default values, in this example we omit the `Reservoir`. Of
course you can pass your customized `Reservoir`, here we specify a sample size
of 512:

```scala mdoc:silent
  val testUniformHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register("DropwizardUniformHistogram", Array("uniform", "histogram"), new UniformReservoir(512))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r
```

All dropwizard's `Reservoir`'s provide a default implementation, for
`ExponentiallyDecayingReservoir`, for instance, it sets the sample size to 1028
with an alpha factor of 0.015 which offers a 99.9% confidence level with a 5%
margin of error (assuming a normal distribution) and heavily biases the
reservoir to the past 5 minutes of measurements:

```scala mdoc:silent
  val testExponentialHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register("DropwizardExponentialHistogram", Array("exponential", "histogram"), new ExponentiallyDecayingReservoir)
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r
```

Here's an example of a customized  `SlidingTimeWindowArrayReservoir`:

```scala mdoc:silent
  val testSlidingTimeWindowHistogram: RIO[DropwizardRegistry, MetricRegistry] = for {
    h <- histogram.register("DropwizardSlidingHistogram", Array("sliding", "histogram"), new SlidingTimeWindowArrayReservoir(30, TimeUnit.SECONDS))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.update(_))
    r <- registry.getCurrent()
  } yield r
```

which stores only the emasurements made in the las 30 seconds.


## Meter

Measures the rate at which a set of events occur:

```scala mdoc:silent
  val testMeter: RIO[DropwizardRegistry, MetricRegistry] = for {
    m <- meter.register("DropwizardMeter", Array("test", "meter"))
    _ <- RIO.foreach(Seq(1L, 2L, 3L, 4L, 5L))(m.mark(_))
    r <- registry.getCurrent()
  } yield r
```


## Timer

A `Timer` is basically a `histogram` of the duration of a type of event and a `meter`
of the rate of its occurrence.

```scala mdoc:silent
  val testTimer: RIO[DropwizardRegistry, (MetricRegistry, List[Long])] = for {
    r   <- registry.getCurrent()
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
  val name = MetricRegistry.name("DropwizardTimer", Array("test", "timer"): _*)
  val r    = rt.unsafeRun(testTimer)
  val meanRate = r._1
    .getTimers()
    .get(name)
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
have to do is define a function that takes a `DropWizardRegistry` module and
returns an http4s`Router` object:

```scala mdoc:silent
  val httpApp =
    (registry: DropwizardRegistry) =>
      Router(
        "/metrics" -> service.serveMetrics(registry)
      ).orNotFound
```

For measuring, we can reuse our testing function from the `Reporters` section:

```scala mdoc:silent
  val testServer: RIO[
    DropwizardRegistry with DropwizardReporters,
    DropwizardRegistry
  ] =
    for {
      dwr <- RIO.environment[DropwizardRegistry]
      r   <- dwr.registry.getCurrent()
      _   <- reporters.jmx(r)
      _   <- reporters.console(r, 30, TimeUnit.SECONDS)
      c   <- counter.register(Show.fixClassName(DropwizardTests.getClass()), Array("test", "counter"))
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
    } yield dwr
```

finally, we just have to call `Server.builder` and provide the environment:

```scala mdoc:silent
  override def run(args: List[String]) = {
    val kApp: Task[KleisliApp] = testServer.map(r => httpApp(r)).provideSome(_ => {
      new DropwizardRegistry with DropwizardReporters
    })

    val app: RIO[HttpEnvironment, Unit] = kApp >>= builder

    app
      .catchAll(t => putStrLn(s"$t"))
      .provideSome[HttpEnvironment] { rt =>
        new Clock with Console with System with Random with Blocking {
          override val clock: Clock.Service[Any]       = rt.clock
          override val console: Console.Service[Any]   = rt.console
          override val system: System.Service[Any]     = rt.system
          override val random: Random.Service[Any]     = rt.random
          override val blocking: Blocking.Service[Any] = rt.blocking
        }
      }
      .run
      .map(r => { println(s"Exiting $r"); 0})
  }
```
