---
id: essentials_statsd
title:  "StatsD/DogStatsD Pure ZIO Client"
---

ZIO Metrics StatsD/DogStatsD ZIO client is based on the `Censorinus` client, but
uses ZIO's concurrency toolbox such as `Queue`, `Stream`, `Schedule` as well as
[ZIO-NIO](https://zio.github.io/zio-nio/) for UDP channels.

Its main components, besides the `Metrics` themselves are the `Encoder` module
with instances for StatsD and DogStatsD and the `Client` itself, which is not a
module but is used for separate instances for StatsD and DogStatsD.

# Metrics
## StatsD Metrics

We're gonna need the following import:

```scala mdoc:silent
  import zio.metrics._
```

Although each metric can be created directly, the preferred way is to do it
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
  import zio.metrics.Client.ClientEnv
  import zio.ZManaged
  
  val statsDClient: ZManaged[ClientEnv, Throwable, StatsDClient] = StatsDClient()
  statsDClient.use { client => 
    client.gauge("name", 34.0)
  }
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
  statsDClient.use { client => 
    client.counter("counterName", 3.0)       // no sample rate
    client.counter("counterName", 2.0, 0.75) // 75% sample rate
    client.increment("counterName")
    client.decrement("counterName", 0.9)     // 90% sample rate
  }
```

### Timers
The number of milliseconds between a start and end time.

```scala mdoc:silent
  import java.util.concurrent.TimeUnit
  import zio.RIO
  import zio.Clock
  import zio.Duration
  
  statsDClient.use { client => 
    for {
      clock <- RIO.environment[Clock]
      t1    <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _     <- clock.get.sleep(Duration(75, TimeUnit.MILLISECONDS))
      t2    <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _     <- client.timer("zmetrics.timer", (t2 - t1).toDouble, 0.9)
    } yield ()
  }
```

### Meter
Measures the rate of events over time, calculated at the server.

```scala mdoc:silent
  statsDClient.use { client => 
    client.meter("zmetrics.meter", 2.5)
  }
```

### Set
Counts the number of unique occurrences of events over a period of time.

```scala mdoc:silent
  statsDClient.use { client => 
    client.set("zmetrics.meter", "ocurrence")
  }
```

## DogStatsD Metrics
As stated earlier, StatsD shares its `Metrics` type with DogStatsD. Besides
supporting `Tag`s for every type, DogStatsD also adds a `Histogram` metric and an
`Event` and `ServiceCheck` types.

### Histogram
Allows you to measure the statistical distribution of a set of values.

```scala mdoc:silent
  import zio.metrics.dogstatsd._
  
  val dogStatsDClient = DogStatsDClient()
  dogStatsDClient.use { dogClient =>
    dogClient.histogram(
        "zmetrics.hist", 
        2.5,                        // value
        0.9,                        // sample rate
        Seq(Tag("key1","val1"), Tag("key2","val2"))
    )
  }
```

### ServiceCheck
Allow you to characterize the status of a service in order to monitor it within Datadog.

```scala mdoc:silent
  dogStatsDClient.use { dogClient =>
    dogClient.serviceCheck(
      "zmetrics.checks", 
      ServiceCheckOk,
      None,                       // defaults to current time
      None,                       // optional hostname
      Some("Check custom message"),
      Seq(Tag("key1","val1")),
      true                        // use synchronous version
    )
  }
```

### Event
Events are limited to 4000 characters. If an event is sent out with a message
containing more than 4000 characters only the 4000 first characters are
displayed.

```scala mdoc:silent
  val tagEnv = Tag("env", "prod")
  val tagVersion = Tag("version", "0.1.0")
  dogStatsDClient.use { dogClient =>
    dogClient.event(
      "zmetrics.dog.event",         // name
      "something amazing happened", // event text/message
      None,                         // timestamp, encoder defaults to 'now'
      Some("hostname"),
      Some("aggregationKey"),       //  to group events with same key
      Some(EventPriorityLow),       // 'None' defaults to 'normal'
      Some("sourceType"),
      Some(EventAlertError),        // 'None' defaults to 'info'
      Seq(tagEnv, tagVersion),
      false                         // use asynchronous version
    )
  }
```


# Client

The different reporting clients (`StatsDClient` and `DogStatsDClient`)
use a `zio.metrics.Client` which is a simple UDP client.
Each of them takes a `Client` as a parameter but
also provides constructors with default values for each such that:

```scala mdoc:silent
  Client() == Client(5, 5000L, 100, None, None, None)
```

The `Client` constructors return a `ZManaged`. You can create your own specific client
by reusing the default client constructors like this:

```scala mdoc:silent
  Client().map(client => new StatsDClient(client))
```

or you can just use one of the custom constructors that wrap this process:

```scala mdoc:silent
  StatsDClient() == StatsDClient(5, 5000L, 100, None, None, None)
```

The first two parameters (`bufferSize` and `timeout`) define how to aggregate each
batch of metrics sent to the StatsD server. The third parameters (`queueCapacity`)
determines the size of the internal queue used to batch said metrics. Before
going into detail let's see how a `Client` looks like. **NOTE** however that
this is a base client, ZIO-Metrics-StatsD also provide `StatsD` and a
`DogStatsD` specialized clients.

```scala mdoc:silent
  import zio.{ RIO, Runtime, Task }
  import zio.Clock
  import zio.Console
  import zio.Console.printLine
  import zio.Chunk
  import zio.metrics.encoders._

  // Provide a runtime
  val rt = Runtime.unsafeFromLayer(Encoder.statsd ++ Console.live ++ Clock.live)
  
  val program = {
    val messages = Chunk(1.0, 2.2, 3.4, 4.6, 5.1, 6.0, 7.9)
    val createClient = Client()
    createClient.use { client =>
      for {
        opt <- RIO.foreach(messages)(d => Task(Counter("clientbar", d, 1.0, Seq.empty[Tag])))
        _   <- RIO.collectAll(opt.map(m => client.sendM(true)(m)))
      } yield ()
    }
  }
```

Since the client was created with default values, the internal queue is a [back-pressured bounded
queue](https://zio.dev/docs/datatypes/datatypes_queue) with a maximum capacity of 100 items. 
This means that any fiber that offers items when the queue is
full, will be suspended until the queue is able to add the item.

## Batching
The next relevant bit how the underlying client works. The `Client` will batch 
items based on the constructor values for `bufferSize` and `timeout`. Essentially, items in the
queue will be processed once `bufferSize` is reached or the `timeout` (in
milliseconds) expires, whichever happens first. So for the default values of 5
and 5000, items in the queue will be processed either when 5 items are collected
or after 5 seconds have passed.

The default constructors will use the default message processor which will sample metrics
according to their defined `sampleRate` (Counter, Timer and Histogram only),
then it encodes each Metric according to the provided `Encoder` and then sends
them to the specified UDP StatsD server. In this case, the default uses
`localhost` at port `8125`.

## Synchronous vs Asynchronous Delivery
The next line in the sample client creates 7 `Counter`s with a `sampleRate` of
`1.0`, ensuring every metric will be encoded and sent to the StatsD server. The
last line in the sample client is the one that actually `sends/offer` the metric
to the queue. There are two variations of this method `send: Queue[Metric] =>
Metric => Task[Unit]` and `sendAsync: Queue[Metric] => Metric => Task[Unit] `.
The asynchronous version, [forks a
fiber](https://zio.dev/docs/datatypes/datatypes_fiber) and returns immediately.
The synchronous one offers the metric in the same fiber the rest of the program
has running, while this operation is normally almost as fast as the
asynchronous, issues may arise in case of a full queue since the fiber will
block until there is space to add more items to the queue. In such a case, the
asynchronous version comes in handy since the main execution of our `program`
will not block, the risk for this scenario is that we keep creating and
suspending fibers which might become a memory leak.

We can now run our sample client so:

```scala mdoc:silent
  def main(args: Array[String]): Unit = {
    rt.unsafeRun(program >>= (lst => printLine(s"Main: $lst").provideSomeLayer(Console.live)))
  }
```

## Custom Message Processor
As explained earlier, the sample client uses the default message processor which
samples the metrics, encodes them and sends them to StatsD using `UDPClient`.
Here is a similar processor that performs NO sampling, encodes the message,
prints the encoded message to console and then uses the default host and port
from `UDPClient`.

```scala mdoc:silent
  val myudp: Chunk[Metric] => RIO[Encoder with Console, Chunk[Int]] = msgs =>
    for {
      sde <- RIO.environment[Encoder]
      opt <- RIO.foreach(msgs)(sde.get.encode(_))
      _   <- printLine(s"udp: $opt")
      l   <- RIO.foreach(opt.collect { case Some(msg) => msg })(s => UDPClient().use(_.send(s)))
    } yield l
```

and we can use this instead of the default behavior by using the `withListener` constructor:

```scala mdoc:silent
  val messages = Chunk(1.0, 2.2, 3.4, 4.6, 5.1, 6.0, 7.9)
  val createCustomClient = Client.withListener { l =>
    myudp(l).provideSomeLayer[Encoder](Console.live)
  }
  createCustomClient.use { client =>
    for {
      opt <- RIO.foreach(messages)(d => Task(Counter("clientbar", d, 1.0, Seq.empty[Tag])))
      _   <- RIO.collectAll(opt.map(m => client.sendM(true)(m)))
    } yield ()
  }
```

A message processor is defined as: `Chunk[Metric] => RIO[Encoder, F[A]]`, since
`myudp` requires `Encoder with Console` which is NOT the same type as just
`Encoder`, we need to prove to the compiler our encoding capabilities using `provideSome`.

## StatsD Client
Most of the client's functionality is provided by `Client`, you won't normally use
it though since you can use `StatsDClient` instead which has methods to help
create and offer/send metrics to the queue. Here's a sample of how it's used.

```scala mdoc:silent
  import zio.Schedule
  
  val schd = Schedule.recurs(10)    // optional, used to send more samples to StatsD
  val createStatsDClient = StatsDClient()       // StatsD default client

  def program(r: Long)(statsDClient: StatsDClient) =
    for {
      clock <- RIO.environment[Clock]
      t1 <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _  <- statsDClient.increment("zmetrics.counter", 0.9)
      _  <- printLine(s"waiting for $r ms") *> clock.get.sleep(Duration(r, TimeUnit.MILLISECONDS))
      t2 <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _  <- statsDClient.timer("zmetrics.timer", (t2 - t1).toDouble, 0.9)
    } yield ()
```

We can reuse `rt`, the runtime created earlier to run our `program`:

```scala mdoc:silent
  def main1(args: Array[String]): Unit = {
    val timeouts = Seq(34L, 76L, 52L)
    rt.unsafeRun(
      createStatsDClient.use { statsDClient =>
        RIO
          .foreach(timeouts)(l => program(l)(statsDClient))
          .repeat(schd)
      }
    )
    Thread.sleep(10000)             // wait for all messages to be consumed
  }
```

## DogStatsD Client
Just like the `StatsDClient`, except that the `DogStatsDClient` has methods to
help you create and send histograms, service checks and events which are not
supported by StatsD. 

```scala mdoc:silent
  val createDogStatsDClient = DogStatsDClient()

  def dogProgram(r: Long)(dogStatsDClient: DogStatsDClient) =
    for {
      clock <- RIO.environment[Clock]
      t1 <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      _  <- dogStatsDClient.increment("zmetrics.dog.counter", 0.9)
      _  <- printLine(s"waiting for $r ms") *> clock.get.sleep(Duration(r, TimeUnit.MILLISECONDS))
      t2 <- clock.get.currentTime(TimeUnit.MILLISECONDS)
      d  = (t2 - t1).toDouble
      _  <- dogStatsDClient.timer("zmetrics.dog.timer", d, 0.9)
      _  <- dogStatsDClient.histogram("zmetrics.dog.hist", d)
      _  <- dogStatsDClient.serviceCheck("zmetrics.dog.check", ServiceCheckOk)
      _  <- dogStatsDClient.event("zmetrics.dog.event", "something amazing happened")
    } yield ()
```

Since we need a different `Encoder` for DogStatsD than for StatsD, we'll have to
create a new runtime to support it.

```scala mdoc:silent
  val rtDog = Runtime.unsafeFromLayer(Encoder.dogstatsd ++ Console.live ++ Clock.live)
  
  def main2(args: Array[String]): Unit = {
    val timeouts = Seq(34L, 76L, 52L)
    rtDog.unsafeRun(
      createDogStatsDClient.use { dogStatsDClient =>
        RIO
          .foreach(timeouts)(l => dogProgram(l)(dogStatsDClient))
          .repeat(schd)
      }
    )
    Thread.sleep(10000)
  }

```
