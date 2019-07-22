# Proceedings for the Design Session for Scalaz Metrics

## Summary
The session began with a quick appraisal of the current [design document](https://github.com/scalaz/scalaz-metrics) with special emphasis on the competitors. In particular, `Metrics Scala`, `Kamon` and `Monad Metrics` were discussed. It was agreed that a `comprehensive` library should be one that has at least the intruments proposed originally by Coda Hale whose library acts as the basis for most metrics related libraries.

Next, John began modelling the main `traits` for scalaz-metrics using definitions and experience from the attendees, he has already shared [the result in a gist](https://gist.github.com/jdegoes/27768b0b03d4246b04d8cc32b6cf30ac).

## Notes on the traits
To understand all instruments be sure to watch the [Metrics, Metrics, Everywhere](https://www.youtube.com/watch?v=czes-oa0yik) talk if you haven't done so.

### Metrics
Metrics takes 3 type parameters:
* `C[_]`: A context for execution. For instance, on `requestTime(doRequest(x, y, z))`, the execution context `C` would be the function call for `doRequest`.
* `F[__]`: Is an effect such as `IO`
* `L`: Is a label which needs not be a string. For instance, it could be some `class` or similar component such that when using a syntax extension (`io.counter(label)`), `L` defines the scope of the counter and any hierarchy can be derived from the class or component's own hierarchy.

`A` in `gauge` is bounded by `Semigroup` so that no matter where its called from on an application, we have the ability to combine the values, if needed.

Similarly, `A` on `histogram` needs to be bounded, at the very least by `Ord` since histogram values need to be ordered into quantiles.

Besides the current specified bounds, `A` may need to be further constrained by what values JMX actually supports.

### Reservoir
Histograms are traditionally built from an entire data set by first sorting the data and then taking the median value. This is not possible for high-throughput, low-latency services so sampling is used instead. This sampling is done via reservoir representative of the whole data stream. The technique is called [reservoir sampling](https://github.com/erikvanoosten/metrics-scala/blob/master/docs/Manual.md#histograms). We must support different `Reservoir` implementations.

### Renderers
We need to be able to transform measured data into different pluggable output formats such as HTML, Json, etc.

## Next Steps

1. Find out what [types work on JMX](https://docs.oracle.com/javase/7/docs/api/javax/management/openmbean/CompositeData.html)
1. Based on the above, specify the appropriate context bounds fro `A`.
1. Implement the traits as a wrapper for some existing API such as Dropwizard or Kamon
1. Raise tickets for the above actions

## Travis
Execute `sbt scalafmtCheck test:scalafmtCheck scalafmtSbtCheck test` to make sure the format is correct.

