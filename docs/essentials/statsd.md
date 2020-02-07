---
id: essentials_statsd
title:  "StatsD/DogStatsD Pure ZIO Client"
---

ZIO Metrics StatsD/DogStatsD ZIO client is based on the `Censorinus` client, but
uses ZIO's concurrency toolbox such as `Queue`, `Stream`, `Schedule` as well as
[ZIO-NIO](https://zio.github.io/zio-nio/) for UDP channels.

Its main components, besides the `Metrics` themselves are the `Encoder` module
with instances for StatsD and DogStatsD and the `Client` itself, which is not a
module but has separate instances for StatsD and DogStatsD.

# Metrics
## StatsD Metrics

We're gonna need the following import:

```scala mdoc:silent
  import zio.metrics._
```

Although each metric can be created directly, th preferred way is to di it
through the `StatsDClient` which has helper methods for all metrics.

### Gauge
A gauge is a value calculated by the client, the value is simply sent to
StatsD for reporting without any processing involved. You can create it directly so:

```scala mdoc:silent
  val gauge = Gauge("name", 34.0, Seq.empty[Tag])
```

Note that:
1. the second argument is a `Double` and
2. StatsD does NOT support tags but DogStatsD does and since the
   same `Gauge` class is used for both then setting a value for `tags` has no
   effect whatsoever, its just ignored by `StatsDEncoder`.

If we use the client instead we save ourselves issues as with `tags` above:

```scala mdoc:silent
  import zio.metrics.statsd._
  import zio.Queue
  
  val uioQueue = Queue.bounded[Metric](100) // details on Client Section
  val client = StatsDClient()
  uioQueue >>= (q => 
    client.gauge("name", 34.0)(q)
  )
```

Also note, this methods are asynchronous by default, we'll talk more about that
later.

### Counter
A count is a gauge calculated at the server, that is, you can increment or
decrement a value by one or, alternatively, pass the value that should be
increases/decreased. It may also have a `sampleRate` which is represented as a
Double between 0 and 1, as such, a sampleRate of `0.5` means that roughly 50% of
the values will be actually sent the StatsD server. A sampleRate of 1.0 (or
above) ensures that all values are sent.

```scala mdoc:silent
  uioQueue >>= (queue => {
    implicit val q = queue // make it implicit so we don't pass it everytime
    client.counter("counterName", 3.0) // no sample rate
    client .counter("counterName", 2.0, 0.75) // 75% sample rate
    client.increment("counterName")
    client.decrement("counterName", 0.9) // 90% sample rate
  })
```

### Timers
The number of milliseconds between a start and end time.

```scala mdoc:silent
  import java.util.concurrent.TimeUnit
  import zio.clock.Clock
  import zio.duration.Duration
  
  for {
      q  <- uioQueue
      t1 <- Clock.Live.clock.currentTime(TimeUnit.MILLISECONDS)
      _  <- Clock.Live.clock.sleep(Duration(75, TimeUnit.MILLISECONDS))
      t2 <- Clock.Live.clock.currentTime(TimeUnit.MILLISECONDS)
      _  <- client.timer("zmetrics.timer", (t2 - t1).toDouble, 0.9)(q)
  } yield ()
```

### Meter
Measures the rate of events over time, calculated at the server.

```scala mdoc:silent
  for {
    q  <- uioQueue
    _  <- client.meter("zmetrics.meter", 2.5)(q)
  } yield ()
```

### Set
Counts the number of unique occurrences of events over a period of time.


```scala mdoc:silent
  for {
    q  <- uioQueue
    _  <- client.set("zmetrics.meter", "ocurrence")(q)
  } yield ()
```

## DogStatsD Metrics
As stated earlier, StatsD shares its `Metrics` type with DogStatsD. Besides
supporting `Tag`s for every type, DogStatsD also adds a `Histogram` metric and an
`Event` and `ServiceCheck` types.

### Histogram
Allows you to measure the statistical distribution of a set of values.

```scala mdoc:silent
  import zio.metrics.dogstatsd._
  
  val dogClient = DogStatsDClient()
  for {
    q  <- uioQueue
    _  <- dogClient.histogram(
        "zmetrics.hist", 
        2.5, // value
        0.9, // sample rate
        Seq(Tag("key1","val1"), Tag("key2","val2"))
        )(q)
  } yield ()
```

### ServiceCheck
Allow you to characterize the status of a service in order to monitor it within Datadog.

```scala mdoc:silent
  for {
    q  <- uioQueue
    _  <- dogClient.serviceCheck(
        "zmetrics.checks", 
        ServiceCheckOk,
        None, // defaults to current time
        None, // optional hostname
        Some("Check custom message"),
        Seq(Tag("key1","val1")),
        true // use synchronous version
        )(q) // don't forget the queue
  } yield ()
```

### Event
