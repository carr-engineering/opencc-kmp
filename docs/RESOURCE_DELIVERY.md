# Resources and independent consumers

Core has no dependency on platform I/O, data or tooling. Data modules contain binary resources and
metadata only, and no longer pull in the engine. Runtime dictionaries retain their immutable values;
resource acquisition finishes before synchronous conversion starts.

| Host | Provider | Published resource delivery |
| --- | --- | --- |
| JVM | ClasspathResourceProvider or FileResourceProvider | Data target JARs contain `opencc/` resources; ZIP staging is also supported |
| Android | AndroidAssetResourceProvider | Unpack the published resources ZIP into generated assets with AGP's variant source API |
| Apple | AppleBundleResourceProvider or FileResourceProvider | Unpack into an app or `.bundle` resource directory; pass its NSBundle |
| Browser JS/Wasm | BrowserResourceProvider | Host serves selected binary files; `prepareConfig` suspends during fetch, then returns a synchronous converter |
| Node JS/Wasm | NodeFileResourceProvider | Host stages ZIP contents; builtin filesystem reads require Node's `process.getBuiltinModule` (the pinned test runtime supports it) |

The browser adapter uses fetch in the platform module. It adds no network dependency to core.
Node adapters do not import a Node package into browser module graphs. Hosts can still supply their
own sync/suspend providers and resource verification/download policy.

`opencc-data` remains the complete offline distribution. Selected pack names encode their sorted
config list, e.g. `opencc-data-s2t` and `opencc-data-s2t-t2s`. Each includes the complete selected
resource closure, optional dictionaries and notices, with shared resources deduplicated. Configure
pack generation with a quoted semicolon-separated property:

```sh
./gradlew '-Popencc.packs=s2t;t2s;s2t,t2s' -Popencc.targets=jvm \
  :opencc-data-s2t:publishAllPublicationsToConsumerRepository
```

Each KMP root publication attaches a deterministic `resources` ZIP classifier. A Gradle resource
configuration can select `io.github.opencckmp:opencc-data-s2t:0.1.0-SNAPSHOT:resources@zip` with
`isTransitive = false`; JVM clients may instead select its ordinary KMP/JVM dependency. Binary files
are never emitted as Kotlin string literals or entry objects. A tiny internal identity declaration
ensures data-only KMP variants have valid klibs; it contains only the upstream version.

`consumers/` is a separate Gradle build. It uses Maven coordinates from `.cache/maven`, never project
substitution. The Android app uses generated assets and default R8 release shrinking with no OpenCC
keep rules. The iOS simulator fixture loads a real Foundation bundle through the published adapter.
JS/Wasm tests run in Node and headless Chrome and perform actual async HTTP acquisition. The runner
asserts HTTP requests completed, including typed missing-resource failures. Simulator environment
variables use the required `SIMCTL_CHILD_` forwarding.

Publish core/platform/data locally before running independent tests:

```sh
./gradlew -Popencc.targets=jvm,android,iosArm64,iosSimulatorArm64,js,wasmJs \
  :opencc-core:publishAllPublicationsToConsumerRepository \
  :opencc-platform:publishAllPublicationsToConsumerRepository \
  :opencc-data-s2t:publishAllPublicationsToConsumerRepository
python3 tools/test_consumers.py
python3 tools/test_consumers.py :kit:jsBrowserTest :kit:wasmJsBrowserTest
```

These are local Maven publications, not a Maven Central release. Production JS/Wasm distributions
and installed Swift apps now exercise all five deployment profiles; detailed evidence is in
[WEB_AND_APPLE_MEASUREMENTS.md](WEB_AND_APPLE_MEASUREMENTS.md). Android runtime/device checks,
physical-device performance and full memory/CI gates remain acceptance work. Packaged resources
do not prove the still-pending Jieba algorithm's conformance.
