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

## Configurable public release staging

The release workflow is implemented but disabled by default. Default development coordinates stay
`io.github.opencckmp:…:0.1.0-SNAPSHOT`; `opencc.group` and `opencc.version` select the owner-approved
namespace and the port's semantic version. They do not change Kotlin package names. The independent
consumer build accepts the same properties and `opencc.repository`, so verification resolves the
exact staged release rather than a previously published snapshot.

Release staging is an opt-in local Maven repository; it never uploads by itself:

```sh
./gradlew -Popencc.release=true -Popencc.group=YOUR_VERIFIED_GROUP -Popencc.version=0.1.0 \
  -Popencc.repositoryUrl=https://github.com/YOUR_OWNER/YOUR_REPOSITORY \
  -Popencc.developerId=YOUR_OWNER -Popencc.developerName='YOUR_NAME' \
  publishAllPublicationsToReleaseStagingRepository
python3 tools/verify_publications.py --repository .cache/release/maven \
  --group YOUR_VERIFIED_GROUP --version 0.1.0 \
  --targets jvm,android,js,wasmJs,iosArm64,iosSimulatorArm64,iosX64,macosArm64,macosX64,linuxX64,mingwX64
```

Every staged publication includes sources, a documentation JAR containing generated Dokka pages and
contract guides, public source/SCM metadata, developer identity and its release tag. GPG signing occurs
after attribution embedding and artifact verification. `tools/release.py prepare` verifies the entire
KMP graph, POM requirements and final detached signatures, generates MD5/SHA-1/SHA-256/SHA-512 checksums,
and assembles a deterministic Maven-layout bundle. `verify-bundle` checks members/checksums; `upload`
also checks each cryptographic signature before sending the bundle. Registry uploads only occur via
the explicit `upload` command. The implementation follows the official [Central bundle requirements](https://central.sonatype.org/publish/requirements/)
and [Publisher API](https://central.sonatype.org/publish/publish-portal-api/).

## Owner setup and release authorization

Complete PLAN.md's remaining API, export, conformance and performance acceptance before enabling
publication. Passing automated jobs alone does not establish unmeasured physical-device targets.
Create a protected GitHub environment named `public-release` with required reviewers and tag-only
access. Configure these repository variables:

| Variable | Value |
| --- | --- |
| `OPENCC_GROUP` | Maven Central verified namespace owned by the publisher |
| `OPENCC_REPOSITORY_URL` | Public `https://github.com/OWNER/REPOSITORY` source URL |
| `OPENCC_DEVELOPER_ID`, `OPENCC_DEVELOPER_NAME` | Real publisher identity for POM metadata |
| `OPENCC_SIGNING_KEY_ID` | Full fingerprint of the signing key, with its public key distributed |
| `OPENCC_PUBLIC_RELEASE_ENABLED` | `true` only after the complete release acceptance review |

Provide `OPENCC_SIGNING_KEY` (ASCII-armored private key), `OPENCC_SIGNING_PASSWORD`,
`CENTRAL_USERNAME` and `CENTRAL_PASSWORD` (Central Portal token pair) as secrets available to the
protected release job. The keyring is temporary; secrets are passed through environment/stdin rather
than command arguments or logs. Publish the public signing key to the keyserver(s) supported by Central
before attempting release validation. Never commit credentials or private keys.

A `vMAJOR.MINOR.PATCH` tag first runs all conformance, API, consumer, Android runtime and size jobs.
The release workflow then verifies tag/checkout identity, rebuilds every exact-version publication,
runs independent release consumers and builds JVM/Linux/Windows/macOS ARM64/macOS x64 CLI ZIPs.
Its staging payload is uploaded for review before the protected environment permits signing and
publishing. Central must report `PUBLISHED` before GitHub CLI assets are released. Assets carry complete
offline data, attribution, detached signatures, SHA-256 checksums and the public key.

Central upload retries are deliberately not automatic. The deployment ID/state is recorded immediately
and uploaded as evidence, including on failure. If a request timed out or Central is still publishing,
inspect the Portal before re-running: immutable versions cannot be overwritten. If Central succeeds
but GitHub asset creation fails, recover the signed assets from the workflow artifact and create the
GitHub release; do not submit the same Central bundle again. No public release has been performed in
this checkout, and public namespace ownership/credentials remain external prerequisites.

The separate `interop/` build publishes opt-in `opencc-interop` JVM/JS/Apple KMP variants plus production
JavaScript and release Apple XCFramework ZIP classifiers. It consumes the exact staged core version,
embeds the same attribution, and is verified by direct Swift/Node consumers. Its release documentation
JAR includes rendered Dokka HTML plus the facade contract guide and KDoc source. Its resource interface
accepts binary bytes rather than Base64 text. Engine-only consumers do not retain these exports.
See [the focused export guide](../interop/README.md) for supported facade operations and the complete
Kotlin API's optional-extension boundary.

Public releases publish all 23 individual configuration packs plus the `s2t,t2s` shared pack. The release
selector is derived from the locked upstream inventory (`tools/release.py pack-selections`), while
ordinary development retains its two small example selections. Publication inventory verification
requires every individual pack, checks exact config hashes and complete resource dependency closures
(including Jieba's implicit IDF/stop-word resources), rejects unrelated files and duplicates, and verifies
each resource's size/SHA-256. The complete bundle additionally preserves the original raw Jieba models.
The unsigned staged inventory records every artifact SHA-256 and the source commit before approval;
the signing job verifies these hashes again after downloading the stage.
