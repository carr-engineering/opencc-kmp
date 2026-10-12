# CI scope and release gates

`.github/workflows/ci.yml` runs on pull requests, main pushes, version tags and manual dispatch.
Actions use immutable commit pins and read-only repository permissions; Dependabot proposes updates.
The checksum-pinned actionlint script checks the workflow. Python syntax and Git whitespace checks
run alongside behavior tests. Kotlin formatting follows `.editorconfig` and the Kotlin style setting;
there is not yet a Kotlin formatter gate.

| Job | Coverage |
| --- | --- |
| Linux | JVM/Linux Native/JS/Wasm common tests, all differential matrices, data acceptance, actual published consumers, CLI process comparisons and unpacked JVM/Linux distributions |
| Android | Owned API 35 x86_64 emulator executes all four R8 release consumer profiles, checks real inline/asset conversion assertions and records APK hashes/device logs |
| Windows | JVM/Windows Native tests, all three release executable links, Unicode-file conversion/in-place output and binary writer smoke checks |
| Apple | All configured target compilation/ABI checks, Dokka, JVM/macOS/iOS simulator tests, all local publication variants, artifact checks and independent Android/iOS/JS/Wasm/JVM consumers |
| Size | Attributed publications, R8 APK and dependency measurements, production webpack/DCE/Wasm and browser execution, Swift release-app/simulator measurements; unchanged checked-in budgets |

Each size gate runs even when another gate fails. Reports are uploaded on failure, and no workflow
updates baselines automatically. Byte-preserving AAR compression brings all Android application and
dependency-download profiles within their unchanged budgets; see SIZE_BASELINES.md. The Apple size
gate explicitly uses the measured `smallBinary` release option with the pinned toolchain; all five
profiles pass after the compact decimal parser change. General library defaults remain unchanged.
CI must expose unresolved gates. A passing APK budget does not override a failing dependency-download budget.

Apple jobs select the newest installed Xcode 26.x, record available runtimes, and use ARM64 runners
with SDK 36. Simulator results are not physical-device startup/memory or App Store delivery evidence.
Windows smoke checks are not the POSIX native-oracle process matrix; broader Windows oracle and
filesystem checks remain acceptance work. Cross-compilation does not prove execution on another host.

The workflow has been linted and its task graphs checked locally. This checkout has no configured
Git remote, so hosted CI has not run. Repository branch protection/required checks must be configured
by its owner after the workflow is pushed. The checked-in definitions are not successful remote runs.

## Public release checklist

Before a version tag can publish a public release, complete the remaining PLAN.md acceptance work,
resolve measured budgets, establish physical-device performance evidence and audit every upstream
operation/platform export. Configure the owner/repository URL, registry namespace, private reporting
contacts, signing credentials and release infrastructure. Decide the initial stable API and record
its semantic version independently from upstream OpenCC's version.

Tag publication is implemented in `.github/workflows/release.yml` and remains disabled until the owner
sets `OPENCC_PUBLIC_RELEASE_ENABLED=true` after complete acceptance review. It runs after Linux,
Windows, Apple, Android and size jobs pass. Exact-version staged artifacts and consumers are checked
before the protected `public-release` environment signs and submits the Central bundle. GitHub CLI
assets are released only after Central confirms publication. See [PUBLICATION.md](PUBLICATION.md) for
owner variables, secrets, approval and recovery procedures. Neither local task success nor a checked-in
workflow proves successful hosted execution or public publication.

Android emulator coverage exercises release shrinking and Android asset loading on a real Android
runtime. The runner owns its temporary AVD and emulator process; it does not stop existing devices.
The checked-in runner has not been executed locally because this checkout has no installed emulator.
It does not establish mid-range physical-device startup, throughput or peak-memory targets.

The Apple job also builds `interop/` against published engine artifacts, checks its committed ABI,
validates generated TypeScript, and executes direct Swift/JavaScript consumers of the optimized exports.
These checks cover binary resource injection and host-language error/stream contracts; they do not
replace the complete Kotlin API inventory. Export-library byte measurements are reported separately
from the engine application's checked-in budgets.
