# Implementation progress

The acceptance requirements live in [PLAN.md](PLAN.md). This file records verified progress; a
checkbox is completed only when its implementation and relevant checks exist.

## Milestones

- [ ] 1. KMP modules/targets, pinned upstream identity, API inventory, resource delivery, consumer fixtures.
- [ ] 2. Text dictionaries, config parsing, segmentation, normalization, conversion and API contracts.
- [ ] 3. Interoperable `.ocd` / `.ocd2` readers and writers, legacy layouts and byte orders.
- [ ] 4. Reproducible complete/selective data, manifests, closure, deduplication and measured budgets.
- [ ] 5. Facades, streaming, inspection, tooling, phrase extraction, CLI and differential tests.
- [ ] 6. Segment mapping and optional detection.
- [ ] 7. Benchmarks, consumer shrinking, documentation, CI and release readiness.

## Execution log

### Foundation

- Started from a directory containing only PLAN.md; initialized Git for focused milestone commits.
- Resolved OpenCC `ver.1.4.2` to commit `025f371dc76b598d77384fbdab90c937471844d8`.
- Recorded source archive SHA-256 and a verification/fetch script in `upstream.lock.json` and
  `tools/upstream.py`; checksum verification passed.
- Added the eight planned modules, configurable KMP target graph, pinned Kotlin 2.4.0 / Gradle
  8.14.3 wrapper (with distribution checksum), Apache-2.0 attribution and initial README.
- Generated `docs/upstream-inventory.json` with configuration resource references and header digests.
- Validation: JVM module graph and CLI entry-point compilation passed. Core tests report NO-SOURCE
  at this scaffold stage; this is not engine conformance evidence. Other targets remain unverified.
- The full API mapping, platform delivery, consumer fixtures and size baselines remain in progress.

## Completion policy

Do not describe the port as complete until every required configuration, mapped API operation,
format/tool and target has conformance evidence. Record failures and unsupported environment checks
explicitly. Publication requires configured ownership, signing credentials and release infrastructure;
local preparation is not evidence of an actual public release.
