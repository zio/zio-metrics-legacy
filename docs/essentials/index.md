---
id: essentials_index
title:  "Overview"
---

ZIO-Metrics is a thin wrapper over both Prometheus and Dropwizard intrumentation
libraries allowing you to measure the behaviour of your application in a
performant purely functional manner.

`If it can affect your code's business value, measure it.` -- Coda Hale

`In order to know how well our *code* is generating *business value*, we need *metrics*` -- also Coda Hale


ZIO-Metrics follows [the module pattern in
ZIO](https://zio.dev/docs/howto/howto_use_module_pattern) to provide modules for Prometheus and Dropwizard metrics.

 - **[Prometheus](prometheus.md)** — Prometheus ZIO Wrapper.
 - **[Dropwizard](dropwizard.md)** — Dropwizard ZIO Wrapper.

## Installation

`ZIO-Metrics` is available via Maven, thus an import in `build.sbt` is enough:

```scala
// Prometheus
libraryDependencies += "dev.zio" %% "zio-metrics-prometheus" % "0.0.9"

// Dropwizard
libraryDependencies += "dev.zio" %% "zio-metrics-dropwizard" % "0.0.9"
```

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Prometheus Java Client](https://github.com/prometheus/client_java)
 - [Dropwizard Metrics Core](https://metrics.dropwizard.io/4.0.0/manual/core.html)
