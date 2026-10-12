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

Text and upstream `.ocd` / `.ocd2` dictionary resources can be supplied through `ResourceProvider`.
Format writers and final platform resource packaging are still in progress. [API mappings](docs/API.md) distinguish implemented
capabilities from remaining parity work. `upstreamConformance` builds the native reference using
CMake and checks all upstream answer pairs, golden corpora and all 16 configurations using text
dictionaries and original `.ocd2` dictionaries. It also verifies all 22 binary dictionaries in both
formats. Common unit tests do not need CMake or upstream downloads.

## Data selection and platform resources

Build-time generation requires Python 3.12+, CMake and a C++ compiler for the pinned reference tools:

```sh
./gradlew -Popencc.targets=jvm :opencc-data:generateData
./gradlew -Popencc.targets=jvm -Popencc.configs=s2t,t2s :opencc-data:bundleSelectedData
./gradlew -Popencc.targets=jvm :opencc-data:verifyDataBundles
```

The full resource directory is `opencc-data/build/generated/resources/opencc`; its deterministic
archive is `opencc-data/build/bundles/opencc-data.zip`. Selected resources and their archive are
under `opencc-data/build/selected`. Manifest entries record sizes, SHA-256 hashes, the upstream pin
and the complete config dependency graph. Selection includes optional dictionaries and deduplicates
shared resources. The zip is kept outside runtime resource folders to avoid shipping duplicate data.

JVM consumers of `opencc-data` can use `ClasspathResourceProvider`. Other hosts can copy selected
resources into Android assets or Apple bundles and use the corresponding provider, or supply a
`FileResourceProvider`/custom provider. Gradle automation for final mobile/browser application
packaging and published consumer fixtures remains in progress.

The full data distribution includes experimental Jieba assets. The Kotlin Jieba segmentation port
is still pending; adding its resources alone does not enable those configurations.

## Provenance

Upstream source is fetched only for build-time data generation and conformance. It is never linked
into the Kotlin runtime. The fetcher checks the archive digest before extraction. Apache-2.0 text,
upstream authors and notices are included with redistributed data and derived implementations.
