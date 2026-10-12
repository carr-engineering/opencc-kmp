# API baselines and publication verification

The port is not ready for a public release. Its coordinates currently use the placeholder group
`io.github.opencckmp` and version `0.1.0-SNAPSHOT`. A repository owner must establish the release
namespace, source URL, signing and registry credentials before public publication.

## ABI and reference documentation

Kotlin 2.4's integrated ABI validation checks the public JVM and KLIB declarations against the
committed `api/` baselines in each library/tool module. Generated data-pack projects have no public
declarations and are excluded. The complete data and aggregate carrier modules retain empty public
baselines. These carriers generate actual KLIBs so platform dependencies resolve from Maven.

```sh
./gradlew checkKotlinAbi
./gradlew dokkaGenerate
```

Keep all targets configured when refreshing the baseline with `./gradlew updateKotlinAbi`; review
every API difference in Git. An updated baseline records a deliberate API decision, rather than
proving compatibility by itself. Restricted-target development checks are useful, but the complete
target baseline and platform-host checks remain release gates. See the official
[Kotlin ABI validation documentation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html).

Dokka generates combined API documentation in `build/dokka/html`. JVM and Android share their file
adapter through an explicit `jvmAndroidMain` source set, keeping source ownership unambiguous for
documentation and compilation. Generated reference pages do not replace the contract guides and
upstream-operation inventory, which still require a complete audit.

## Shipped licenses and provenance

Publication tasks embed LICENSE, NOTICE, AUTHORS and the locked upstream identity into the actual
JAR/AAR/KLIB and source artifacts. Additional license texts are module-specific: MARISA/RapidJSON in core,
MARISA/Darts in dictionary tools, cppjieba in Jieba, and TCLAP in CLI. KLIB notices are resources
within the component containing its manifest. Resource ZIP classifiers already carry data notices.
Embedding runs after artifact construction and before publication metadata and signing, so registry
checksums describe the delivered bytes. Runtime code does not read this payload.

Android AARs store the inner `classes.jar` entries and compress the outer archive at DEFLATE level 9.
This compresses across class/metadata entries, avoiding ineffective double compression. Class bytes,
Kotlin metadata, resources and notices are preserved; no consumer decoder or runtime code is added.
The larger intermediate JAR affects build-time disk usage, not the APK. Byte audits and the measured
consumer results are recorded in [SIZE_BASELINES.md](SIZE_BASELINES.md).

CLI parser/help behavior is adapted from OpenCC's vendored TCLAP. Its `StdOutput.h` matches the
official TCLAP v1.2.5 source at commit `58c5c8ef24111072fc21fb723f8ab45d23395809` byte for byte;
`licenses/tclap.txt` contains that release's COPYING file, preserving its MIT attribution.

```sh
python3 tools/test_publication_notices.py
./gradlew -Popencc.targets=jvm,android,js,wasmJs,macosArm64,iosSimulatorArm64 publishAllPublicationsToConsumerRepository
python3 tools/verify_publications.py
python3 tools/test_consumers.py --profile full :kit:jvmTest :kit:jsNodeTest :kit:wasmJsNodeTest :kit:iosSimulatorArm64Test
```

The verifier validates Gradle module metadata sizes and hashes against the latest local snapshot,
embedded notices/provenance, the core/optional-runtime dependency boundary, and the complete
aggregate graph. It writes `.cache/publications-verification.json`. Consumer fixtures resolve real
published artifacts from `.cache/maven`, exercising KLIB linking and resource preparation rather
than substituting repository projects. Successful local checks are not evidence of registry upload,
signature verification, Windows/Linux execution or physical-device performance.

The consumer runner owns an ephemeral localhost HTTP port and generates the test-only resource URL.
Manual Gradle test runs retain the `opencc.fixturePort=8765` default; the Python runner supplies its
allocated port automatically. This keeps concurrent fixtures isolated without changing production
application code or resource-delivery contracts.

Neither the engine nor its narrow browser resource adapter uses Kotlin's legacy DOM declarations.
`kotlin.js.stdlib.dom.api.included=false` avoids publishing that automatic dependency. Hosts may add
their chosen browser API library separately; this does not change resource preparation or conversion.
