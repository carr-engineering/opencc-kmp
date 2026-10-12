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

Core has no data, networking or tooling dependencies. Consumers can select generated data packs
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
Pure Kotlin format builders/writers are available in `opencc-dictionary-tools`; final consumer
resource packaging is still in progress. [API mappings](docs/API.md) distinguish implemented
capabilities from remaining parity work. `upstreamConformance` builds the native reference using
CMake and checks all upstream answer pairs, golden corpora and all 16 configurations using text
dictionaries and original `.ocd2` dictionaries. It also verifies all 22 binary dictionaries in both
formats. Common unit tests do not need CMake or upstream downloads.

String ranges count UTF-16 units; encoded conversion accepts ByteArray ranges and preserves native
partial/non-canonical byte behavior. `EncodedInputMode` selects explicit lengths or NUL termination,
and `Utf8ConverterStream` supplies encoded streaming. See [text and byte contracts](docs/ENCODED_API.md)
for buffer units, inspection snapshots, NULs, errors and composed deletion maps.
The [configuration contract](docs/CONFIGURATION.md) covers advisory schema warnings, accepted JSON,
ordered extension options and sync/suspend loading with identical validation/resource order.

`opencc-jieba` supplies the seven experimental Jieba configurations through a common Kotlin
segmentation factory; `opencc-all` includes the module. Register it when loading those configs:

```kotlin
import io.github.opencckmp.jieba.JiebaSegmentationFactory

val jieba = SimpleConverter("s2twp_jieba", resources,
    segmentationFactories = mapOf("jieba" to JiebaSegmentationFactory()))
check(jieba.convert("勇敢的士兵") == "勇敢的士兵")
```

The caller supplies `resources` using a bundled/file provider or async preparation. See
[Jieba contracts](docs/JIEBA.md) for merged/text dictionaries, fallback resources and model ownership.

```kotlin
import io.github.opencckmp.tools.*

val dictionary = TextDict.fromText("里\t里 裏\n")
val image = DictionaryWriters.ocd2(dictionary)
check(MarisaDict.fromBytes(image).match("里")?.values == listOf("里", "裏"))
val mapped = converter.convertWithMap("汉字") // half-open UTF-16 input/output ranges
```

The writers reproduce all built-in native images byte for byte. See
[dictionary layouts and interoperability](docs/DICTIONARY_FORMATS.md) for endian/legacy support.
`opencc-phrase-extract` provides the statistical phrase extraction pipeline with Kotlin filters and
typed signals. Opt-in candidate/ambiguity APIs and the optional `opencc-detect` module are described
in [alignment and detection](docs/ALIGNMENT_AND_DETECTION.md). Detection depends on core only; its
dictionaries are selected explicitly.

Run native tooling conformance independently from portable unit tests:

```sh
./gradlew -Popencc.targets=jvm :opencc-dictionary-tools:upstreamWriterConformance
./gradlew -Popencc.targets=jvm :opencc-phrase-extract:upstreamPhraseConformance
./gradlew -Popencc.targets=jvm :opencc-detect:upstreamDataDetection
./gradlew -Popencc.targets=jvm :opencc-jieba:upstreamJiebaConformance
```

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

JVM consumers of `opencc-data` can use `ClasspathResourceProvider`. Published ZIP classifiers can be
staged into Android assets or Apple bundles. Browser/Node JS and Wasm adapters support host resources
and asynchronous preparation. [Resource delivery](docs/RESOURCE_DELIVERY.md) describes the independent
Maven consumer fixtures and the checks already run.

[Initial size baselines](docs/SIZE_BASELINES.md) record actual release APK and dependency-artifact
measurements for engine-only, one-config, multi-config and complete offline deployments, with
regression budgets. [Production web and Swift measurements](docs/WEB_AND_APPLE_MEASUREMENTS.md)
add JS/Wasm distribution sizes, installed iOS simulator checks and Native size-option evidence.

Loaded dictionaries and converters can be shared across threads. Select optional `opencc-cache`
and pass a caller-owned
`DictionaryCache` for repeated config loads, or verified resource providers for host-delivered
bytes. [Cache identity and ownership](docs/CACHES_AND_OWNERSHIP.md) explains initialization,
eviction, close/clear behavior, async preparation and resource verification.
Physical-device performance, complete memory profiling and release gates remain in progress.

The full data distribution includes experimental Jieba assets. The Kotlin Jieba segmentation port
is still pending; adding its resources alone does not enable those configurations.

## Provenance

Upstream source is fetched only for build-time data generation and conformance. It is never linked
into the Kotlin runtime. The fetcher checks the archive digest before extraction. Apache-2.0 text,
upstream authors and notices are included with redistributed data and derived implementations.
