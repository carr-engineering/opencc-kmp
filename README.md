# opencc-kmp

A pure Kotlin Multiplatform port of OpenCC, implementing the requirements in [PLAN.md](PLAN.md).
The reference is OpenCC **1.4.2**, pinned by commit and archive SHA-256 in `upstream.lock.json`.
OpenCC is maintained by Carbo Kuo and contributors and licensed under Apache-2.0.

**Implementation is in progress.** See [PROGRESS.md](PROGRESS.md) for verified capabilities and
remaining milestones. This repository does not yet claim full upstream parity or release readiness.

## Building

Use JDK 17 or newer and the checked-in Gradle wrapper. Targets can be restricted while developing:

```sh
./gradlew -Popencc.targets=jvm :opencc-core:jvmTest
./gradlew -Popencc.targets=jvm,js,wasmJs :opencc-core:allTests
python3 tools/upstream.py fetch
./gradlew -Popencc.targets=jvm :opencc-core:upstreamConformance
```

Without `opencc.targets`, all planned targets are configured. Android builds require Android SDK 36;
Apple executable/framework tests require Xcode. JVM bytecode targets Java 17. Kotlin 2.4.0 and
Gradle 8.14.3 are pinned; macOS x64 is retained despite upstream compiler deprecation.

Core has no data, networking or tooling dependencies. Consumers will be able to select data packs
and optional tooling, or use `opencc-all` for the complete upstream library surface and offline data.
Public APIs use Kotlin text, collection, error and ownership conventions; native pointer/allocation
conventions are mapped to equivalent operations rather than copied.

## Current conversion API

```kotlin
import io.github.opencckmp.*

val converter = Config().newFromString("""
    {"name":"example","conversion_chain":[
      {"dict":{"type":"inline","entries":{"汉字":"漢字"}}}
    ]}
""")
check(converter.convert("汉字") == "漢字")
val trace = converter.inspect("汉字")
```

Text dictionary resources can be supplied through `ResourceProvider`. Binary formats and bundled
resource delivery are still in progress. [API mappings](docs/API.md) distinguish implemented
capabilities from remaining parity work. `upstreamConformance` builds the native reference using
CMake and checks all upstream answer pairs, golden corpora and all 16 configurations using text
dictionaries. Common unit tests do not need CMake or upstream downloads.

## Provenance

Upstream source is fetched only for build-time data generation and conformance. It is never linked
into the Kotlin runtime. The fetcher checks the archive digest before extraction. Apache-2.0 text,
upstream authors and notices are included with redistributed data and derived implementations.
