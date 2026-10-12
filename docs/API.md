# Upstream to Kotlin mapping

Reference: OpenCC `ver.1.4.2`, commit `025f371dc76b598d77384fbdab90c937471844d8`.
`upstream-inventory.json` records the verified release inputs. Public operations are mapped to
Kotlin capabilities; native pointers, allocation/free and C++ container types are not copied.

## Implemented runtime surface

| Upstream operations | Kotlin equivalent | Contract |
| --- | --- | --- |
| Config::NewFromString / NewFromFile | Config.newFromString / newFromFile | JSON, provider resources, options, relative logical names |
| ConfigLoadOptions::includeTofuRiskDictionaries | ConfigLoadOptions.includeTofuRiskDictionaries | Defaults to true; segmentation always includes its dictionaries |
| SimpleConverter constructors | SimpleConverter(configName, provider, options, readers) | Bare bundled config names map to `.json`; filesystem search is provider responsibility |
| Converter / SimpleConverter::Convert | convert(String), convert(String, startIndex, endIndex), convertPrefix | Ordinary text uses half-open UTF-16 ranges; supplementary pairs cannot be split |
| Encoded input / explicit length conversion | convertUtf8 / convertUtf8Prefix, EncodedInputMode | Half-open byte ranges; native bounded partial/non-canonical traversal; explicit-length or NUL-terminated input |
| Buffer output | convertTo(String, Appendable/CharArray), convertUtf8To | Counts UTF-16 units or bytes; byte buffer input defaults to NUL termination; insufficient capacity fails before mutation |
| Converter::Inspect | inspect / ConversionInspectionResult and inspectUtf8 / EncodedInspectionResult | Typed text/owned byte snapshots, numbered stages, recursive pipelines and native NUL inspection behavior |
| Converter accessors | segmentation / conversionChain | Config normalization delegates accessors to the main converter |
| PipelineConverter accessors | PipelineConverter.segmentation / conversionChain | Last child's segmentation; no single conversion chain |
| ConverterStream::ConvertChunk / Finish | ConverterStream and Utf8ConverterStream | Owned text/encoded sessions; default tail 16 characters; partial UTF-8/UTF-16 boundaries and final flush |
| Segmentation / MaxMatchSegmentation | Segmentation.segment / MaxMatchSegmentation | Segments preserve phrase boundaries; unmatched complete IDS remains atomic |
| Conversion::Convert / AppendConverted | Conversion.convert / appendConverted | One dictionary stage, ordered default candidates |
| ConversionChain::Convert / ConvertWithTrace | ConversionChain.convert / convertWithTrace | Stages convert each segment independently |
| Dict::Match / MatchPrefix / MatchAllPrefixes | Dict.match / matchPrefix / matchAllPrefixes | Longest prefix; all prefixes descending; group order breaks ties |
| Dict::KeyMaxLength / GetLexicon / group accessors | keyMaxLength / lexicon / groupItems / matchPolicy | Kotlin text lengths are UTF-16 units |
| DictEntry / Lexicon / TextDict | DictEntry / Lexicon / TextDict | Kotlin strings and copied candidate lists; UTF-8 scalar sorting; duplicate text keys rejected |
| DictGroup / UnionDictGroup | DictGroup / UnionDictGroup | ShortCircuit returns first child's match; Union chooses longest across children |
| DartsDict / BinaryDict readers | DartsDict.fromBytes / BinaryDict.fromBytes | Fixed uint32 and legacy uint64 fields; auto byte-order detection for `.ocd` |
| MarisaDict / SerializedValues readers | MarisaDict.fromBytes / SerializedValues.fromBytes | LOUDS links, nested tries, text/binary tails, ordered values; portable runtime lookup |
| Darts / BinaryDict / SerializedValues serialization | DictionaryWriters.ocd / binaryPayload / serializedValues | Pure Kotlin builders and writers in the separate tooling module; fixed-width output and explicit byte order |
| MarisaDict serialization | DictionaryWriters.ocd2 | Pure Kotlin nested LOUDS builder; deterministic upstream default layout and ordered candidate serialization |
| DictConverter | DictionaryConverter.load / write / convert, DictionaryFormat | All five upstream input formats; text, ocd and ocd2 output; cppjieba base/user metadata and override ordering |
| GetAllConversions (unstable upstream) | Converter.getAllConversions | Normalized word input; exact-match candidate branching, discovery-order deduplication and prefix fallback; requires experimental opt-in |
| ConvertWithAmbiguities / AmbiguityStream (unstable upstream) | Converter.convertWithAmbiguities / AmbiguityStream | Typed results with UTF-16 spans, normalized sources, global stream source indexes and analyzed status; requires experimental opt-in |
| PhraseExtract::Extract / step operations / Reset / release operations | PhraseExtract.extract / extractSuffixes / extractPrefixes / calculateFrequency / calculateCohesions / calculateSuffixEntropy / calculatePrefixEntropy / extractWordCandidates / selectWords / reset / releaseSuffixes / releasePrefixes | Separate phrase module; owned mutable session; call reset before reuse; reset retains length options and restores default filters |
| PhraseExtract setters / Words / WordCandidates / Signals / statistic accessors | Length/filter properties, words / wordCandidates / signal / frequency / probability / logProbability / cohesion / entropy / prefixEntropy / suffixEntropy | Unicode scalar length units, String candidates, immutable signal snapshots and copied lists; filter true excludes a candidate |
| ResourceProvider buffer access | ResourceProvider.read / MemoryResourceProvider | ByteArray, host-owned lifetime; memory snapshots copy byte arrays |
| Kotlin resource/cache extensions | DictionaryCache, ResourceDigest, VerifiedResourceProvider / VerifiedAsyncResourceProvider | Explicit cache ownership, content/version/format identity, concurrent initialization and trusted-manifest byte verification |
| Exceptions | OpenCCException subclasses | Typed Kotlin errors; malformed UTF-16 has an explicit Kotlin error |
| opencc_open / convert / close / error | OpenCC.open → OpenCCHandle.convert / close; OpenCC.error | Null failure, context-scoped last error; automatic ownership; no manual string free |
| Platform-native wide strings / paths | String and platform resource adapters | No separate wchar_t representation required |
| Filesystem/provider-backed resources | FileResourceProvider / ClasspathResourceProvider / AndroidAssetResourceProvider / AppleBundleResourceProvider | Platform I/O stays outside core; Windows paths use UTF-16 at the boundary |
| Browser/Node resource acquisition | BrowserResourceProvider / NodeFileResourceProvider | JS and Wasm adapters; suspend fetch preparation and synchronous local file loading; no networking in core |

`prepareConfig` is a Kotlin extension: suspending host resource acquisition followed by synchronous
conversion. It requires no coroutine or network dependency in core. Explicit byte modes replace
native C-string input conventions. Text and bytes retain upstream's config-dependent NUL behavior;
byte modes do not impose byte indexing on ordinary String operations.

## Remaining parity work

| Requirement | Current status |
| --- | --- |
| Full advisory config schema diagnostics | Basic root diagnostics implemented; recursive parity pending |
| Environment/executable search paths and runtime zip adapters | File/classpath/Android/Apple/browser/Node providers implemented; remaining adapters pending |
| Full upstream UTF8Util / UTF8StringSlice operation inventory | Conversion helpers implemented; remaining utility mappings pending |
| Complete CLI options, diagnostics, exit codes | Pending; CLI currently a scaffold |
| Bundled Jieba segmentation extension | Common extension interface implemented; algorithm/data port pending |

No row marked pending is claimed as implemented. The source inventory and this table are refined
with each milestone; conformance counts in PROGRESS.md describe exactly what was tested.

The complete data generator includes 16 built-in configs and 7 experimental Jieba configs/assets.
The latter remain unavailable for conversion until their segmentation factory is ported; data
presence is not a claim of algorithm conformance. Selective bundles preserve each selected config's
full resource closure, including optional dictionaries.

`convertWithMap` and the optional `ScriptDetector` are Kotlin extensions beyond upstream; their
contracts and selection examples live in [ALIGNMENT_AND_DETECTION.md](ALIGNMENT_AND_DETECTION.md).
Cache/concurrency, resource verification and shared dictionary lifetimes are specified in
[CACHES_AND_OWNERSHIP.md](CACHES_AND_OWNERSHIP.md).
The encoded-input differences and reproducible native probe are recorded in
[ENCODED_CONTRACT_AUDIT.md](ENCODED_CONTRACT_AUDIT.md).

Implemented byte/text, mode, inspection and streaming contracts are in [ENCODED_API.md](ENCODED_API.md).
