---
id: essentials_files
title:  "Prometheus ZIO Wrapper"
---

ZIO Metrics Prometheus provides Prometheus' 5 metrics plus a number of
exporters all connected through the `CollectorRegstyr`.

Required imports for presented snippets:

```scala mdoc:silent
import java.util
import zio.{ RIO, Runtime }
import io.prometheus.client.CollectorRegistry
import zio.internal.PlatformLive
import zio.metrics.{Label => PLabel, Show}
import zio.metrics.prometheus._
import zio.metrics.prometheus.{counter => pcounter}

// and for printing debug messages to the console
import zio.console.{ Console, putStrLn }
```

We will also provide our own `Runtime`:

```scala mdoc:silent
  val rt = Runtime(
    new PrometheusRegistry with PrometheusCounter with PrometheusGauge with PrometheusHistogram with PrometheusSummary
        with PrometheusExporters with Console.Live,
    PlatformLive.Default
  )
```

We include ALL 5 metrics since we will see an example for each one, normally you
just need to include the ones you will actually use.


We will assume the reader knows has working knowledge for Prometheus already, if
not then head over to [Prometheus Java Client](https://github.com/prometheus/client_java).

## Registry
All metrics register themselves with the registry and all exporters need access
to the registry in order to obtain and export its registered metrics.
ZIO-Metrics expose it via [Ref](https://zio.dev/docs/datatypes/datatypes_ref)
providing methods to register every metric type plus one `getCurrent` method to
obtain the current `CollectorRegistry`

You can access the registry module using environmental effects:

```scala mdoc:silent
  val testRegistry: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(PLabel("simple_counter", Array("method")))
    r  <- pr.registry.getCurrent()
  } yield r
```

All `register*` methods require `Label` object (some require some more
parameters). A label is composed of a name and an array of labels which may be
empty in the case of no labels.

```scala mdoc:silent
case class Label[A: Show](name: A, labels: Array[String])
```

Note that zio-metrics does not depend on either cats or scalaz so this `Show` is
defined on
[typeclasses](https://github.com/zio/zio-metrics/blob/master/common/src/main/scala/zio/metrics/typeclasses.scala)
with instances for `String` and `Class[A]`.

Then we also have `getCurrent()` that simply returns the `CollectorRegistry` and
is needed by all `Exporters`.

## Counter
Counter has methods to increase a counter by 1 or by an arbitrary double
passed as a parameter along with optional labels.

```scala mdoc:silent
  val testCounter: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(PLabel("simple_counter", Array.empty[String]))
    _  <- pcounter.inc(c)
    _  <- pcounter.inc(c, 2.0)
    r  <- pr.registry.getCurrent()
  } yield r
```

If the counter is registered with labels, then you need to increase the counter
  passing the same number of labels when registered.
```scala mdoc:silent
  val testLabeledCounter: RIO[PrometheusRegistry with PrometheusCounter, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    c  <- pr.registry.registerCounter(PLabel("simple_counter", Array("method", "resource")))
    _  <- pcounter.inc(c, Array("get", "users"))
    _  <- pcounter.inc(c, 2.0, Array("get", "users"))
    r  <- pr.registry.getCurrent()
  } yield r
```

You can run and verify the results so:
```scala mdoc:silent
  val set: util.Set[String] = new util.HashSet[String]()
  set.add("simple_counter")
  val r = rt.unsafeRun(testCounter)
  val counter = r
    .filteredMetricFamilySamples(set)
    .nextElement()
    .samples
    .get(0)
    .value
  assert(counter == 3.0)
```

There's an easier way to observe the state of the `CollectorRegistry` using the
`write004` Exporter covered later.

Also notice that `counter` here is a [helper method](https://github.com/zio/zio-metrics/blob/master/prometheus/src/main/scala/zio/metrics/prometheus/Helpers.scala) that makes it easier to call
the actual methods from `Counter`. 

## Gauge
Besides increasing, a gauge may also decrease and has methods to `set` and `get`
a values as well as to `setToTime` and `setToCurrentTime`. 

```scala mdoc:silent
  val testGauge: RIO[PrometheusRegistry with PrometheusGauge, (CollectorRegistry, Double)] = for {
    pr <- RIO.environment[PrometheusRegistry]
    r  <- pr.registry.getCurrent()
    g  <- pr.registry.registerGauge(PLabel("simple_gauge", Array.empty[String]))
    _  <- gauge.inc(g)
    _  <- gauge.inc(g, 2.0)
    d  <- gauge.getValue(g)
  } yield (r, d)
```

Here I chose to return both the `CollectorRegistry` and the latest value from
the gauge as a tuple. `gauge` refers to the [helper
method](https://github.com/zio/zio-metrics/blob/master/prometheus/src/main/scala/zio/metrics/prometheus/Helpers.scala).
With labels it looks like this:

```scala mdoc:silent
  val testLabeledGauge: RIO[PrometheusRegistry with PrometheusGauge, (CollectorRegistry, Double)] = for {
    pr <- RIO.environment[PrometheusRegistry]
    r  <- pr.registry.getCurrent()
    g  <- pr.registry.registerGauge(PLabel("simple_gauge", Array("method")))
    _  <- gauge.inc(g, Array("get"))
    _  <- gauge.inc(g, 2.0, Array("get"))
    d  <- gauge.getValue(g, Array("get"))
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
  assert(a1 == 3.0)
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
  val exporterTest: RIO[
    PrometheusRegistry with PrometheusCounter with PrometheusHistogram with PrometheusExporters with Console,
    HTTPServer
  ] =
    for {
      pr <- RIO.environment[PrometheusRegistry]
      r  <- pr.registry.getCurrent()
      _  <- exporters.initializeDefaultExports(r) // initialize HotSpot collectors
      hs <- exporters.http(r, 9090) // http exporter
      c  <- pr.registry.registerCounter(Label(ExportersTest.getClass(), Array("exporter")))
      _  <- counter.inc(c, Array("counter"))
      _  <- counter.inc(c, 2.0, Array("counter"))
      h  <- pr.registry.registerHistogram(Label("export_histogram", Array("exporter", "method")))
      _  <- histogram.time(h, () => Thread.sleep(2000), Array("histogram", "get"))
      s  <- exporters.write004(r) // export as a 004 encoded string
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
Histogram has 3 modes we can use:
1. We can ``time`` how long a task/function takes to complete.
2. We can `observe` arbitrary `Double` values.
3. We can start an arbitrary `timer` and `observe` its duration.

Using a histogram to `time` a function is pretty much what was shown on the
`Exporters` example above, where `() => Thread.sleep(2000)` is the function we
want to time:

```scala mdoc:silent
  val testHistogramTimer: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("simple_histogram_timer", Array("method")))
    _  <- histogram.time(h, () => Thread.sleep(2000), Array("post").reverse )
    r  <- pr.registry.getCurrent()
  } yield r
```

You can, of course, verify the usual way:
```scala mdoc:silent
  val set: util.Set[String] = new util.HashSet[String]()
  set.add("simple_histogram_timer_count")
  set.add("simple_histogram_timer_sum")

  val r     = rt.unsafeRun(testHistogramTimer)
  val count = r.filteredMetricFamilySamples(set).nextElement().samples.get(0).value
  val sum   = r.filteredMetricFamilySamples(set).nextElement().samples.get(1).value
```

or simpler using our `exporters` helper:
```scala mdoc:silent
  val r = rt.unsafeRun(testHistogramTimer)
  exporters.write004(r)
```

If, instead, we want to measure arbitrary values:
```scala mdoc:silent
  val testHistogram: RIO[PrometheusRegistry with PrometheusHistogram, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("simple_histogram", Array("method")))
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(histogram.observe(h, _, Array("get")))
    r  <- pr.registry.getCurrent()
  } yield r

```

`RIO.foreach` will take each value of the list `List(10.5, 25.0, 50.7, 57.3,
19.8)` and apply the function `histogram.observe(h, _, Array("get"))` to each
value in a synchronous manner, where `h` is the histogram we registered as
`simple_histogram` with a `method` label, `_` refers to the value (10.5, 25.0,
etc.) and `Array("get")` is the specif label for the current observation.

Finally, to use an arbitrary timer, after registering the histogram, we need to
start the timer using our recently created `histogram` object with the
`startTimer` method which returns a `timer` object. We will use this `timer` to
mark every point we want observed (measure) thus giving us the duration between
when `startTimer` was called and each time we `observeDuration`. 
 
```scala mdoc:silent
 val f = (n: Long) => {
    RIO.sleep(Duration.fromScala(n.millis)) *> putStrLn(s"n = $n")
  }

  val testHistogramDuration: RIO[PrometheusRegistry with PrometheusHistogram
      with Console with Clock, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    h  <- pr.registry.registerHistogram(Label("duration_histogram", Array("method")))
    t  <- histogram.startTimer(h, Array("time"))
    dl <- RIO.foreach(List(75L, 150L, 200L))(n => for {
            _ <- f(n)
            d <- histogram.observeDuration(t)
          } yield d
          )
    _  <- RIO.foreach(dl)(d => putStrLn(d.toString()))
    r  <- pr.registry.getCurrent()
  } yield r
```

Now lets inspect our values by `tap`ping our `RIO`. Add ` with Clock.Live`
 to the the runtime `rt` before executing the following code.

```scala mdoc:silent
  rt.unsafeRun(testHistogramDuration.tap(r => exporters.write004(r).map(println)))
```

Please note there's no reason to use `tap` here, its just to demonstrate that we
are returning `RIO`s which means we have at our disposal all its combinators. We
might as well just use

```scala mdoc:silent
  val r = rt.unsafeRun(testHistogramTimer)
  exporters.write004(r)
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
  val testSummary: RIO[PrometheusRegistry with PrometheusSummary, CollectorRegistry] = for {
    pr <- RIO.environment[PrometheusRegistry]
    s  <- pr.registry.registerSummary(Label("simple_summary", Array("method")), List((0.5, 0.05), (0.9, 0.01)))
    _  <- RIO.foreach(List(10.5, 25.0, 50.7, 57.3, 19.8))(summary.observe(s, _, Array("put")))
    r  <- pr.registry.getCurrent()
  } yield r
```

