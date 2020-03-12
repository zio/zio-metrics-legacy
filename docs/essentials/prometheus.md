---
id: essentials_prometheus
title:  "Prometheus ZIO Wrapper"
---

ZIO Metrics Prometheus provides Prometheus' 4 metrics plus a number of
exporters all connected through the `CollectorRegstry`.

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
// and for inspecting primetheus
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
defined on `typeclasses` with instances for String and Class[A].
Besides the `register*` functions, we also have `getCurrent()` that simply
returns the `CollectorRegistry` which is needed by all `Exporters`.

Using the registry helper the above function becomes:

```scala mdoc:silent
  val testRegistryHelper: RIO[Registry, CollectorRegistry] = for {
    _  <- registerCounter("simple_counter", Array("method"))
    r  <- getCurrentRegistry()
  } yield r
```

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
    c <- counter.register("PrometheusTestHelper, Array("method", "resource")")
```

From here on. we will use environmental efects, helper methods or a mix of both.

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
etc.) and `Array("get")` is the specic label for the current observation.

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
aquisition, use and release of the timer and the task execution.

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

instead or any other way we've seen of observing our `CollectoRegistry`.

## Summary
`Sumamry` works exactly as `Histogram` above except a `Summary` also allows to
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

