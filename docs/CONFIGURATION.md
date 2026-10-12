# Configuration contracts

The loader uses the unchanged OpenCC 1.4.2 config structure: optional name/segmentation/normalization,
required conversion_chain, file/inline/group dictionaries, nested policies and risk-dictionary
options. Every bundled schema definition is implemented by a small specialized validator; no JSON
or general schema library is added to runtime downloads.

```kotlin
val loader = Config(provider, warningSink = { warning ->
    println("${warning.path}: ${warning.keyword} (${warning.schemaPath})")
})
val converter = loader.newFromFile("s2t.json", ConfigLoadOptions(includeTofuRiskDictionaries = false))
```

Schema validation is advisory. It emits only the first violation in document traversal order, with
the same document/schema JSON pointers and keyword as upstream. Required-property checks happen
when the containing object ends. Dictionaries and segmentation use the pinned anyOf alternatives;
a missing group policy warns but still defaults to short_circuit. Empty chains and groups remain
accepted by the semantic loader despite schema warnings. Unknown properties warn and are ignored
where the native loader ignores them. No warning changes accepted conversion behavior.

ConfigWarning carries path, message, schemaPath and keyword. The common engine exposes warnings
through a caller-supplied callback rather than writing to a platform stderr stream. The default
callback is absent, so unused advisory validation adds no load work and can shrink away; applications/
CLI adapters choose their logging policy. Callbacks must support
concurrent invocation when their loader is shared. Parse failures happen before schema diagnostics;
semantic failures can follow an advisory warning. Error messages use Kotlin/JSON terms string and
boolean in place of native std::string/bool. Typed error capabilities and failing properties remain
the same. Missing native binary plugins map to UnavailableSegmentationExtension; OS library paths
and binary ABI loading are documented platform boundaries, not engine dependencies.

Parsing accepts comments, trailing commas and duplicate object members. Required property lookup
uses the first member, while schema validation visits every member; duplicate inline keys are
rejected. JSON input ends at an actual NUL, including when loaded from a resource. Escaped NULs
remain in the parsed JSON model for schema checks, then config string properties and inline entry
keys/values use the native logical prefix at NUL. A UTF-8 BOM is not an accepted config prefix in
the pinned loader. Unicode escapes require four hex digits and valid supplementary pairs; lone
surrogates fail parsing. Numeric syntax and finite-double limits match the pinned parser, including
underflow acceptance. The old artificial nesting limit has been removed; deep accepted fixtures
are checked against native OpenCC. Resource encoding/malformed dictionary audits remain separate.

SegmentationFactory receives immutable SegmentationProperties, an ordinary Map interface for
convenient lookup plus orderedPairs for complete upstream option order and duplicates. Lookup
uses the first occurrence: __config_dir comes first, then resource pairs, then ordinary plugin
properties. Hosts can use orderedPairs when their equivalent extension has another policy. NUL
prefixes in names/values match native option extraction. Properties are validated before missing-
extension errors; no reflection or eager extension registry is required.

Sync and suspend resource loading drive the same ordered load session. Synchronous apps do not
start a coroutine; the suspend driver awaits only the session's resource requests. No parsing or
extension initialization is replayed after an awaited read. prepareConfig
fetches config bytes, then only dictionaries used by the selected options, once per type/name in
that load. Inline entries named file and ignored metadata are not interpreted as file references.
Risk dictionaries skipped by includeTofuRiskDictionaries=false are not acquired; segmentation
always includes its dictionaries. Native loading/error order is preserved across awaited reads.
Dictionary readers and conversion run synchronously after bytes arrive; core never performs
networking. Optional owned caches retain their async provider identity across preparations.

For a segmentation extension, resourcesToPrepare can refine declared provider names based on its
options or add other required names. Its synchronous create receives a copied memory provider of
those prepared resources. The default preparation list is the config's resources map resolved
relative to the config directory. This lets hosts preserve option-dependent resources without
fetching disabled assets. The complete/selected offline bundles still contain the full supported
resource closure; selective preparation and selective packaging are different choices.

Extensions with optional resources or fallback paths override suspend prepareResources instead.
The default implementation uses resourcesToPrepare and copies the acquired bytes into a memory
provider. The custom hook receives the host's AsyncResourceProvider; resource I/O remains outside
the engine and precedes synchronous create. The bundled Jieba factory uses this hook to retain
merged/text fallback and auxiliary-resource lookup without eagerly requiring every possible path.

The differential config corpus checks 4,952 cases through native string/file entry points and both
risk options: acceptance/output, first-warning pointers/keyword, error category and meaningful
semantic diagnostics. It includes all value kinds, dictionaries/groups, plugin fields, duplicate
members, NUL aliases, escapes/numbers, warning priority and deep JSON. Common tests additionally
exercise actually suspended reads, option selection, repeated-resource deduplication, load/error
order, plugin preparation and immutable ordered options on JVM/JS/Wasm/macOS/iOS.

Numeric tokens retain their original spelling. Their lexical grammar and range acceptance follow
the pinned RapidJSON default decimal path, including its staged unsigned integer, fraction and
exponent arithmetic. This is not simply a mathematical-finiteness check: `0e309` is rejected, and
an enormous integer mantissa can overflow before a negative exponent is applied. Tests cover
1,012 additional range-boundary cases. A common Kotlin checker avoids the general Kotlin/Native
floating parser and its regex dependency; no JSON capability is removed. The adapted algorithm's
MIT attribution is retained in the engine artifact.

```sh
python3 tools/audit_config.py
./gradlew -Popencc.targets=jvm :opencc-core:upstreamConformance
```
