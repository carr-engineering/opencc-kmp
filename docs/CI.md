# CI scope and release gates

`.github/workflows/ci.yml` runs on pull requests, main pushes, version tags and manual dispatch.
Actions use immutable commit pins and read-only repository permissions; Dependabot proposes updates.
The checksum-pinned actionlint script checks the workflow. Python syntax and Git whitespace checks
run alongside behavior tests. Kotlin formatting follows `.editorconfig` and the Kotlin style setting;
there is not yet a Kotlin formatter gate.

| Job | Coverage |
| --- | --- |
| Linux | JVM/Linux Native/JS/Wasm common tests, all differential matrices, data acceptance, actual published consumers, CLI process comparisons and unpacked JVM/Linux distributions |
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

Current tags trigger verification only. Automatic registry/tag publication remains unconfigured;
local Maven artifacts and executable archives are reviewable preparation. A release workflow must
require the conformance/API/resource/size gates, sign final artifacts after notice embedding, verify
signatures/checksums, and publish every platform variant, selected data pack and complete distribution.
Never report public publication from local task success.
