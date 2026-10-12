# Implementation progress

The acceptance requirements live in [PLAN.md](PLAN.md). This file records verified progress; a
checkbox is completed only when its implementation and relevant checks exist.

## Milestones

- [ ] 1. KMP modules/targets, pinned upstream identity, API inventory, resource delivery, consumer fixtures.
- [ ] 2. Text dictionaries, config parsing, segmentation, normalization, conversion and API contracts.
- [x] 3. Interoperable `.ocd` / `.ocd2` readers and writers, legacy layouts and byte orders.
- [ ] 4. Reproducible complete/selective data, manifests, closure, deduplication and measured budgets.
- [ ] 5. Facades, streaming, inspection, tooling, phrase extraction, CLI and differential tests.
- [x] 6. Segment mapping and optional detection.
- [ ] 7. Benchmarks, consumer shrinking, documentation, CI and release readiness.

## Current acceptance priorities

1. Published-artifact Android/iOS/JS/Wasm consumer fixtures, resource delivery and initial application
   size/download/startup/memory baselines. These remain outstanding despite working JVM resources.
2. Encoded-input/NUL/malformed-input contracts, complete JSON diagnostics and owned concurrent caches.
3. Bundled Jieba segmentation and the three CLI tools, including all pinned options and exit codes.
4. Selective published data packs, CI size budgets/shrinking/performance, API compatibility and release
   infrastructure/documentation. Public publication requires configured ownership and credentials.

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

### Common conversion engine

- Implemented packed text-dictionary prefix lookup, ordered candidate entries, lexicons, both group
  policies, IDS-aware segmentation, per-segment conversion chains, normalization and typed inspection.
- Added stdlib-only config parsing with comments/trailing commas, inline dictionaries, load options,
  provider-relative resources and extension factories. Recursive schema warning parity remains pending.
- Added UTF-16 string ranges, UTF-8 encoded operations, safe buffer output, streaming, Kotlin facades,
  context-scoped errors and a suspend resource-preparation factory.
- Recorded implemented and pending API mappings in `docs/API.md`.
- Validation: initial 9 common behaviour tests passed on JVM, JS/Node, Wasm/Node, macOS Arm64 and
  iOS Arm64 simulator. Facade coverage subsequently expanded JVM tests to 10 passing tests.
- Conformance on JVM with upstream-generated text dictionaries: all 556 upstream answer pairs,
  5 golden corpus pairs, and native-oracle comparisons for all 16 bundled configs passed.
- Built the pinned C++ reference and ran its 21 test executables successfully. The oracle build
  isolates upstream Git-version discovery from this project's history and is never a runtime binding.
- Removed generated Python bytecode from tracking and added source line-ending/cache rules.

### Next acceptance work

- Native binary formats and their builders/writers, followed by complete/selective binary data packs.
- Platform adapters and published consumer fixtures; no binary size budgets have been claimed yet.
- Remaining upstream utility, experimental API, phrase extraction, CLI and Jieba parity; see API.md.

### Binary dictionary readers

- Implemented `.ocd` double-array traversal, BinaryDict payloads, legacy uint64 layouts and both byte
  orders, plus `.ocd2` MARISA LOUDS restoration with nested links, text/binary tails and ordered values.
- Native `.ocd2` dictionaries restore into a packed Kotlin trie; this reader makes no native calls.
  Bundled format builders/writers, further layout fixtures and memory/startup optimization remain pending.
- Validation: all 22 upstream dictionaries in both formats matched 65,175 ordered entries/candidate
  sets and exact/prefix lookups against native-exported text. The 556 answer pairs, five golden corpus
  pairs and 16-config native comparisons also passed using original upstream `.ocd2` inputs.
- Portable fixtures and simulated legacy/endian `.ocd` layouts passed on JVM, JS/Node, Wasm/Node,
  macOS Arm64 and iOS Arm64 simulator: 13 common tests per target (10 engine/facade, 3 binary).
- Disabled Wasm incremental compilation after a Kotlin 2.4.0 internal linkage-cache failure; a full
  linkage rerun passed. This changes build caching, not runtime semantics.
- Upstream's direct `.ocd2` → `.ocd` tool path aborts on unsorted MARISA IDs. Oracle fixture generation
  uses upstream text export and text → `.ocd` sorting; reference source is unmodified.
- Initial data-only measurement: all 22 built-in `.ocd2` files total 1,246,899 raw bytes; individually
  deflated files total 606,213 bytes. This is not a final application-size budget or claim.
- MARISA adaptation attribution and the selected BSD-2-Clause licence text are recorded.

### Resource adapters and selective data

- Added JVM/classpath, Android assets, Apple bundles and POSIX file providers, plus Windows UTF-16
  file access. Browser/Wasm blocking filesystem operations explicitly report unavailable files;
  host-supplied async resource preparation remains supported, with web/Node helpers pending.
- Validation: Unicode filesystem/resource tests and real packaged-config loading passed on JVM;
  adapters compiled on Android, iOS simulator, macOS, Linux x64 and Windows x64. Linux/Windows runtime
  tests have not run on this macOS host.
- Added Gradle complete/selected bundle generation, dependency manifests, original binary/config
  assets and deterministic zip archives. Full data includes 23 configs (16 built-in + 7 Jieba),
  28 dictionary/model resources and licence notices. Jieba conversion itself remains pending.
- Four data acceptance tests passed: dependency closure including optional data, exclusion of unrelated
  resources, shared-resource deduplication, hashes, reproducible zip bytes and stale-file pruning.
- JVM data JAR inspected: one copy of each asset, 23 dictionary images/configs and no nested bundle zip.
- Measured data archives, including provenance/notices: `s2t` selection 505,317 bytes; full distribution
  including Jieba assets 6,581,710 bytes. These are data-container measurements, not final app budgets.
- Remaining: automatic Android/Apple/web delivery in published consumer fixtures, per-config Maven
  data-pack publication, verification/caching providers, runtime footprint/performance and size gates.

### Darts builder and dictionary serialization

- Added a pure Kotlin Darts keyset builder matching upstream's unit placement, free-block ring and
  fixed-width binary serialization. Builders remain in `opencc-dictionary-tools`, outside core.
- Added text, BinaryDict payload and SerializedValues writers, explicit byte order and field limits.
- JVM validation: all 22 built-in `.ocd` outputs were byte-identical to upstream; native OpenCC loaded
  each Kotlin-generated file and exported identical text. Three common writer tests passed.

### MARISA builder and format conversion

- Added a pure Kotlin nested LOUDS builder, compressed suffix tails, packed link vectors, native
  acceleration tables and ordered value serialization. No runtime dependency on the builders.
- Added all upstream dictionary-converter formats: text, text_space, ocd, ocd2 and cppjieba_utf8,
  including base/user dictionary precedence and metadata. Host I/O remains separate.
- JVM native validation: all 22 built-in `.ocd2` images and the 348,986-entry merged Jieba image were
  byte-identical; native OpenCC read every generated built-in image. Both writers also matched 26
  varied custom dictionaries (empty keys excluded from the upstream Darts validator).
- Five common tooling tests passed on JVM, JS/Node, Wasm/Node, macOS Arm64 and iOS Arm64 simulator.
- Added the Darts adaptation licence to generated data bundles alongside its attribution.
- Further MARISA byte-order/word-layout fixtures remain required before milestone 3 closes.

### Binary layout portability

- Added explicit/detected MARISA packed-word layouts for little/big endian and 32/64-bit words.
  Little-endian 32/64 layouts coincide; big-endian layouts require different word decoding.
- An independent Python translator preserves native graph/ID/rank/cache data and changes numeric
  layout only. All 88 built-in variants matched the Kotlin writer and reader on JVM.
- A translated native BE32 fixture and current/legacy/endian Darts fixtures passed on every available
  common-test runtime. Native big-endian/32-bit hardware itself has not been used; details and limits
  are recorded in `docs/DICTIONARY_FORMATS.md`.

### Phrase extraction

- Added the statistical extraction pipeline in its separate common Kotlin module: suffix/prefix
  extraction, frequency/probability, candidate ordering, cohesion, both entropies, filters and reset.
  Public values are Kotlin strings, immutable signal snapshots and copied lists; length options
  count Unicode scalar values. Extraction sessions own their mutable state.
- Ported every upstream PhraseExtract unit case into five common tests; all passed on JVM, JS/Node,
  Wasm/Node, macOS Arm64 and iOS Arm64 simulator.
- Native differential checks passed on 35 corpora, including supplementary characters and a large
  rare-phrase corpus: 7,552 candidates had identical ordering, frequencies and filter decisions;
  floating statistics agreed within 1e-12. Both accepting and rejecting default filters were tested.
- CLI formatting/distribution remains separate acceptance work.

### Segment maps and experimental candidate APIs

- Added original-to-final half-open UTF-16 maps composed through normalization, dictionary stages
  and converter pipelines. Maps retain complete input/output coverage through straddled matches,
  expansion, deletion, supplementary characters and atomic IDS sequences.
- Added opt-in upstream candidate lookup, ambiguity annotations and ambiguity streaming. Candidate
  branches preserve ordering/deduplication; streaming uses shared conversion window rules and global
  source indexes. Pipeline accessors now return the last stage's segmentation as upstream does.
- Native checks uncovered and fixed inline config values incorrectly split on spaces: they are now
  one literal string, matching upstream. Key-only entries now preserve their key as default output.
- Validation: 22 common core tests passed on JVM, JS/Node, Wasm/Node, macOS Arm64 and iOS simulator.
  Native comparisons passed for 160 built-in cases, 32 custom inline/normalization/group/deletion
  cases and streaming windows/final flush/source indexes. Existing 556 answers, five golden corpus
  pairs and dictionary interoperability checks also passed.
- Malformed UTF-16 test strings are now constructed at runtime: Kotlin/JS replaced lone surrogate
  string literals during code generation, which otherwise prevented those tests exercising the input.

### Optional script detection

- Added the optional, immutable `ScriptDetector` with the planned distinguishing-character set rules,
  configurable ratios/confidence counts and Hong Kong/Taiwan variant/phrase scoring.
- Dependencies stay at core only; callers select dictionaries explicitly. The resource factory loads
  six dictionaries for regional scoring or just two for character-only detection.
- Validation: three common detection tests passed on JVM, JS/Node, Wasm/Node, macOS Arm64 and the iOS
  simulator. A JVM test with the real upstream dictionaries verified script/region examples and the
  two-versus-six resource closure. Heuristic regional tie/scoring behaviour is documented.

## Completion policy

Do not describe the port as complete until every required configuration, mapped API operation,
format/tool and target has conformance evidence. Record failures and unsupported environment checks
explicitly. Publication requires configured ownership, signing credentials and release infrastructure;
local preparation is not evidence of an actual public release.
