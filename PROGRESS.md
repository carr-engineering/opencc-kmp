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

1. Continue startup/memory profiling after the primitive-array builder and owned caches. Initial
   published consumers and package-size baselines exist across Android, Swift/iOS and JS/Wasm;
   physical-device performance, broader peak-memory evidence and production delivery gates remain.
2. Complete the upstream utility inventory and malformed-resource audits. JSON/schema and encoded conversion,
   inspection, streaming and equivalent text/map/NUL operations now have differential coverage.
3. Broader CLI argument/filesystem failure and custom Jieba resource/numeric audits, plus
   Windows/Linux execution and executable-release verification.
4. Selective published data packs, CI size budgets/shrinking/performance, API compatibility and release
   infrastructure/documentation. Public publication requires configured ownership and credentials.

### Parallel completion work: resource operations

- Added owned resource snapshots and opt-in metadata/resolve operations without enlarging the
  minimal read-only provider interface. Corrected ordered filesystem lookup, regular-file checks,
  absolute names with empty search paths, and upstream missing-path diagnostics.
- ZIP metadata uses the shared snapshot contract; ordinary reads retain their original allocation
  path. File freshness adapters keep Android API 23 support and isolate native metadata layouts.
- Validation: 38 pinned native resolve/resource comparisons, platform/ZIP JVM ownership and version
  tests including 320 concurrent snapshot reads, and Android/all seven Native/JS/Wasm adapter
  compilation passed. Core snapshot tests passed JVM/JS/Wasm/macOS/iOS simulator.
- ABI integration and production-size remeasurement follow the remaining concurrent changes; this
  entry does not claim the final shipping gates have passed.

### Parallel completion work: operation inventory and utilities

- Recorded 447 public declarations across 40 pinned headers with Kotlin equivalents and documented
  ownership/platform adaptations. Added 273 individual native test cases to the inventory.
- Added String scalar-boundary and encoded ByteArray helpers for traversal, byte budgets/maps,
  IDS and selectors. Native pointer identity is replaced by documented Kotlin value semantics.
- Validation: 3,796 native-generated utility expectations and 17 directly ported UTF8Util cases
  passed JVM/JS/Wasm/macOS/iOS simulator; scanner tests and deterministic regeneration passed.
- Corrected no-candidate dictionary entry formatting to return the key, preserving the distinction
  from a single empty candidate. Full upstream assertion-level review still has 256 pending cases;
  declaration mappings are not themselves conformance evidence. ABI/size integration follows.

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

### Published data packs and browser resources

- Added configuration-identified data modules and deterministic ZIP classifiers on their KMP root
  publications. Data modules no longer depend on core. JVM target JARs retain automatic classpath
  resources; other hosts stage the published archive explicitly into their native resource system.
- Verified local standard KMP publications for JVM, Android, Apple, JS and Wasm. Data-only native
  variants required a tiny internal upstream-version declaration to produce valid klib artifacts.
- Added browser fetch and Node filesystem adapters for JS/Wasm with no extra runtime libraries.
  Fetch remains in the platform adapter, outside conversion. Independent HTTP consumers verified
  async preparation and typed missing-resource errors on both web targets.
- Complete/selective data acceptance tests and full JVM packaged-resource tests still pass.

### Independent consumers and initial APK size baseline

- Added a separate Maven-only consumer build for Android, JVM, iOS simulator, JS and Wasm. One-config
  synchronous and asynchronous resource loading passed on JVM/Node/Chrome/iOS simulator; web runs
  asserted 12 real HTTP requests per pair of JS/Wasm tests, including missing-resource failures.
- Android release APKs built with default R8 shrinking and no OpenCC keep rules. Five measured
  profiles cover baseline, engine/inline, s2t, s2t+t2s and complete offline data. Their incremental APK
  sizes were 0, 14,074, 534,496, 569,810 and 6,710,293 bytes respectively.
- Every packaged resource matched its manifest hash and selected closure; no native runtime libraries,
  duplicate entries or nested data archives were present. Runtime artifact download sizes/hashes and
  raw/compressed data/DEX contributions are recorded in `measurements/android-initial.json`.
- Added initial measured regression allowances. Play-specific download sizes, device startup/memory,
  installed Android/Swift app runtime checks and browser/Native production size profiles remain open.

### Production web distributions and installed Swift consumers

- Added isolated production profiles, shared controlled measurement code, JS minification/DCE,
  optimized Wasm distributions and a small Swift facade around the published Kotlin libraries.
- All five web profiles passed three fresh Chrome navigations each on JS and Wasm. Complete
  packaged-data bytes and actual lazy HTTP payloads are recorded separately; gzip transport uses
  browser decoding with no additional engine codec. Source maps are excluded from measured apps.
- All five Swift profiles passed three installations/launches on a temporary iOS simulator, loading
  resources from the installed application bundle. Manifest contents/hashes and app containers were
  verified; only the runner's own simulator was removed afterward.
- Incremental package measurements and initial size budgets are recorded in web/apple JSON reports
  and `docs/WEB_AND_APPLE_MEASUREMENTS.md`. The web size gate reproduced successfully.
- Native smallBinary reduced the s2t app ZIP by 41,076 bytes. It remains off by default. The first
  debug test run explicitly ignored this option; validation was corrected to release test binaries,
  and 57 common Native tests passed. Three installed smallBinary app launches also passed.
- Measured data preparation and process/heap footprints expose further optimization work. These
  host/simulator observations do not prove physical-device p95 targets or complete peak-memory gates.

### Dictionary preparation allocation

- Replaced per-node construction objects/maps with shared-prefix construction into primitive arrays;
  public lexicon IDs, ordered candidates and the allocation-free hot lookup layout are preserved.
- All 24 common engine tests passed on JVM, JS, Wasm, macOS and the iOS simulator; upstream format,
  conversion/annotation and deterministic writer checks passed. All 65,175 upstream dictionary
  entries now have complete prefix-result comparisons. Android release size gates passed.
- Three installed s2t simulator launches measured 152–154 MiB peak RSS, down from 196–198 MiB in the
  initial run. Production web/Swift consumer reports and the rejected direct-MARISA tradeoff are
  documented in `docs/WEB_AND_APPLE_MEASUREMENTS.md`; startup samples remain environment-limited.

### Explicit cache ownership and resource verification

- Core exposes a small DictionaryCache interface. Optional `opencc-cache` supplies concurrent LRU
  initialization, SHA-256 content identity, retry after failure, eviction/clear/close and sync/async
  verified-resource wrappers. The aggregate includes it; engine/data/platform dependencies do not.
- Config, SimpleConverter and async preparation accept the owned cache and stable provider identity;
  conversion state remains per call. Public lexicon/segment iterators no longer expose mutable
  platform collection iterators. Closing a cache or facade handle preserves other converters.
- Eight portable cache/integrity tests passed on JVM, JS, Wasm, macOS and iOS simulator. Five JVM
  tests cover actual eight-thread initialization/failure sharing, concurrent conversion, pending
  eviction, close/clear during a load and JDK digest parity. Four-thread Native initialization and
  conversion passed on both macOS and iOS simulator. Published independent consumers passed on
  JVM/JS/Wasm/iOS, including optional cache/verification APIs and actual resource acquisition.
- The initial combined implementation exceeded the engine dependency-download budget despite R8
  removing unused app code. Isolating optional implementations restored the unmodified budget:
  engine download increment 166,071 bytes and APK increment 14,541 bytes. All five Android size
  profiles passed; `measurements/android-cache-isolation.json` records exact resources/graphs/sizes.
- All five production JS/Wasm profiles passed three fresh browser runs and unchanged size budgets;
  all five installed Swift profiles passed three simulator launches and unchanged Native ZIP budgets.
  The optional cache implementation is exercised through test-only published dependencies, so it
  cannot silently increase the runtime graph of the uncached engine/data measurement profiles.

### Encoded-input reference audit

- Added a byte-oriented native probe and a reproducible 5,832-case corpus across four configs and
  explicit-length/C-string/buffer operations. The reference accepts many partial/malformed sequences,
  while standalone continuation bytes usually raise InvalidUTF8; an empty chain returns raw bytes.
- Confirmed config-dependent NUL behavior and different buffer/prefix contracts. These expose
  concrete differences in the current strict Kotlin decoding path. API mapping now marks encoded
  parity pending explicitly; `docs/ENCODED_CONTRACT_AUDIT.md` records examples and next coverage.
- This audit does not change runtime conversion or claim encoded parity. It is the evidence used
  for the next implementation milestone; strict Kotlin String validation remains a separate contract.

### Byte-preserving conversion and equivalent text contracts

- Implemented direct encoded dictionary prefix lookup, conversion chains/pipelines, explicit-length
  and NUL-terminated input, safe byte buffers, typed encoded inspection and encoded streaming with
  native leading-byte/partial/IDS/backwards-window semantics. Cached encoded key lengths and bounded
  integer accounting avoid per-character byte arrays and JS Long allocation; inspection byte types
  retain useful fields without unnecessary generated copy/equality methods.
- Corrected native NUL semantics in inline JSON entries, segmented String conversion, inspection,
  potential ambiguities and composed UTF-16 deletion maps. Empty stream finish skips conversion.
- The native differential suite passes 71,775 cases across 29 configs, including every built-in config
  using original images, text/ocd/ocd2 fixtures, nested groups, normalization, empty chains and inline
  NUL entries. It checks byte prefixes/modes/buffers, full inspection trees, streaming chunk outputs
  and final flush; valid inputs additionally check Kotlin String equivalents and composed maps.
- Eight portable encoded tests passed on JVM, JS, Wasm, macOS and iOS simulator. All prior engine
  tests and native format/writer checks passed; built-in candidate/ambiguity comparisons expanded to
  192 cases including NULs. Detailed contracts are in `docs/ENCODED_API.md`.
- Initial dependency measurements exposed a known engine-only download regression over the earlier
  partial-engine budget. Required byte behavior is retained; the allowance is unchanged. Optimization
  and complete-engine release baselines remain acceptance work, alongside the other open milestones.

### Lean internal JSON model

- Replaced unused internal JSON data-class copying/equality/stringification with small immutable
  field holders; parser acceptance, field ordering and duplicate handling are unchanged.
- Full 71,775-case native differential checks and all 32 common engine tests passed on JVM,
  JS/Wasm and macOS/iOS simulator. Published byte contracts had independently passed on
  JVM/JS/Wasm/iOS; all production web profiles passed unchanged size gates.
- Relative to the encoded feature checkpoint, Android dependency artifacts shrink by 3,265 bytes
  and engine APK by 479 bytes. Latest engine increments are 189,343 download bytes and 14,207 APK
  bytes. All APK profiles and selected/full dependency profiles pass; the engine-only dependency
  budget (176,932 bytes) still fails by 12,411 bytes and remains open. No allowance was widened.
- Exact measurements are in `measurements/android-lean-json.json`; this optimization retains all
  encoded/string behavior and candidate data, rather than removing capabilities to satisfy a gate.
- Installed Swift consumer checks passed for all five profiles (three launches each), but the
  Native engine-only ZIP increment is 102,182 bytes versus its 99,800-byte budget, a 2,382-byte
  regression. Selected/full profiles pass their ZIP budgets. `measurements/apple-encoded.json`
  records the complete runs; the Native and dependency engine-only gates remain open together.

### Pinned schema, JSON semantics and shared resource loading

- Implemented the complete finite pinned schema with advisory first-violation diagnostics, matching
  document/schema pointers and keywords. Corrected number overflow, Unicode escapes, actual NUL
  input suffixes, logical string prefixes, duplicate-key diagnostics and semantic validation order;
  removed the artificial JSON nesting limit. Native error wording maps to Kotlin string/boolean.
- Missing segmentation extensions now have the correct base error capability. Immutable plugin
  options expose both convenient first-occurrence Map lookup and every ordered duplicate pair.
- Sync/suspend config loading shares validation and acquisition order. Async preparation no longer
  mistakes inline file keys or ignored metadata for resources, or fetches disabled risk dictionaries.
  Factories can specify option-dependent/additional preparation names before synchronous creation.
- Native comparisons pass 3,940 string/file/risk-option cases, including schema pointer/keyword and
  semantic diagnostics, alongside the 71,775 encoded cases and all existing conversion/format checks.
  Six common config tests and cache tests pass across five runtimes, including real suspend/resume,
  load/error order, deduplication, option selection and plugin preparation. Contracts are documented
  in `docs/CONFIGURATION.md`. Publication/consumer and size checks follow this feature checkpoint.
- Published JVM/JS/Wasm/iOS consumers passed the updated loader, including actual HTTP acquisition.
  Native conformance always reruns when its external probes/fixtures are regenerated, preventing
  stale up-to-date test results from standing in for current reference comparisons.

## Completion policy

Do not describe the port as complete until every required configuration, mapped API operation,
format/tool and target has conformance evidence. Record failures and unsupported environment checks
explicitly. Publication requires configured ownership, signing credentials and release infrastructure;
local preparation is not evidence of an actual public release.

### Configuration loading without synchronous coroutine machinery

- Made advisory schema diagnostics optional; the callback retains complete pinned diagnostics when
  requested, while apps that do not log warnings can shrink the validator away.
- Sync and suspend drivers now use one ordered load session. Only the async driver awaits resource
  requests; parsing, extension creation, validation order and decoded dictionary reuse are preserved
  without replay or a coroutine bridge in synchronous applications.
- All 38 common engine tests passed across JVM/JS/Wasm/macOS/iOS, together with 3,940 configuration
  and 71,775 encoded differential cases and prior format/conversion checks. Independent published
  JVM/JS/Wasm/iOS consumers passed, including twelve actual HTTP requests.
- Measured each optimization in the checked-in android-config reports. The final engine APK increment
  is 25,111 bytes, down from 30,412 for the original shared suspend loader; dependency downloads are
  210,469 bytes, down from 218,290. All selected/full APK profiles remain within their allowances.
  Engine APK/download and selected-pack dependency budgets still exceed the earlier partial-engine
  baselines; no allowance was widened and complete-engine size acceptance remains open.

### Optional common Jieba segmentation and complete resource closure

- Added opencc-jieba with the pinned weighted prefix/DAG routing, HMM/Viterbi, user metadata and
  precedence, ASCII/decimal rules, scalar search limit and encoded rune decoder. Packed arcs,
  primitive frequency/probability arrays and per-call scores avoid per-character lookup allocation
  and unnecessary temporary objects. Loaded models are immutable; no global caches/native bindings.
- Added an explicitly registered resource factory, synchronous and actually suspending merged/text
  fallback, installed user defaults, relative/auxiliary lookup, and native plugin NUL/encoded coverage
  errors. SegmentationFactory.prepareResources allows host extensions to perform optional/fallback
  acquisition before synchronous creation. SimpleConverter now accepts factories directly.
- The complete aggregate includes Jieba; core's runtime graph remains stdlib only. Corrected selected
  Jieba packs to include implicit IDF/stop-word dependencies, with deduplication and exclusion checks.
  Retained cppjieba MIT attribution and documented the Kotlin ownership/resource/API equivalents.
- Native checks pass 2,064 direct segmentation, 9,632 conversion/error and 4,032 streaming cases,
  including all upstream testcase inputs and all seven experimental configs under both risk options.
  Byte chunks/final flush, supplementary text, NULs, malformed bytes, String equivalents and composed
  maps are covered. Eight JVM threads share a real model and verify native token answers concurrently.
- Four common tests pass on JVM, JS, Wasm, macOS and iOS simulator. The remaining Native variants
  compile on Linux x64, Windows x64, iOS Arm64/x64 and macOS x64; runtime tests there still require
  their hosts. The complete aggregate's dependency metadata resolves successfully.
- Published full-bundle JVM/JS/Wasm/iOS consumers pass all seven configs, actual large-model decoding,
  shared immutable segmentation and asynchronous preparation (148 real HTTP requests). Jieba remains
  test-only in these existing measurement profiles. The full-model conformance timeout is 60 seconds;
  it is not a performance budget. All five data-pack acceptance checks pass.
- Additional library artifact bytes are recorded in measurements/jieba-initial.json: JVM 38,149;
  Android 36,634; JS 51,655; Wasm 51,687; macOS 52,192; iOS simulator 52,244. These exclude existing
  core/stdlib, data and application size and are not presented as a complete dependency measurement.
  Broader custom numeric/model/malformed-resource audits and physical-device size/performance gates
  remain open. CLI completion is the next major feature milestone.
- Latest Android measurements preserve the runtime module graphs and restore every APK profile's
  unchanged budget: engine APK increment 17,285 bytes. Engine dependency increment is 215,282 bytes;
  engine/one/multi dependency budgets remain exceeded. measurements/android-jieba-extension.json
  records these gates explicitly; no download allowance was widened.

### Encoded ambiguity and stored-ZIP prerequisites for CLI conformance

- Added experimental byte-oriented ambiguity conversion and streaming with byte-unit spans, source
  identity, normalization, group/chain composition, native windows and caller-owned result arrays.
  Reused the shared alignment composition; normal conversion lookup allocation behavior is unchanged.
- Encoded UTF-8 failures can retain copied logical byte context and a stage-buffer offset. This lets
  CLI diagnostics preserve malformed native bytes without placing them in ordinary Kotlin text.
- Added optional opencc-zip with the pinned stored-entry format, name filtering/normalization,
  duplicate/basename semantics, immutable archive ownership, opaque entry cache keys and typed errors.
  Core and ordinary platform/data dependency graphs do not gain ZIP code or a decompression codec.
- Four encoded ambiguity/ownership/range tests and two ZIP tests passed on JVM, JS, Wasm, macOS and
  iOS simulator. In-progress CLI execution also passes 96 native process comparisons on JVM and
  macOS Native, including ZIP modes, malformed JSONL input and input beyond the 1 MiB buffer.
  This is prerequisite evidence, not yet a claim of complete CLI/distribution or malformed-resource
  conformance. CLI source remains under active implementation for subsequent focused commits.

### Common command-line tools and local executable distributions

- Replaced the scaffold with stateless common implementations of opencc, opencc_dict and
  opencc_phrase_extract. Added pinned options/defaults/TCLAP diagnostics, all four conversion modes,
  byte-transparent/human JSON/strict ambiguity JSONL framing, variation warnings, timing/count
  reports, stored ZIPs, config-relative/package/environment/executable resource lookup and in-place
  identity/temporary-file/metadata/replacement handling. Tools use the existing common algorithms.
- JVM and Native have thin owned stream/filesystem adapters and exact statistical number formatting.
  Native Windows uses typed UTF-16 file paths, command-line arguments and environment APIs. Linux,
  Windows x64 and macOS Arm64 sources compile; Native runtime checks here cover macOS Arm64.
- All three tools have JVM entry points and named Native release executables. Reproducible JVM and
  macOS archives include complete offline data and notices. Both were unpacked and launched from an
  unrelated cwd without data/plugin environment variables; standard/Jieba configs and all launchers
  passed. This is local distribution evidence, not a public signed release.
- The final matrix passes 212 process cases on JVM and another 212 on Native, comparing stdout,
  stderr, exit codes, produced files, mutation/mode and measured-result schemas with upstream. It
  exercises all 23 configs in four modes, malformed bytes/NUL/CRLF/IVS, stored ZIPs, embedded options,
  >1 MiB UTF-8/JSONL, deterministic dictionary combinations, cppjieba multi-input metadata, nonempty
  phrase statistics, in-place/symlink/hard-link behavior and error timing. Exact evidence is in
  measurements/cli-initial.json and can be regenerated with tools/test_cli*.py.
- CLI contracts/build/verification are documented in docs/CLI.md. Broader malformed-resource and
  filesystem/argument audits remain, as do Windows/Linux runtime checks, Windows JVM link-count
  parity, public release/export/licensing checks and the outstanding size/performance/CI gates.
  No milestone is marked complete merely because these selected CLI process cases passed.

### API baselines, documentation and actual publication payloads

- Enabled Kotlin's integrated ABI validation and committed the initial JVM/KLIB baselines for every
  declared target. Generated data selections have no public API and are excluded from baseline
  storage. Added Dokka's combined HTML reference and explicit shared JVM/Android adapter ownership.
- Fixed defects revealed by actual publication: the aggregate now builds a real Native carrier KLIB,
  and CLI process exit uses a platform adapter so common metadata compiles as well as JVM/Native code.
- Added module-specific license/provenance embedding before metadata/signing, plus artifact checksum,
  notice and runtime graph verification. Preserved TCLAP MIT attribution for parser/help adaptations;
  the pinned vendored help header was compared byte for byte with the official v1.2.5 release.
- All-target ABI generation passed; ABI checks, Dokka, JVM platform tests and Android adapter
  compilation passed. Refreshed all local publications for JVM/Android/JS/Wasm/macOS Arm64/iOS
  simulator; 87 publication records and 199 artifact files pass embedded-notice/checksum/graph checks.
  Published full-bundle JVM/JS/Wasm/iOS tests pass with 148 actual HTTP resource requests. Legal payload
  placement, preserved compiled bytes, stale replacement and repeated-byte determinism are also tested.
- A stricter graph check identified Kotlin's automatically added legacy DOM dependency in JS.
  Disabled that unused dependency in libraries and consumers; refreshed metadata now enforces stdlib
  as core's only runtime dependency. JS/Wasm module tests, all-target ABI checks and independent
  full-bundle consumers pass after the change. Browser resource operations remain available.
- Public ownership/credentials, remote CI, remaining export/API/resource audits and measured release
  budgets are still open. Artifact notices add delivered bytes; those bytes must be included in the
  next size run. Existing allowances have not been widened.

### Cross-host CI definitions and repository contribution guidance

- Added immutable-action-pin jobs for Linux differential matrices and unpacked distributions,
  Windows JVM/Native execution and Unicode tool smoke, and Apple all-target ABI/documentation/local
  publication/consumer checks. All size gates run independently and retain reports on failure.
  Added checksum-pinned actionlint, syntax/whitespace checks and dependency update proposals.
- Generalized the CLI differential runner's Native target selection. The macOS process matrix still
  passes all 212 cases; a separate JVM/Native smoke runner passes 22 checks for tool help/version,
  Unicode paths/stdin, in-place output and binary dictionary round trips. The smoke runner does not
  claim the complete Windows oracle matrix.
- Added contribution/release procedures, conduct/security policies, an unreleased changelog and
  issue/PR templates. Owner-specific reporting contacts and public publishing remain unconfigured.
  Workflow lint and Linux/Windows Gradle task-graph selection pass locally. There is no Git remote,
  so hosted CI and Windows/Linux runtime success are not claimed.
- All-target publication uncovered a POSIX metadata-commonization failure hidden by individual
  target compilation: Darwin/Linux stat fields have different native widths. Moved field access
  into their thin target adapters, preserving the filesystem contracts without leaking those typedefs
  into common metadata.
- Every target now publishes locally and the complete ABI check passes. Artifact verification
  covers 150 publication records and 362 files across all eleven targets. Rebuilt macOS tools pass
  the 212-case differential matrix, 22 JVM/Native smoke checks and the unpacked complete-data archive.
- Refreshed Android production evidence after attribution packaging in
  measurements/android-attributed-publication.json: engine APK increment is 17,289 bytes, dependency
  increment 235,272 bytes. All four APK budgets pass; engine/one/multi download budgets fail and the
  complete bundle passes. Budgets remain unchanged; CI intentionally exposes these unresolved gates.

### Byte-preserving Android download optimization

- Identified ineffective nested compression in AARs: individually compressed classes prevent the
  outer ZIP from exploiting repeated class/metadata structure. Store the inner JAR entries and use
  standard DEFLATE level 9 on the enclosing AAR before publication metadata/signing.
- The engine AAR is 159,808 bytes, down from 235,272 (32.1%). All 127 class/metadata/resource entries
  match the previous publication byte for byte; outer payloads and attribution are unchanged. No
  runtime decoder, API/module split or algorithm change is introduced. Intermediate JAR disk usage
  grows to 511,197 bytes; build/read timings are packaging diagnostics, not Android performance claims.
- Every production APK retains the preceding measurement's size. All eight unchanged Android
  APK/download budgets now pass: dependency increments engine 159,808, one 675,850, multi 710,667,
  full 6,752,265 bytes. Exact environment, dependency hashes and ZIP/DEX detail are preserved in
  measurements/android-aar-compression.json; archive alternatives/hashes in aar-compression.json.
- Four payload/routing tests, artifact checksum/graph verification and independent full-bundle
  JVM/JS/Wasm/iOS consumers pass (148 HTTP requests). Remaining platform/export/API/performance
  and release gates remain required; Android size success is not full-plan completion.

### Faithful decimal acceptance without Native regex retention

- Replaced the general float-parser range check with the pinned RapidJSON default staged decimal
  arithmetic. Fixed previously missed zero/exponent acceptance and huge-mantissa behavior; retained
  original numeric tokens and errors. Included the adapted MIT license in core publications.
- Added 1,012 range comparisons, bringing config coverage to 4,952 cases through both native load
  entry points/risk options. Full encoded (71,775), conversion/format/annotation conformance passes.
  Forty-four common engine tests pass on JVM/JS/Wasm/macOS/iOS; every power constant is bit exact.
  Complete ABI checks, all target publication and 150-record/362-file payload verification pass.
- Native symbol inspection confirms zero regex symbols. A 309-double table initially increased
  Android APK cost; replaced it with 36 constants and exact ULP corrections, verified on all five
  runtimes and Native release builds. Fixed overflow thresholds avoid unsigned-division machinery.
- All Android APK/download gates pass unchanged: engine 17,993/163,150 bytes; one
  537,273/679,321; multi 572,596/714,138; complete 6,713,090/6,755,733. The rejected large-table
  report is retained beside the current android-decimal-compact.json report.
- All Apple budgets pass with the explicitly evaluated smallBinary release option: engine ZIP
  increment 99,054; one 624,630; multi 659,910; complete 6,703,271. Every installed profile passes
  three launches. Native release engine/concurrency/Jieba/ZIP checks pass. CI selects this option
  explicitly; general defaults remain off. Physical-device performance remains unverified.
- A full-disk failure interrupted browser/Apple reruns. Removed only obsolete local publication
  snapshots and generated Xcode artifacts, preserved every current publication, and stopped the
  identified task-owned idle daemon. Subsequent sequential Apple/browser measurements complete.
  All production web budgets pass: engine JS/Wasm increments 27,425/17,715 gzip bytes; every
  selected/full profile executes successfully. Exact evidence is in web-decimal-compact.json.
  Full published JVM/JS/Wasm/iOS consumers pass with 148 HTTP requests. No allowance was widened
  and no environment failure was reported as conformance.

### Isolated HTTP delivery in published-consumer fixtures

- Replaced the fixed HTTP port with an OS-assigned ephemeral port and generated its URL in test-only
  Kotlin sources. Independent/concurrent test runs no longer conflict with an existing local service.
  Production fixture source and dependency graphs are unchanged.
- Full JVM/JS/Wasm/iOS published consumers pass with 148 actual requests, including missing-resource
  errors, async preparation and all bundled Jieba configurations. No unrelated service was stopped.
