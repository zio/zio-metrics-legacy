---
id: essentials_prometheus
title:  "Prometheus ZIO Wrapper"
---

ZIO Metrics Prometheus provides Prometheus' 4 metrics plus a number of
exporters all connected through the `CollectorRegistry`.

Required imports for presented snippets:

```scala mdoc:silent
import zio.{ RIO, Runtime }
import io.prometheus.client.CollectorRegistry
import zio.metrics.{ Label => ZLabel, Show }
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio.metrics.prometheus.exporters._

// also for printing debug messages to the console
import zio.console.{ Console, putStrLn }
// and for sleeping/clocks
import zio.clock.Clock
import zio.duration.Duration
import scala.concurrent.duration._
// and for inspecting prometheus
import java.util
```

We will also provide our own `Runtime`:

```scala mdoc:silent
  val rt = Runtime
    .unsafeFromLayer(Registry.live ++ Exporters.live ++ Console.live ++ Clock.live)
```

We include ALL 5 metrics in the `runtime` since we will see examples for each, normally you
just need to include the ones you will actually use.


We will assume the reader has working knowledge for Prometheus already, if
not then head over to [Prometheus Java Client](https://github.com/prometheus/client_java).

## Registry
All metrics register themselves with the registry and all exporters need access
to the registry in order to obtain and export its registered metrics.
ZIO-Metrics expose it via [Ref](https://zio.dev/docs/datatypes/datatypes_ref)
providing methods to register every metric type plus one `getCurrent` method to
obtain the current `CollectorRegistry`

You can access the registry module using either environmental effects or
zio-metrics
[Helper](https://github.com/zio/zio-metrics/blob/master/prometheus/src/main/scala/zio/metrics/prometheus/Helpers.scala)
methods. We'll start using environmental effects until the `Helper` methods are introduced:

```scala mdoc:silent
  val testRegistry: RIO[Registry, CollectorRegistry] = for {
    pr <- RIO.environment[Registry]
    _  <- pr.get.registerCounter(ZLabel("simple_counter", Array("method")))
    r  <- pr.get.getCurrent()
  } yield r
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
returns the `CollectorRegistry` which is needed by all `Exporters`.

Using the registry helper the above function becomes:

```scala mdoc:silent
  val testRegistryHelper: RIO[Registry, CollectorRegistry] = for {
    _  <- registerCounter("simple_counter", Array("method"))
    r  <- getCurrentRegistry()
  } yield r
```

### Pass the CollectorRegistry explicitly
If, for whatever reason, you already have a `CollectorRegistry` in your app and
want to pass that to `zio-metrics-prometheus`, you can use the `explicit` layer
intead of `live`. You just need to feed an `Option[CollectorRegistry]` to the
`explicit` layer so:

```scala
 val myRegistry = CollectorRegistry.defaultRegistry
  val preCounter = PCounter
    .build()
    .name("PreExistingCounter")
    .help("Counter configured before using zio-metrics")
    .register(myRegistry)
  preCounter.inc(9)

  val myCustomLayer = ZLayer.succeed[Option[CollectorRegistry]](Some(myRegistry)) >>> Registry.explicit
```

In this example we create a `CollectorRegistry` external to `zio-metrics`
(`myRegistry`), add a counter and increase it to 9. The next step is to `lift`
`myRegistry` to a layer (i.e. create a Layer that takes `Nothing` and outputs
`Option[CollectorRegistry]`) and compose it with `Registry.explicit`. The you
just need to use `myCustomLayer` wherever you would have used `Registry.live`,
i.e. `counter.register(name, Array("exporter")).provideLayer(myCustomLayer)` or `val rt = Runtime.unsafeFromLayer(myCustomLayer ++ Exporters.live ++ Console.live)`

## Counter
Counter has methods to increase a counter by 1 or by an arbitrary double
passed as a parameter along with optional labels.

```scala mdoc:silent
  val testCounter: RIO[Registry, CollectorRegistry] = for {
    c <- Counter("simple_counter", Array.empty[String])
    _ <- c.inc()
    _ <- c.inc(2.0)
    r <- getCurrentRegistry()
  } yield r
```

If the counter is registered with labels, then you need to increase the counter
  passing the same number of labels when registered.

```scala mdoc:silent
  val testLabeledCounter: RIO[Registry, CollectorRegistry] = for {
    c  <- Counter("simple_counter_labeled", Array("method", "resource"))
    _  <- c.inc(Array("get", "users"))
    _  <- c.inc(2.0, Array("get", "users"))
    r  <- getCurrentRegistry()
  } yield r
```

You can run and verify the results so:

```scala mdoc:silent
  val set: util.Set[String] = new util.HashSet[String]()
  set.add("simple_counter")
  val r = rt.unsafeRun(testCounter)
  val count = r
    .filteredMetricFamilySamples(set)
    .nextElement()
    .samples
    .get(0)
    .value
  assert(count == 3.0)
```

There's an easier way to observe the state of the `CollectorRegistry` using the
`write004` Exporter covered later.

## Helpers
Environmental effects gives us access direct access to zio-metrics' `Metrics`
which means we have to pass empty arrays and other `Default` parameters when
we do not use any labels or special configuration. Helper methods take care of all the boilerplate
and gives us easy-to-use functions. Using them out `testCounter` example above
becomes:

```scala mdoc:silent
  val testCounterHelper: RIO[Registry, CollectorRegistry] = for {
    c <- counter.register("PrometheusTestHelper")
    _ <- c.inc()
    _ <- c.inc(2.0)
    r <- getCurrentRegistry()
  } yield r
```

This version accepts directly either a `String` for a name, the
helper method creates the empty array the labels parameter needs. There are also
helper methods that supports an array for labels:

```
    c <- counter.register("PrometheusTestHelper", Array("method", "resource"))
```

From here on, we will use environmental effects, helper methods, or a mix of both.

## Gauge
Besides increasing, a gauge may also decrease and has methods to `set` and `get`
values as well as to `setToTime` and `setToCurrentTime`. 

```scala mdoc:silent
  val testGauge: RIO[Registry, (CollectorRegistry, Double)] = for {
    g  <- gauge.register("simple_gauge")
    _  <- g.inc()
    _  <- g.inc(2.0)
    _  <- g.dec(1.0)
    d  <- g.getValue()
    r  <- getCurrentRegistry()
  } yield (r, d)
```

Here I chose to return both the `CollectorRegistry` and the latest value from
the gauge as a tuple. `gauge` refers to the [helper
method](https://github.com/zio/zio-metrics/blob/master/prometheus/src/main/scala/zio/metrics/prometheus/Helpers.scala).

With labels it looks like this:

```scala mdoc:silent
  val testLabeledGauge: RIO[Registry, (CollectorRegistry, Double)] = for {
    g  <- Gauge("simple_gauge", Array("method"))
    _  <- g.inc(Array("get"))
    _  <- g.inc(2.0, Array("get"))
    _  <- g.dec(1.0, Array("get"))
    d  <- g.getValue(Array("get"))
    r  <- getCurrentRegistry()
  } yield (r, d)
```

And to run and verify the result:
```scala mdoc:silent
  val setG: util.Set[String] = new util.HashSet[String]()
  setG.add("simple_gauge")
  val rG = rt.unsafeRun(testGauge)
  val a1 = rG._1
    .filteredMetricFamilySamples(setG)
    .nextElement()
    .samples
    .get(0)
    .value

  assert(a1 == rG._2)
  assert(a1 == 2.0)
```

This way to verify our metrics is awkward and cumbersome, let's look at what
`Exporters` can do for us in this regard.

## Exporters

Prometheus provides 4 main exporters:

1. Http
2. Pushgateways
3. Graphite Bridges
4. TextFormat
5. DefaultExports

TextFormat is a Prometheus Exporter with a single method `write004` which writes the
contents of a `CollectorRegistry` in the 004 content-type encoding that
Prometheus expects. That's what we will use to verify our metrics from now on.

DefaultExports, on the other hand, registers a number of HotSpot collectors to a
given registry. Let's look at an example of how all this works. 

```scala mdoc:silent
  import io.prometheus.client.exporter.HTTPServer
  
  val exporterTest: RIO[
    Registry with Exporters with Console,
    HTTPServer
  ] =
    for {
      r  <- getCurrentRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      c  <- Counter("ExportersTest", Array("exporter"))
      _  <- c.inc(Array("counter"))
      _  <- c.inc(2.0, Array("counter"))
      h  <- histogram.register("export_histogram", Array("exporter", "method"))
      _  <- h.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- putStrLn(s)
    } yield hs

  def main(args: Array[String]): Unit =
    rt.unsafeRun(exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}")))
```

Where `>>=` = `flatMap`. Also note that `exporters` refers to the helper object
and that in this example we are returning the `HTTPServer` object used by
Prometheus, we could potentially use it (for instance) to stop the
server,`exporters` helper has the method `stopHttp` for that.
Besides printing the metrics to the console you can also ask them via http `curl
http://localhost:9090`. Since we are already using `histogram` in this example,
let's look at it in more detail next.

## Histogram
Histogram has 4 modes we can use:
1. We can `time` how long a function takes to complete.
2. We can `observe` arbitrary `Double` values.
3. We can start an arbitrary `timer` and `observe` its duration.
4. We can `time` how long a `zio.Task` or a `zio.RIO` takes to complete.

Using a histogram to `time` a function is pretty much what was shown on the
`Exporters` example above, where `() => Thread.sleep(2000)` is the function we
want to time:

```scala mdoc:silent
  val testHistogramTimer: RIO[Registry, CollectorRegistry] = for {
    h <- histogram.register("simple_histogram_timer", Array("method"))
    _ <- h.time(() => Thread.sleep(2000), Array("post"))
    r <- getCurrentRegistry()
  } yield r
```

You can also configure the [different types of
buckets](https://prometheus.github.io/client_java/io/prometheus/client/Histogram.Builder.html#buckets-double)
, for instance, we can use `Linear Buckets` with the function above:

```scala mdoc:silent
  val testHistogramTimerHelper: RIO[Registry, CollectorRegistry] = for {
    h <- Histogram("simple_histogram_time_custom", Array("method"), LinearBuckets(1, 2, 5))
    _ <- h.time(() => Thread.sleep(2000), Array("post"))
    r <- getCurrentRegistry()
  } yield r
```

You can, of course, verify the usual way:
```scala mdoc:silent
  val setHT: util.Set[String] = new util.HashSet[String]()
  setHT.add("simple_histogram_timer_count")
  setHT.add("simple_histogram_timer_sum")

  val rht   = rt.unsafeRun(testHistogramTimer)
  val cnt   = rht.filteredMetricFamilySamples(setHT).nextElement().samples.get(0).value
  val sum   = rht.filteredMetricFamilySamples(setHT).nextElement().samples.get(1).value
```

or simpler using our `exporters` helper:
```scala mdoc:silent
  val rhtE = rt.unsafeRun(testHistogramTimerHelper)
  write004(rhtE)
```

If, instead, we want to measure arbitrary values:
```scala mdoc:silent
  val testHistogram: RIO[Registry, CollectorRegistry] = for {
    h <- histogram.register("simple_histogram", Array("method"))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.observe(_, Array("get")))
    r <- getCurrentRegistry()
  } yield r
```

`RIO.foreach` will take each value of the list `List(10.5, 25.0, 50.7, 57.3,
19.8)` and apply the function `h.observe(_, Array("get"))` to each
value in a synchronous manner, where `_` refers to the value (10.5, 25.0,
etc.) and `Array("get")` is the specific label for the current observation.

We can override the `DefaultBuckets` so:

```scala mdoc:silent
  val testHistogramBuckets: RIO[Registry, CollectorRegistry] = for {
    h <- histogram.register("simple_histogram", Array("method"), DefaultBuckets(Seq(10, 20, 30, 40, 50)))
    _ <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(h.observe(_, Array("get")))
    r <- getCurrentRegistry()
  } yield r
```

To use an arbitrary timer, after registering the histogram, we need to
start the timer using our recently created `histogram` object with the
`startTimer` method which returns a `timer` object. We will use this `timer` to
mark every point we want observed (measure) thus giving us the duration between
when `startTimer` was called and each time we `observeDuration`. 
 
```scala mdoc:silent
  val f = (n: Long) => {
    RIO.sleep(Duration.fromScala(n.millis)) *> putStrLn(s"n = $n")
  }

  val testHistogramDuration: RIO[Registry with Console with Clock, CollectorRegistry] = for {
    h <- Histogram("duration_histogram", Array("method"), ExponentialBuckets(0.25, 2, 5))
    t <- h.startTimer(Array("time"))
    dl <- RIO.foreach(List(75L, 750L, 2000L))(
           n =>
             for {
               _ <- f(n)
               d <- h.observeDuration(t)
             } yield d
         )
    _ <- RIO.foreach(dl)(d => putStrLn(d.toString()))
    r <- getCurrentRegistry()
  } yield r
```

We could define `ExponentialBuckets` substituting the following line:

`h  <- histogram.register("duration_histogram", Array("method"), ExponentialBuckets(0.25,2,5))`


Finally, we have two variations of `time`, one that returns the duration and the
result of executing the `Task` or `RIO` and another (`time_`) that only executes the
`Task` and returns the result, but not the duration. The benefit of `time_` is
that it uses `ZIO.bracket` underneath to have stronger guarantees on the
acquisition, use, and release of the timer and the task execution.

```scala mdoc:silent
  import zio.Task
  
  val testHistogramTask: RIO[Registry, (CollectorRegistry, Double, String)] = for {
    h     <- Histogram("task_histogram_timer", Array.empty[String], DefaultBuckets(Seq.empty[Double]))
    (d,s) <- h.time(Task{Thread.sleep(2000); "Success"})
    r     <- getCurrentRegistry()
  } yield (r, d, s)

  val testHistogramTask2: RIO[Registry, (CollectorRegistry, String)] = for {
    h <- histogram.register("task_histogram_timer_")
    a <- h.time_(Task{Thread.sleep(2000); "Success"})
    r <- getCurrentRegistry()
  } yield (r, a)
```

Now lets inspect our values by `tap`ping our `RIO`. Add ` with Clock.Live`
 to the runtime `rt` before executing the following code.

```scala mdoc:silent
  rt.unsafeRun(testHistogramDuration.tap(r => write004(r).map(println)))
```

Please note there's no reason to use `tap` here, its just to demonstrate that we
are returning `RIO`s which means we have at our disposal all its combinators. We
might as well just use

```
  val rhd = rt.unsafeRun(testHistogramDuration)
  write004(rhd)
```

instead or any other way we've seen of observing our `CollectorRegistry`.

## Summary
`Summary` works exactly as `Histogram` above except a `Summary` also allows to
pass the list of percentiles we wish to include in our measures: `quantiles:
List[(Percentile, Tolerance)]` where `Percentile` is a `Double` value between 0
and 1 that represents the percentile (i.e. 0.5 = median) and `Tolerance` is a
`Double` value (also between 0 and 1) that represents the tolerated error. Refer
to the [Prometheus
Percentile](https://prometheus.io/docs/practices/histograms/#quantiles)
documentation for more information.

```scala mdoc:silent
  val testSummary: RIO[Registry, CollectorRegistry] = for {
    s  <- Summary("simple_summary", Array("method"), List((0.5, 0.05), (0.9, 0.01)))
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(s.observe(_, Array("put")))
    r  <- getCurrentRegistry()
  } yield r
```

Just like `Histogram` it has methods `time` and `time_` that take a `Task` or
`RIO` as input.

```scala mdoc:silent
  val testSummaryTask: RIO[Registry, CollectorRegistry] = for {
    s <- summary.register("task_summary_timer")
    _ <- s.time(Task(Thread.sleep(2000)))
    r <- getCurrentRegistry()
  } yield r

  val testSummaryTask2: RIO[Registry, CollectorRegistry] = for {
    s <- summary.register("task_summary_timer_")
    _ <- s.time_(Task(Thread.sleep(2000)))
    r <- getCurrentRegistry()
  } yield r
```

## Dependency Injection with ZLayers
A common principle in functional programming is to execute the effects `at the
end of the world` in your application. This basically means that you will write
pure functions by wrapping your effectful code in ZIO and you execute them from
your top-most functions, i.e. in `main` or as close as possible to `main`.
Unfortunately, this has the effect (pun intended) of creating a tree-like
structure of nested functions.

Let's say, for instance, you have a `Router` which calls a `Service` which
calls 2 different functions, `MeasuringPoint1` and `MeasuringPoint2` where you
need to count something. If you `register` the counter in `MyRoutes` then you
need to pass the counter downstream first to `MyService1` and then to the MeasuringPoint
functions. 

```
MyRoutes
  -> MyService1(counter)
    -> MeasuringPoint1(counter)
    -> MeasuringPoint2(counter)
```

The deeper such functions are, the more cumbersome this becomes. You could
reduce the number of times you pass the counter by registering it immediately
above where they're needed (in `MyService1`) but on one hand that means you will
probably end up registering your metrics all over your code (instead of at a
central location) and on the other it will not work if you also need the counter
on, say, a `MyService2` service.

One way to deal with this is using `ZLayer` to make your environment `R` carry
your metrics for you. I'll present two ways to achieve this, each with its own
advantages and drawbacks. The first is to create your own `Metrics` layer where
you will register the exact metrics you need as private values and then you
simply expose methods to use them in your `MeasuringPoints` through the Layer:

```scala
  type Env = Registry with Exporters with Console
  val rtLayer = Runtime.unsafeFromLayer(Registry.live ++ Exporters.live ++ Console.live)

  type Metrics = Has[Metrics.Service]

  object Metrics {
    trait Service {
      def getRegistry(): Task[CollectorRegistry]

      def inc(tags: Array[String]): Task[Unit]

      def inc(amount: Double, tags: Array[String]): Task[Unit]

      def time(f: () => Unit, tags: Array[String]): Task[Double]

    }

    val live: Layer[Nothing, Metrics] = ZLayer.succeed(new Service {

      private val (myCounter, myHistogram) = rtLayer.unsafeRun(
        for {
          c <- counter.register("myCounter", Array("name", "method"))
          h <- histogram.register("myHistogram", Array("name", "method"))
        } yield (c, h)
      )

      def getRegistry(): Task[CollectorRegistry] =
        getCurrentRegistry().provideLayer(Registry.live)

      def inc(tags: Array[String]): zio.Task[Unit] =
        inc(1.0, tags)

      def inc(amount: Double, tags: Array[String]): Task[Unit] =
        myCounter.inc(amount, tags)

      def time(f: () => Unit, tags: Array[String]): Task[Double] =
        myHistogram.time(f, tags)
    })

  }
```

And then we can use it so:

```scala
  val exporterTest: RIO[
    Metrics with Exporters with Console,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[Metrics]
      _  <- putStrLn("Exporters")
      r  <- m.get.getRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      _  <- m.get.inc(Array("RequestCounter", "get"))
      _  <- m.get.inc(Array("RequestCounter", "post"))
      _  <- m.get.inc(2.0, Array("LoginCounter", "login"))
      _  <- m.get.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- putStrLn(s)
    } yield hs

  val programL = exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rtLayer.unsafeRun(programL.provideSomeLayer[Env](Metrics.live))
```

The key of this approach is that even if you register only one counter, you can
in practice have as many counters (or any other metric) as you need by
distinguishing them using the `name` tag. You can have as many different
counters as combination of tags you can devise for your app. Your
`MeasuringPoints` can be as nested as they need to be, you just need to declare
`Metrics` as part of your environment in the function that will actually get to
use it.

The main drawback, as far as I can tell, is that you need that extra call to
`unsafeRun` in order to extract and use the metrics themselves. If you don't,
then every call to `inc` or `time` would attemp to re-register the metric which
results in an Exception. This is because things wrapped up in `ZIO`s are
descriptions of a program so flatmapping or folding on them causes them to
start their execution flow from the beginning.

Although the ideal is to call `unsafeRun` only once in your App, this is only an
ideal and calling it a second (or even third) time is OK as long as you do not
abuse its usage.

You can, however, eliminate this extra `unsafeRun` by registering your `counter`
and `histogram` during startup and pass them as inputs to your `ZLayer` using
`fromFunction`:

```scala
    val receiver: ZLayer[(Counter, Histogram), Nothing, Metrics] = ZLayer.fromFunction[(Counter, Histogram), Metrics.Service]( minsts => new Service {

      def getRegistry(): Task[CollectorRegistry] =
        getCurrentRegistry().provideLayer(Registry.live)

      def inc(tags: Array[String]): zio.Task[Unit] =
        inc(1.0, tags)

      def inc(amount: Double, tags: Array[String]): Task[Unit] =
        minsts._1.inc(amount, tags)

      def time(f: () => Unit, tags: Array[String]): Task[Double] =
        minsts._2.time(f, tags)
    })
```

The second approach is somewhat more generic and doesn't need extra calls to
`unsafeRun` but it requires the use of `PartialFunction`s and keeping a private
`Map` inside our custom Layer, here called `MetricsMap`:

```scala
  type MetricMap = Has[MetricMap.Service]

  case class InvalidMetric(msg: String) extends Exception

  object MetricMap {
    trait Service {
      def getRegistry(): Task[CollectorRegistry]

      def put(name: String, metric: Metric): Task[Unit]

      def getHistogram(name: String): IO[InvalidMetric, Histogram]

      def getCounter(name: String): IO[InvalidMetric, Counter]
    }

    val live: Layer[Nothing, MetricMap] = ZLayer.succeed(new Service {

      private var metricsMap: Map[String, Metric] = Map.empty[String, Metric]

      def getRegistry(): Task[CollectorRegistry] =
        getCurrentRegistry().provideLayer(Registry.live)

      def put(key: String, metric: Metric): Task[Unit] =
        Task(
          this.metricsMap =
            if (metricsMap.contains(key))
              metricsMap.updated(key, metric)
            else
              metricsMap + (key -> metric)
        ).unit

      def getHistogram(name: String): IO[InvalidMetric, Histogram] =
        metricsMap(name) match {
          case h @ Histogram(_) => IO.succeed(h)
          case _                => IO.fail(InvalidMetric("Metric is not a Histogram or doesn't exists!"))
        }

      def getCounter(name: String): IO[InvalidMetric, Counter] =
        metricsMap(name) match {
          case c @ Counter(_) => IO.succeed(c)
          case _              => IO.fail(InvalidMetric("Metric is not a Counter or doesn't exists!"))
        }
    })
  }
  ```

Simply put: the `MetricsMap` layer exposes methods to `get` and `put` metrics
inside its private `Map`, overwriting the existing metric if the `key` is
already registered. You only write to this `Map` when registering a metric NOT
when actually using it, so the requirement for safe-concurrency are quite low.

This technique aloows you to register all your metrics in one place:

```scala
  val startup: RIO[
    MetricMap with Registry,
    Unit
  ] =
    for {
      m     <- RIO.environment[MetricMap]
      name  = "ExportersTest"
      c     <- Counter(name, Array("exporter"))
      hname = "export_histogram"
      h <- histogram
            .register(hname, Array("exporter", "method"))
            .provideLayer(Registry.live)
      _ <- m.get.put(name, c)
      _ <- m.get.put(hname, h)
    } yield ()
```

and then usinig them downstream wherever you need:

```scala
  val rtMM = Runtime.unsafeFromLayer(MetricMap.live ++ Registry.live ++ Exporters.live ++ Console.live)

  val exporterTest: RIO[
    MetricMap with Exporters with Console,
    HTTPServer
  ] =
    for {
      m  <- RIO.environment[MetricMap]
      _  <- putStrLn("Exporters")
      r  <- m.get.getRegistry()
      _  <- initializeDefaultExports(r)
      hs <- http(r, 9090)
      c  <- m.get.getCounter("ExportersTest")
      _  <- c.inc(Array("counter"))
      _  <- c.inc(2.0, Array("counter"))
      h  <- m.get.getHistogram("export_histogram")
      _  <- h.time(() => Thread.sleep(2000), Array("histogram", "get"))
      s  <- write004(r)
      _  <- putStrLn(s)
    } yield hs

  val programMM = startup *> exporterTest >>= (server => putStrLn(s"Server port: ${server.getPort()}"))

  def main(args: Array[String]): Unit =
    rt.unsafeRunMM(programMM)
```
