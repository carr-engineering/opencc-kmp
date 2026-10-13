# Performance acceptance

`benchmarks/` is a standalone consumer of the published core/platform artifacts using
[kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark/tree/v0.4.17) and JVM JMH.
It introduces no dependencies into any published library. The loaded 1,000-character, exactly
1 MiB UTF-8 corpus, and initialization-plus-conversion workloads use s2t, t2s and s2twp.
Initialization measures config parsing, file reads and dictionary restoration with potentially warm
OS caches; it must not be described as a cold device launch. Results are average milliseconds per
operation, not p95 latencies.

```sh
./gradlew -Popencc.targets=jvm publishAllPublicationsToConsumerRepository
python3 tools/run_benchmarks.py
# Short execution check, including full-output comparison against native:
python3 tools/run_benchmarks.py --profile smoke
```

The `smoke` profile verifies harness execution with shorter runs. Release comparisons use `main`,
retain the JSON under `.cache/benchmarks/<run>/`, and record the host, JDK, upstream
identity, artifact identity, benchmark version and command. Shared CI-host timing is trend evidence,
not proof of a physical-device target.

The first full `main` run is retained in
[jvm-performance.json](../measurements/jvm-performance.json), with five one-second warmups and ten
one-second measurement iterations per workload, exact resolved dependency hashes and complete native
output checks. On the recorded macOS ARM64/JDK 24 host:

| Config | Loaded 1,000 chars, mean ms | Initialize + 1,000 chars, mean ms | JVM 1 MiB, mean ms | Native 1 MiB, mean ms |
| --- | ---: | ---: | ---: | ---: |
| s2t | 0.053 | 17.940 | 23.153 | 6.028 |
| t2s | 0.043 | 0.984 | 18.409 | 1.631 |
| s2twp | 0.148 | 18.468 | 72.487 | 15.675 |

Native uses 30 in-process samples after five warmups on the same exact 1 MiB UTF-8 input. Its column
includes conversion/output allocation, without configuration loading. The JVM is slower on these
throughput workloads; neither column is a universal performance guarantee. JVM resource loading may
use warm OS file caches, and these averages do not establish Android or browser p95.

The production browser/Swift fixtures report startup and conversion timings alongside size in
`tools/measure_web.py` and `tools/measure_apple.py`. Browser JS heap samples exclude process and Wasm
linear-memory peaks; simulator process RSS includes runtime and system overhead.

`benchmarks/android/` provides an isolated R8 release APK that consumes the published core/platform
artifacts and the selected s2t pack. Prepare its native reference, build it, then explicitly select
the phone to measure:

```sh
python3 tools/prepare_android_benchmark.py
ANDROID_HOME=/path/to/android-sdk ./gradlew -p benchmarks/android assembleRelease
python3 tools/run_android_benchmark.py --sdk /path/to/android-sdk --serial DEVICE_SERIAL
```

The runner requires at least 30 fresh application processes. Each performs 100 warmups and 300
timed conversions, checking the complete native output after each timed interval. Reports include
nearest-rank p50/p95, first resource load and conversion, activity creation time, APK/reference hashes,
device/build identity, battery/thermal conditions, process VmHWM and heap snapshots. Corpus/reference
setup is outside the resource-load interval; provider construction and all dictionary reads are inside.
OS file caches may remain warm. Emulator results carry a separate scope and cannot establish the
physical-phone target. No physical-device result is recorded by merely compiling this APK.

The independent production Wasm fixture now has
[web-performance.json](../measurements/web-performance.json): 30 fresh Chrome processes on the
recorded ARM64 desktop, with 100 warmups and 300 measured 1,000-character conversions per process.
All 9,000 outputs match the native reference. Warm p50/p95 are approximately 0.1/0.2 ms, and startup
p95 is 100 ms. The recorded timer step is 0.1 ms; smaller/zero intervals are resolution-limited.
Sampled JS heap reached 54.8 MB and excludes process and Wasm linear-memory peaks.

```sh
python3 tools/run_web_benchmark.py --check-target
```

This checks the 30 ms warm p95 target on the recorded desktop host, retaining every raw sample,
per-browser quantiles, resource preparation/fetch timing, native/reference/distribution hashes and
actual browser identity. It measures localhost gzip delivery and fresh browser profiles; OS file
caches may remain warm. CI retains the same report separately from size accounting.

Before a release, independently measure at least 30 fresh launches and warmed 1,000-character
conversion samples on a mid-range 2022 Android phone. Report device/OS, release/R8 configuration,
resource pack, p50/p95, process peak memory and thermal conditions; check the PLAN.md 30 ms conversion
and 120 ms initialization targets. JVM throughput and desktop-browser latency evidence above do not
establish those phone targets. No simulator, smoke-profile or average-time result substitutes for
the physical-device measurements or complete peak-memory acceptance.
