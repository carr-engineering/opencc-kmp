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
- Source hash, API audit, implementation and validation are in progress.

## Completion policy

Do not describe the port as complete until every required configuration, mapped API operation,
format/tool and target has conformance evidence. Record failures and unsupported environment checks
explicitly. Publication requires configured ownership, signing credentials and release infrastructure;
local preparation is not evidence of an actual public release.
