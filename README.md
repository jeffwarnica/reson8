# reson8

reson8 is - or will be - a Kubernetes auralizer. In short, this will allow real time monitoring of a Kubernetes cluster
through background sound, the idea being a human operator will develop "ambient awareness" what is normal, and understand the state 
of their cluster without action. 

This idea is directly inspired by the USNIX paper [Peep (The Network Auralizer): Monitoring Your Network With Sound
](https://www.usenix.org/legacy/publications/library/proceedings/lisa2000/full_papers/gilfix/gilfix_html/index.html)

This may or may not be useful in practice. But it will look, or rather, *sound* cool in a keynote. 

## Soundscape

Peep inspired the basic concept.

    Sound representation in Peep is divided into three basic categories: *Events* in networks are things that occur once, naturally represented by a single peep or chirp. Network *states* represent ongoing events by changing the type, volume, or stereo position of an ongoing background sound while *heartbeats* represent the existence or frequency of occurrence of an ongoing network state by playing a sound at varying intervals, such as by changing the frequency of cricket chirps.

Considering a k8s cluster, with Prometheus's backing, we have gauges, counters (which is to say, events), and histograms. Imagining the sounds of a forest, gauges (cpu, memory) map nicely to something like wind or waves crashing, (which are generally "always on") and events (as counters tick up) maps to something like a one off bird chirping (infrequent, on demand). Its less obvious how to map histogram data, but (per Peep), this could map to the frequency (rhythm) or tone (pitch) of always happening cricket chirps.

Actual sound design, very much a TODO.

## Architecture

The sound engine leverages GStreamer through the [gst1-java-core](https://github.com/gstreamer-java/gst1-java-core) bindings, wrapped behind a `GsToolkit` abstraction so production code and unit tests never call native GStreamer APIs directly. The engine models a physical mixer console: input channels feed into `GsMixer`, which outputs a single HTTP `audio/wav` stream for browser clients.

Above the mixer sits a signal pipeline that connects cluster telemetry to audio:

```
K8s / Thanos / Prometheus
        │  metrics & events
        ▼
  SignalManager  ◄── application.yml (signal-map)
        │
        ▼
  SignalBucket  (curve mapping: raw metric → 0–100 intensity)
        │
        ▼
  InputChannel  (one of: WindGaugeChannel, LoopingGaugeChannel,
                          StochasticGaugeChannel, GsDropChannel)
        │
        ▼
    GsMixer  ──► HTTP audio/wav stream  ──► browser
```

`GstChannelFactory` selects the correct `InputChannel` implementation based on the sound type declared in `application.yml` (`PROCEDURAL`, `LOOP`, `STOCHASTIC`, `DROP`). `SignalCurveMap` shapes the raw metric value before it reaches the channel so the audio response is perceptually calibrated.

This is a Quarkus project and heavily leverages its CDI, REST, and testing framework features. The `@IfBuildProperty(name="reson8dev.audiopath")` mechanism selects between the real GStreamer stack (`gs`) and a fully silent stub (`silent`) used in unit tests.

## Status

The core sound engine and signal pipeline are feature-complete for a proof-of-concept. The following are working end-to-end:

- **GsMixer** — GStreamer pipeline with per-channel faders, master volume, VU metering, and clean disposal on Quarkus reload.
- **Four input channel types** — procedural wind noise (`WindGaugeChannel`), looping WAV playback (`LoopingGaugeChannel`), stochastic one-shot drops (`StochasticGaugeChannel`), and single-play drops (`GsDropChannel`).
- **Signal pipeline** — `SignalManager` + `SignalBucket` route K8s stats, K8s events, and Prometheus queries to the correct channels via configurable `SignalCurveMap` interpolation (linear, monotone hermite, cubic spline).
- **K8s / Thanos integration** — `K8Client` watches node metrics and cluster events; `ThanosMetricPoller` polls Prometheus via Thanos.
- **Configuration engine** — `application.yml` maps arbitrary metric sources to named sounds with per-signal curve definitions. Seven signals are pre-configured (CPU load → babbling brook, memory load → wind, pending pods → crickets, pod lifecycle events → chirps, HTTP 500 errors → alarm crickets).
- **Browser output** — HTTP `audio/wav` stream with fan-out to multiple simultaneous subscribers.
- **REST control surface** — channel faders, master volume, k8s-sync toggle, per-signal metric simulation, and a debug dump endpoint.
- **Test suite** — >80% line coverage with ArchUnit rules, mutation tests (PITest), SpotBugs static analysis, and separate unit / integration test tiers (`GsTestProfile` for native GStreamer ITs, `silent` backend for unit tests).

## Roadmap

### Completed
* ~~Cleanup GStreamer objects on Quarkus reload~~
* ~~Looping "always" sounds from WAVs~~
* ~~Configuration engine — full metric-to-sound mapping, named drops, arbitrary Prometheus queries~~
* ~~k8s engine POC — CPU, memory, pod start / stop / crash~~
* ~~Histogram → stochastic channel type~~

### Remaining / next steps
* Better sounding generated procedural audio (wind algorithm improvements — current output is recognisable but rough)
* Proper histogram metric handling: map histogram bucket rates to stochastic channel frequency / rhythm
* Horizontal scalability: the GStreamer pipeline is inherently single-replica; document or address this constraint before any production deployment

## Security Posture (POC — intentionally open)

All REST endpoints are currently **unauthenticated**. This is intentional for the POC phase to simplify local development and cluster deployment. The following mutation endpoints are the highest-priority surfaces to gate before any production or multi-tenant deployment:

| Endpoint | Method | Risk |
|---|---|---|
| `POST /audio/control/fader` | Mutation | Sets per-channel fader / ceiling |
| `POST /audio/control/k8s-sync/{active}` | Mutation | Enables/disables k8s metric ingestion |
| `POST /audio/control/simulate-metric` | Mutation | Injects arbitrary signal values |
| `POST /audio/drop` | Mutation | Triggers one-shot audio drops |
| `PUT /audio/control/master-volume` | Mutation | Changes master volume |

**Before production:** add JWT (`quarkus-smallrye-jwt`) or OIDC (`quarkus-oidc`) protection to these endpoints, or place the service behind an OpenShift Route with OAuth proxy sidecar.

### Wishlist
* Consider k8s influenced channels configured by name
    * e.g. CRD annotations `reson8.io/monitor: true` + `reson8.io/channel: ireallycareaboutthistoday`
* Native executable (GraalVM) — currently blocked by GStreamer JNI bindings

# Quarkus Notes

Generic Quarkus notes follow. They may or may not still be valid.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/reson8-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
