# Blackbox ![Build Status](https://img.shields.io/badge/build-passing-brightgreen?style=for-the-badge) ![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge) ![Platform](https://img.shields.io/badge/platform-hytale-orange?style=for-the-badge)

---

## **The Flight Recorder for Hytale dedicated servers.**
Things rarely break when you are staring at the console; they break at 3 AM when you are asleep. Blackbox is an always on incident recorder designed to solve the ambiguity of **"it just crashed."**

When your server stutters, stalls, or terminates, or otherwise has a seizure, Blackbox ensures you have a clean bundle of state to analyze; it eliminates the need to attempt to reproduce the impossible.

It is not a dashboard; it is not a flamegraph viewer; it is, regrettably, not a box of chicken nuggets. It is a black box.

### But why?

Most observability tools excel at answering "what is slow right now?". Production environments, however, rarely cooperate with live profiling sessions. Blackbox addresses the other common administrative scenario: "I have no idea what happened, and it fixed itself."

Interactive profilers cannot rewind time; Blackbox can. It records state continuously, quietly, and with minimal overhead, ensuring that when an incident ends, the investigation can begin.

### The Incident Report

The output is simple. You get:

* **The Archive**: A single zip file per incident; easy to archive, easy to transfer.
* **The Summary**: A generated `report.html` designed for human readability without requiring port binding or web panels.
* **The Source**: The raw JFR (Java Flight Recorder) recording for granular analysis.

### How The Sausage Is Made (The Architecture)

Blackbox maintains a rolling JVM recording in the background. It utilizes internal JVM mechanisms to ensure low overhead without requiring external agents.

When a trigger fires (such as a heartbeat stall or a manual invocation), Blackbox dumps the buffer to disk, generates the summary, and packages the artifacts. The design goal are post mortems that are automatic and boring; boring is good.

## Installation

1. Place the Blackbox jar into the dedicated server `mods/` directory.
2. Start the server.
3. Upon failure, retrieve the latest archive from the `incidents/` directory.

Blackbox is designed to be a permanent resident in your production environment; it is safe to leave installed.

### How to Use the Analysis?

Investigation follows two distinct paths, depending on the required depth.

#### The Quick Read
Unzip the bundle and view `report.html`. This document summarizes the state of the server at the time of the crash; it is designed to be legible to tired system administrators.

#### The Deep Dive
Open `recording.jfr` in **JDK Mission Control**. This allows for inspection of the rolling history prior to the event: CPU usage, memory allocations, GC pauses, lock contention, and thread timelines.

## Privacy & Security

The default behavior is strictly local.

* Data never leaves the machine automatically.
* There are no third party service hooks.
* Discord integration is optional; it sends only a status alert, never the bundle itself.
* Optional and disabled by default.

If you intend to share a bundle publicly, treat it with the same caution as a heap dump.

## Performance

Blackbox adheres to a strict "do no harm" policy.

* **Thread Safety**: World threads are sacred; no blocking I/O occurs on critical ticks.
* **Isolation**: Capture work is offloaded to Blackbox owned executors.
* **Bounded Resources**: Disk usage is strictly capped by retention policies (count, age, total bytes).
* **Graceful Failure**: Incident capture is best effort; server stability always takes precedence over reporting.

## Relationship to other tools

Blackbox is not a replacement for interactive profilers (yet). Retain your existing toolkit for live investigation; use Blackbox for the incidents you missed.

## Development

To build the project locally, ensure you have JDK 21 installed.

```bash
./gradlew build
```

You can also produce a jar with:

```bash
./gradlew jar
```