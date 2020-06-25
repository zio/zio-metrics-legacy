---
id: essentials_index
title:  "Overview"
---

ZIO-Metrics is  both: 
1. a thin wrapper over both Prometheus and Dropwizard intrumentation
libraries allowing you to measure the behaviour of your application in a
performant purely functional manner.
2. a pure ZIO StatsD/DogStatsD client heavily inspired on [Censorinus](https://github.com/gphat/censorinus)

`If it can affect your code's business value, measure it.` -- Coda Hale

`In order to know how well our *code* is generating *business value*, we need *metrics*` -- also Coda Hale


ZIO-Metrics follows [the module pattern in
ZIO](https://zio.dev/docs/howto/howto_use_layers) to provide modules for
Prometheus and Dropwizard metrics as well as modules for StatsD and DogStatsdD
Encoders and Clients.

 - **[Prometheus](prometheus.md)** — Prometheus ZIO Wrapper.
 - **[Dropwizard](dropwizard.md)** — Dropwizard ZIO Wrapper.
 - **[StatsD](statsd.md)** — StatsD/DogStatsD Pure ZIO client.

## Installation

`ZIO-Metrics` is available via Maven, thus an import in `build.sbt` is enough:

```scala
// Prometheus
libraryDependencies += "dev.zio" %% "zio-metrics-prometheus" % "0.2.7"

// Dropwizard
libraryDependencies += "dev.zio" %% "zio-metrics-dropwizard" % "0.2.7"

// StatsD/DogStatsD
libraryDependencies += "dev.zio" %% "zio-metrics-statsd" % "0.2.7"
```

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Prometheus Java Client](https://github.com/prometheus/client_java)
 - [Dropwizard Metrics Core](https://metrics.dropwizard.io/4.0.0/manual/core.html)
 - [Censorinus](https://github.com/gphat/censorinus)
 - [StatsD Metric Types](https://github.com/statsd/statsd/blob/master/docs/metric_types.md)
 - [DogStatsD Datagram Format](https://docs.datadoghq.com/developers/dogstatsd/datagram_shell)
