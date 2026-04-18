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

The sound engine itself leverages GStreamer through the [gst1-java-core](https://github.com/gstreamer-java/gst1-java-core) bindings. We model a physical mixer console, with input channels, some mixing controls, and output channels. A browser output channel ultimately produces a HTTP stream of audio/wav. Input Channels will be the primary interface of the eventual cluster monitoring engine.

This is a Quarkus project, and heavily leverages its features of DI, REST management, tuned testing framework, and, implicitly, the rest of this generations healthy enterprisy Java features.

## Status

Very early days. This is being developed from the sound engine out. The core "mixer" component is at a credible state; it can output to a browser stream, and has some very ugly "gauge" sounds and can play "drops". A very sketchy SPA web front end is available for testing/demo purposes.

There is a credible test suite providing over 80% line coverage, 61% branch coverage. Mixer.java fails us, handling a lot of difficult to trigger exceptions and guard code, with it being the pokey end of our code into gst1-java-core and the GStreamer C libraries itself. The lines/branches being hit by a test may or may not mean its a good test, but at least it doesn't crash.

## Roadmap

In vague order, not quite and both depth and breadth first.
* Cleanup gs objects on Quarkus reload.
* Continue moving out from the sound core, adding    
    * New channel types
        * Better sounding *generated* "always" sounds
        * Looping "always" sounds from WAVs
        * Something to deal with histogram sources
* Dummy event engine
    * Move our sample sounds & drops 
    * Work out abstraction between "metric" and "sound"
    * Random over time simulation
* k8s engine
    * Connect to cluster, setup (canned) channel list 
        * POC: (cpu, mem, pod start, pod stop, pod crash)
    * Loop and feed k8s metrics to generators
* Configuration engine
    * Map feed sources to channel types
    * Named drops (not just file name)
    * Arbitrary configuration items fed to the monitoring loops
    * Demonstrate connecting, via configuration, arbitrary prom metrics
* Come up with some abstractions and names for things

### Wishlist
* Consider k8s influenced channels?
    * Configured by name, but waiting
    * e.g. CRD annotations `reson8.io/monitor: true` + `reson8.io/channel: ireallycareaboutthistoday`

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
