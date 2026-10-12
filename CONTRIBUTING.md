# Contributing

Read [PLAN.md](PLAN.md), [PROGRESS.md](PROGRESS.md) and the relevant contract guide in `docs/`
before changing behavior. OpenCC `ver.1.4.2` is the pinned reference. Preserve its operations,
features, candidate order and observable results using Kotlin Multiplatform types and ownership.
Keep native pointer/allocation details out of client APIs.

Use a focused change and commit. Explain the problem, the resulting behavior, and concrete
validation. Update the progress log when completing a milestone or identifying an acceptance gap.
Do not mark a feature complete based on compilation alone or silently broaden a size allowance.

## Development checks

JDK 17+, Python 3.12+, and the checked-in Gradle wrapper are required. Native differential tests
also require CMake and a C++ compiler. Android needs SDK 36; Apple execution needs Xcode 26.x.

| Change | Relevant validation |
| --- | --- |
| Common behavior | Affected module's JVM, JS, Wasm and available Native tests |
| Upstream behavior | Corresponding `upstream*Conformance` task and regression cases against the pinned oracle |
| Public signatures | `./gradlew checkKotlinAbi`; review an intentional baseline update across all targets |
| Data selection | `python3 tools/test_data.py`; publication and consumer resource-closure checks |
| Dependency/publication | `python3 tools/test_publication_notices.py`, local publication, `tools/verify_publications.py` and independent consumers |
| CLI/filesystem | `tools/test_cli.py`, unpacked distributions and affected host execution |
| Delivered bytes | Production consumer measurements with `--check`; preserve checked-in budgets |
| Workflows | `python3 tools/lint_workflows.py` |

For example, `./gradlew -Popencc.targets=jvm :opencc-core:jvmTest` is a quick development check.
All-target ABI validation and cross-host CI are separate gates. The published consumer build is
independent of repository projects; it resolves `.cache/maven`. See
[publication verification](docs/PUBLICATION.md) and [CI scope](docs/CI.md).

## Dependency and ownership boundaries

Keep algorithms in commonMain, platform access in thin adapters, and builders/CLI/data in their
explicit modules. Core depends only on Kotlin stdlib. Browser networking belongs to the host adapter;
conversion is synchronous after resource preparation. Loaded models are immutable, conversion state
belongs to each call, and caches have explicit identity and lifetime.

Retain upstream/third-party attribution for adapted code. Add licenses to the module-specific
publication payload when a new adaptation requires them. Inspect the actual distributed archives.

Upstream upgrades are deliberate compatibility changes: update the release tag, exact commit and
archive SHA-256; regenerate the inventory; rerun formats/configs/tools and behavioral matrices; review
API/size changes; document any changed contracts. Kotlin release versions follow semantic versioning,
while the upstream identity is tracked separately in provenance and release notes.
