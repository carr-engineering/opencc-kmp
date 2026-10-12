# Build `opencc-kmp`: a complete Kotlin Multiplatform port of OpenCC

## Goal

Create a **complete, faithful, pure-Kotlin Kotlin Multiplatform (KMP) port of OpenCC** (Open Chinese
Convert, https://github.com/BYVoid/OpenCC): the conversion engine, every public API, every bundled
configuration and dictionary, the dictionary formats and the command-line tools. It must produce
**exactly the same output as upstream OpenCC** for every configuration and input, run identically on
JVM/Android, iOS, macOS, Linux and Web (Wasm/JS), and be fast and small enough for mobile apps and
browsers. The library is generic: it carries no application-specific logic.

**Compatibility principle**: preserve upstream API operations, feature availability and observable
conversion behaviour, using types and ownership conventions natural to Kotlin Multiplatform. Native
C/C++ types, pointer mechanics and allocation conventions are not compatibility requirements. Every
upstream operation must have a documented Kotlin equivalent. Apply this principle to conversion,
configuration, dictionaries, inspection, errors and resources, not only text input and output.

**Size goal**: minimize dependency downloads and the incremental download size of consumer
applications without removing features, dictionary entries or candidates, or changing conversion
results. Complete feature availability remains mandatory; smaller deployments result from explicit
dependency and data selection. A complete offline distribution must remain available.

## Licence and provenance

- Licence: **Apache-2.0**, the same as OpenCC's code and data. Ship upstream's `NOTICE`, `AUTHORS` and
  licence text with the artifacts and credit OpenCC in the README.
- Track the **pinned upstream release `ver.1.4.2`**. During initial setup, record its exact commit and
  the SHA-256 of the source archive; builds must verify these identities.
  The README and artifact metadata state which upstream version the port and its data correspond to.
  Upgrading to a new upstream release is a documented, repeatable process.
- Behavioural reference is **upstream C++ OpenCC**. Use `nk2028/opencc-js` and `laisuk/openccjava`
  (MIT) only as secondary references; when they differ from upstream, upstream wins.

## Scope: what "complete" means

Port everything in upstream's public surface, mapped to idiomatic Kotlin, with a documented
upstream → Kotlin mapping table in the README. Derive the complete API/config/tool inventory from
the pinned release's headers, configurations and tools; the list below does not replace that audit.
Record overloads, options, defaults, errors and platform-bound operations, including segmentation
extensions and their compatibility. Preserve the capabilities of bundled segmentation extensions in
Kotlin and document how hosts supply equivalent extensions through common interfaces; native binary
plugin loading is a platform boundary, not an engine dependency. Document experimental features as
such, without silently omitting them:

1. **Configurations**: every config shipped upstream, loaded from upstream's JSON config files
   unchanged: `s2t`, `t2s`, `s2tw`, `tw2s`, `s2twp`, `tw2sp`, `s2hk`, `hk2s`, `t2tw`, `tw2t`, `t2hk`,
   `hk2t`, `t2jp`, `jp2t`, and any others present in the pinned release (including ones upstream marks
   experimental or in development, such as `s2hkp` / `hk2sp`, exposed and documented as such). Custom
   user configs in the same JSON format must also load.
2. **Config model**: the full pinned schema and loading behaviour: `name`, optional `normalization`,
   segmentation, `conversion_chain`, dictionary types `text`, `ocd`, `ocd2`, `inline` and `group`,
   nested groups and their matching policies, load options and resource references. Resolve file
   references relative to a config directory or provider, matching upstream where filesystem
   operations are supported. Match upstream JSON acceptance, validation, warnings and errors,
   including comments and trailing commas; selecting a smaller parser must not narrow compatibility.
3. **Library classes / API** (Kotlin equivalents of upstream's C++ API, available through the
   appropriate runtime or tooling module):
   - `Config`: load a config from a file path, a string, or a provider-backed resource; errors as typed
     exceptions mirroring upstream's (`FileNotFound`, `InvalidFormat`, `InvalidUTF8`, `ShouldNotBeHere`…).
   - `Converter`: `convert(String): String`, prefix/range conversion, conversion into a buffer or
     appendable, and inspection with typed results. Preserve all upstream conversion operations
     through idiomatic Kotlin overloads with explicit units, as defined in API contracts below.
   - `SimpleConverter`: the convenience facade, constructed from a config name or path, with
     `convert` overloads equivalent to upstream's.
   - `Segmentation` / `MaxMatchSegmentation` and `Segments`, with common Kotlin collections and
     interfaces for segments and extensions.
   - `ConversionChain`, `Conversion`, pipeline/normalization behaviour and streaming conversion,
     including chunk-boundary and final-flush semantics.
   - The dictionary abstractions and implementations: `Dict`, `DictEntry` (single and multi-value),
     `Lexicon`, `TextDict`, `DartsDict` (`.ocd`, double-array trie), `MarisaDict` (`.ocd2`, marisa
     trie), `SerializedValues`, `DictGroup`, and the dictionary converter between formats.
     Expose entries and ordered candidate values using common Kotlin types and collections.
   - `UTF8Util` behaviour where it affects results: character boundaries, surrogate pairs and
     supplementary-plane characters, invalid input handling as upstream defines it.
   - **Phrase extraction** (`PhraseExtract`): the statistical phrase-extraction utility shipped with
     upstream, with the same options and outputs.
4. **C-API-equivalent convenience layer**: `open(configName)`, `convert(text)`, `close()`, and the
   last error, mapped to a small Kotlin facade preserving the operations and error capabilities.
   Use Kotlin ownership for returned strings and buffers; callers must not manually free them.
   Document handle lifetime, close behaviour and instance-scoped error state, including how these
   Kotlin conventions map to upstream's allocation and global-error conventions.
5. **Dictionary formats, read and write**:
   - `.txt` (tab-separated, multi-value),
   - `.ocd` (darts double-array trie),
   - `.ocd2` (marisa trie + serialized values).
   Files produced by upstream tools must load in the port, and files produced by the port must load
   in upstream, byte-identical where upstream's writer is deterministic. This requires a pure-Kotlin
   implementation of the marisa-trie and darts structures as used by OpenCC. Specify supported
   dictionary byte orders, binary versions and legacy layouts, and verify interoperability across
   platforms; do not assume every upstream image is architecture-independent.
6. **Data**:
   - Every dictionary and config in the pinned release.
   - Reproduce upstream's **generated** dictionaries exactly, following upstream's build rules
     (BUILD.bazel / CMake): `STPhrases_GeneratedFromRegionalPhrases`, `TSCharactersExt`, all `*Rev` reverse
     dictionaries (`TWPhrasesRev`, `TWVariantsRevPhrases`, `HKVariantsRevPhrases`, `JPVariantsRev`, …).
   - Build `.ocd2` files from the `.txt` sources as upstream does.
7. **Command-line tools** (a separate `cli` module, JVM and Kotlin/Native executables): `opencc`
   (convert files/stdin with `-c/--config`, `-i/--input`, `-o/--output`, `--path`, `--noflush`, …),
   `opencc_dict` (convert between dictionary formats: `-i`, `-o`, `-f/--from`, `-t/--to`) and
   `opencc_phrase_extract`. Same options, same exit codes, same output as upstream.

## API contracts

- Keep corresponding upstream operations and features available, with idiomatic Kotlin names and
  signatures in the mapping table. Do not introduce native-type replicas solely to match signatures.
- Use `String` for ordinary text input/output, typed results for inspection, common Kotlin collections
  for entries/candidates, and interfaces for resources. Preserve ordered candidates and dictionary
  group precedence in every representation.
- Kotlin string prefix/range operations and segment maps use **half-open UTF-16 code-unit ranges**.
  Document supplementary-character boundaries and malformed UTF-16 handling. Upstream operates on
  encoded input, so define the Kotlin string policy explicitly rather than attributing a UTF-16
  contract to upstream.
- Use `ByteArray` when encoded input, encoded output or binary dictionary data is meaningful. For
  encoded-input operations, preserve upstream UTF-8 byte-length behaviour, embedded NUL handling,
  partial-character handling and invalid-input errors without imposing byte indexing on `String`.
- Specify buffer capacity, offset/range validation, written-length units, failure behaviour and
  error timing for each overload. Safe Kotlin buffer handling must preserve the operation's
  capabilities without reproducing unsafe pointer mechanics.
- Use Kotlin ownership for returned values. Document which resource-owning handles require `close()`,
  how close interacts with shared dictionaries, and how errors map to typed exceptions or facade
  error state. Text conversion results require no manual deallocation.
- Add asynchronous resource preparation/loading for browser and host-downloaded resources; conversion
  remains synchronous once prepared. Retain synchronous loading and convenience constructors for
  locally available resources. Resource acquisition must not introduce networking into conversion.

## Targets

- Kotlin Multiplatform with all conversion algorithms in `commonMain` and no platform-specific
  algorithm implementations: no JNI, no native OpenCC, no reflection. Thin platform adapters may
  implement common resource/I/O interfaces using platform APIs; prefer ordinary interfaces and small
  factories, using `expect`/`actual` where appropriate.
- Targets: `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`, `macosX64`,
  `linuxX64`, `mingwX64` (if feasible), `wasmJs`, `js`.
- `opencc-core` depends only on the Kotlin standard library (kotlinx-serialization or a small
  hand-written parser for JSON configs is acceptable). File I/O sits behind an abstraction (kotlinx-io or
  a `ByteSource` / `ResourceProvider` interface), so web and mobile hosts can supply bytes from bundled
  resources or verified downloads. The core never touches the network and must not transitively
  depend on bundled data, dictionary tooling, phrase extraction, CLI or detection modules.
- Define resource delivery for each target: JVM resources/files, Android assets, Apple bundles/files,
  and browser/Node.js resources. Verify packaging and lookup through published-artifact consumer
  fixtures, including asynchronous preparation where the host cannot supply resources synchronously.

## Modules

- `opencc-core`: config model, segmentation, conversion chain, runtime dictionary implementations and
  readers, streaming/inspection, the `SimpleConverter` facade and the C-API-style facade.
- `opencc-dictionary-tools`: dictionary builders, writers and format conversion. Keep these
  implementations and dependencies outside the conversion runtime, while preserving their public
  operations through this module and the complete aggregate dependency.
- `opencc-platform`: thin resource/I/O adapters for supported targets. Host-supplied implementations
  of the common interfaces remain supported without requiring the bundled adapters.
- `opencc-cache`: optional common dictionary-cache and verified-resource implementations, depending
  only on core. Core exposes the cache interface without pulling these implementations into engine
  dependency downloads. Include this module in the complete aggregate dependency.
- `opencc-jieba`: bundled Jieba segmentation algorithms and resource factory in common Kotlin,
  depending only on core. Hosts register its factory explicitly; include it in the complete
  aggregate without adding extension code or data to core-only dependency downloads.
- `opencc-zip`: optional common stored-ZIP resource reader matching the pinned provider's supported
  format, lookup and error capabilities. Include it in the complete aggregate; keep ZIP handling
  outside core and ordinary file/asset adapter downloads.
- `opencc-data`: every upstream dictionary and config, in the formats upstream ships (`.ocd2` plus
  JSON), plus a manifest (file → SHA-256 → size and config → resource dependencies). Produced by a
  reproducible Gradle build from the pinned upstream release. Retain this complete offline bundle.
  Also provide configuration-selectable packs and a build task for generating a selected bundle:
  - Compute each selected config's complete dependency closure, including normalization,
    segmentation, conversion chains, nested groups, generated/reverse dictionaries and option-enabled
    resources. Preserve optional-resource availability for supported load options.
  - Deduplicate dictionaries shared by selected configs. Preserve original resource names and JSON
    resolution; selected bundles must not silently substitute smaller dictionaries or conversions.
  - Distinguish **lazy loading** (startup/memory), **selective bundling** (download size), and
    **host-managed downloads** (resource acquisition). Lazy loading alone does not reduce bundle size.
  - Load only needed dictionaries, share immutable loaded instances through explicitly owned caches,
    and support release without invalidating dictionaries still in use by another converter.
  - Ship dictionaries as binary resources, not Base64, Kotlin literals or generated entry objects.
    Compare ordinary package/transport compression with additional codecs or compact representations,
    counting decoder code, startup time and peak memory along with compressed bytes. Any internal
    representation must retain all candidate data, public dictionary operations and file-format
    interoperability, with differential tests proving parity.
- `opencc-phrase-extract`: phrase extraction, always separate from core.
- `opencc-cli`: the three tools.
- `opencc-all`: aggregate dependency exposing all upstream library operations, tooling, platform
  adapters and the complete offline data bundle. CLI executables are distributed separately.
  Smaller consumers select modules and data explicitly; no feature is removed from the distribution.
- `opencc-detect` (**optional extra, not part of upstream**, clearly marked as such):
  - Detects whether text is Simplified, Traditional, Mixed or Neutral, using OpenCC's own data.
  - S-only set = keys of `STCharacters` that are neither keys of `TSCharacters` nor values of
    `STCharacters`; T-only set = the mirror rule.
  - Counts `s` and `t`: `s + t = 0` → Neutral; `s / (s + t) >= 0.8` → Simplified; `<= 0.2` → Traditional;
    otherwise Mixed; fewer than 3 distinguishing characters → low confidence.
  - For Traditional text, reports Hong Kong / Taiwan / generic by scoring `HKVariants` / `TWVariants`
    values and `HKPhrases` / `TWPhrases`.
  - Thresholds are parameters.
- Extension beyond upstream (in core, clearly marked as non-upstream):
  `convertWithMap(text): MappedConversion`, which returns the converted text plus a **segment map**
  (ordered half-open UTF-16 source range → target range pairs covering the whole input). Compose maps
  through normalization and every conversion stage. Hosts need it to map selections and highlights
  between original and converted text, because phrase conversion can change lengths. It must not
  change `convert`'s output. Keep this result type distinct from upstream's `Conversion` abstraction.

## Behaviour requirements

- **Bit-for-bit identical output to upstream** for every config, on every target.
- Deterministic, no global mutable state; loaded dictionaries and converters are immutable and safe
  for concurrent use, with per-call conversion state. Streaming sessions and facade error state are
  explicitly owned and have documented concurrency contracts.
- Define cache ownership, identity (provider/resource identity, content version/digest and format),
  concurrent initialization, failure handling, eviction and shared-resource lifetime. Closing a
  converter must not invalidate another converter's resources; expose no mutable cache singleton.
- Linear-time conversion; no quadratic behaviour on very long inputs (≥ 1 MB).
- Correct handling of supplementary-plane characters, CJK compatibility ideographs, mixed scripts,
  non-Chinese text (passed through except for upstream-defined normalization), empty input and invalid
  UTF-8. Define malformed UTF-16 behaviour for the Kotlin API and test its encoded-input mapping.
- Lookups must not allocate per character on the hot path.

## Testing and conformance

- Port **all** of upstream's tests: the `test/testcases` input/answer pairs for every config, the
  config tests, and the unit tests for dictionaries, segmentation, conversion chain, `SimpleConverter`,
  the C API and phrase extraction. Run them on **JVM, iOS simulator, macOS/Linux native and Wasm/JS** in
  CI; every config must pass 100%.
- **Differential testing** against upstream's native `opencc` on a large corpus (e.g. a CC-CEDICT
  dump, Wikipedia samples in Simplified, Taiwan and Hong Kong Traditional, and Japanese text for the
  JP configs). Any difference is a bug.
- Differential coverage must include normalization, candidate ordering, group matching policies and
  nested groups, load options, inline dictionaries, inspection, streaming boundaries/final flush,
  prefix/buffer operations, embedded NULs and malformed input. Verify each mapped Kotlin operation's
  equivalent observable behaviour, without requiring identical native data types.
- Config tests must cover upstream's accepted JSON extensions, validation warnings and errors.
- **Format round-trip tests**: `.txt` ↔ `.ocd` ↔ `.ocd2`, both port → upstream and upstream → port.
  Include supported byte orders and legacy layouts, and cross-target reading of generated files.
- **CLI tests**: compare output and exit codes with upstream's tools.
- Unit tests for the segment map (covers the input, ordered, UTF-16 ranges, consistent with the output,
  composed across normalization and every stage) and for `opencc-detect`.
- Tests for concurrent conversion, cache initialization/eviction, close semantics and shared-resource
  lifetime. Distinguish immutable converters from mutable streaming sessions.
- **Published-artifact consumer fixtures**, introduced early: Android, iOS, JS and Wasm applications
  loading resources through the shipped adapters and custom providers. Test selective packs, the
  complete offline bundle and asynchronous preparation. Verify selected bundles contain every
  required resource and exclude unrelated dictionaries, including shared-data deduplication.
- **Benchmarks** (kotlinx-benchmark), published in CI and the README:
  - converting 1,000 characters with dictionaries loaded: target ≤ 30 ms p95 on a mid-range 2022 Android
    phone and on Wasm in a desktop browser (~10 ms p50);
  - first conversion including lazy dictionary load from bundled files: ≤ 120 ms p95 on that phone;
  - throughput on 1 MB text on JVM, compared with upstream native.
- **Size acceptance and regression checks**, introduced alongside the first consumer fixtures:
  - Measure engine-only, one common config, multiple configs and the complete offline bundle.
  - Report Maven dependency downloads and incremental release application download size against
    otherwise equivalent fixtures without OpenCC. Record raw and compressed bytes, startup time and
    peak memory; separate code, data and decoder contributions.
  - Establish reproducible measured baselines and checked-in CI regression budgets before release;
    do not invent byte targets without measurements. Explain the compression method and measurement
    environment, including device/architecture and browser transport compression.
  - Evaluate Android shrinking, JS/Wasm production builds and Native size options in consumer
    fixtures. Accept optimizations only when conformance still passes and measured total size improves
    within the startup/performance requirements. Dependency artifact size alone is insufficient.

## Packaging and release

- Publish to Maven Central under a neutral group of your choosing (e.g. `io.github.<owner>.opencc`):
  the documented runtime, platform, dictionary-tooling, data-pack, phrase-extraction, CLI, detection
  and aggregate modules. Signed artifacts with sources and KDoc. Also publish CLI binaries as GitHub
  release assets.
- Publish standard KMP root/target variants with verified dependency resolution. Keep data and
  tooling out of core's dependency graph; publish selective data packs without pulling the full bundle.
- Use focused Swift/JS exports and shrinking-compatible APIs/dependencies. Keep the complete Kotlin
  surface available; avoid broad keep rules, reflection-based discovery and eager implementation
  registries that retain unused code. Verify production JS dead-code elimination and benchmark Native
  size options such as `smallBinary` with the pinned toolchain; experimental options require measured
  conformance, size and performance validation.
- Semantic versioning. The upstream OpenCC version is recorded in metadata and release notes.
- GitHub Actions:
  - on PR: build all targets; run tests on JVM, iOS, native and Wasm/JS; check data reproducibility
    and binary compatibility (binary-compatibility-validator); check consumer resource packaging,
    data dependency closures, dependency graphs and size budgets; lint;
  - on tag: publish.
- Repo hygiene: README (usage examples for every API, upstream → Kotlin mapping table, configs list,
  size profiles/budgets, module/data selection, resource delivery, API units/ownership, performance,
  data provenance, upgrade process), LICENSE (Apache-2.0), NOTICE (with
  upstream's), CONTRIBUTING, CODE_OF_CONDUCT, SECURITY.md, CHANGELOG, issue/PR templates, Renovate or
  Dependabot.

## Milestones

1. Skeleton with all modules and targets compiling; pin `ver.1.4.2` with exact commit/archive hash.
   Audit upstream APIs/configs/tools and document Kotlin mappings, platform boundaries and extension
   compatibility. Establish resource delivery and published-artifact Android/iOS/JS/Wasm consumer
   fixtures with initial size measurements and dependency-graph checks.
2. `.txt` dictionaries, compatible config parsing, normalization, max-match segmentation and conversion
   chains/group matching; upstream test cases passing for all configs using text dictionaries.
   Lock equivalent string/encoded-input, buffer, error and ownership contracts with conformance tests.
3. `.ocd2` (marisa) and `.ocd` (darts) readers and writers, with round-trip tests against upstream.
4. `opencc-data`: reproducible generation of all upstream dictionaries, including generated and reverse
   ones. Generate dependency manifests, selective packs/bundles and the complete offline aggregate;
   verify closure, deduplication and resource delivery. Establish measured size baselines and budgets,
   and compare storage/compression choices with startup/memory costs.
5. `SimpleConverter`, the C-API-style facade, streaming/inspection, dictionary tooling, phrase
   extraction, CLI tools, asynchronous preparation and comprehensive differential/concurrency tests.
6. Extensions: `convertWithMap` and `opencc-detect`.
7. Final performance/size benchmarks and consumer shrinking checks, documentation and the first Maven
   Central release. All features, format interoperability and complete/offline availability must pass
   conformance alongside the size budgets.
