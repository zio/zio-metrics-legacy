# scalaz-metrics

[![Gitter](https://badges.gitter.im/scalaz/scalaz-metrics.svg)](https://gitter.im/scalaz/scalaz-metrics?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Goal
A high-performance, purely-functional library for adding instrumentation to any application, with a simple web client and JMX support.

## Introduction and Highlights
Scalaz Metric is a principled functional programming library to measure the behavior of your application. It focuses on provideing metrics and health checks in a performant purely functional manner.

* Comprehensive and consistent
* Type safe, purely-functional
* Pluggable effect monads
* Asynchronous and non-blocking
* Supports streaming
* Can be used in Scala REPLs

If it can affect your code's business value, measure it. -- Coda Hale

In order to know how well our *code* is generating *business value*, we need *metrics* -- also Coda Hale


## Competitors
[Metrics Scala](https://github.com/erikvanoosten/metrics-scala)  
Type safe, pure FP: ✘  
Comprehensive: ✔  
Scala: ✔  
Notes:  
  
[Scala Metrics](https://github.com/PagerDuty/scala-metrics)  
Type safe, pure FP: ✘  
Comprehensive: ✔  
Scala: ✔  
Notes:  

[Kamon](http://kamon.io/documentation/1.x/core/basics/metrics/)  
Type safe, pure FP: ✘  
Comprehensive: ✔  
Scala: ✔  
Notes:  

[JAJMX](https://github.com/dacr/jajmx)  
Type safe, pure FP: ✘ (but very FP oriented)  
Comprehensive: ✔  
Scala: ✔  
Notes:  
  
[SSE-JMX](https://github.com/sptz45/sse-jmx)  
Type safe, pure FP: ✘  
Comprehensive: ✔  
Scala: ✔  
Notes:  
  
[Metrics Datadog](https://github.com/coursera/metrics-datadog)  
Type safe, pure FP: ✘  
Comprehensive: ✔  
Scala: ✘ (Java)  
Notes:  
  
[ekg: Remote monitoring of processes](https://hackage.haskell.org/package/ekg)  
Type safe, pure FP: ✔  
Comprehensive: ✔  
Scala: ✘ (Haskell)  
Notes: [Monad Metrics](https://github.com/parsonsmatt/monad-metrics/blob/17546b92b4e7e94279b81afe76fd6daa5f3ff0f8/src/Control/Monad/Metrics/Internal.hs) a wrapper and API for using EKG metrics  

[prometheus: Prometheus Haskell Client](https://hackage.haskell.org/package/prometheus)  
Type safe, pure FP: ✔  
Comprehensive: ✔  
Scala: ✘ (Haskell)  
Notes:  

[Metrics](https://github.com/codahale/metrics)
Type safe, pure FP: ✘  
Comprehensive: ✔  
Scala: ✘ (Go)  
Notes: [Haskell implementation](https://hackage.haskell.org/package/metrics), [Metrics, Metrics, Everywhere - Coda Hale](https://www.youtube.com/watch?v=czes-oa0yik)  


## Background
[Optimizing Tagless Final – Saying farewell to Free](https://typelevel.org/blog/2017/12/27/optimizing-final-tagless.html)  
  
[Tagless Final algebras and Streaming](https://typelevel.org/blog/2018/05/09/tagless-final-streaming.html)  

## Notes
[Dropwizard and Cats Gist](https://gist.github.com/Daenyth/7795133b3471da32d3121fcf30994484)  
