# Upstream public operations and Kotlin equivalents

Pinned reference: OpenCC `ver.1.4.2`, commit `025f371dc76b598d77384fbdab90c937471844d8`.
This operation inventory complements [API.md](API.md), [ENCODED_API.md](ENCODED_API.md),
[RESOURCE_CONTRACTS.md](RESOURCE_CONTRACTS.md), [CONFIGURATION.md](CONFIGURATION.md),
[JIEBA.md](JIEBA.md), [CLI.md](CLI.md), and [PUBLICATION.md](PUBLICATION.md).

The compatibility requirement is preservation of upstream operations, features and observable
conversion behaviour. Kotlin types, ownership and offset units are intentional API choices.
Native pointers, STL views, mutable allocation and binary plugin descriptors are not replicated.

## Text and encoded utility contracts

`UTF8Util` extensions provide forward/backward scalar boundaries, UTF8 byte-budget truncation,
byte maps, scalar comparison from either end, IDS arity/completeness and variation-selector scans.
String endpoints are half-open UTF16 offsets. `byteMap` contains each scalar's encoded byte start
and a final endpoint; callers needing upstream's start-only map can drop the final element.
Ordinary String slicing, list iteration, joining, replacement, equality and hashing use Kotlin
standard operations. Use `nextIndex` / `previousIndex` when selecting scalar boundaries.

Encoded utilities preserve upstream lead-byte traversal, including widths 5 and 6 and masked
non-canonical sequences. These operations do not assert that their input is canonical UTF8.
`decode` and String operations require valid Unicode scalars. `encodedLength` defaults to first-NUL
termination; `LengthDelimited` includes NUL bytes as characters. Byte truncation preserves original
bytes. Invalid leads and truncated counted sequences throw `InvalidUTF8`; explicit bounded helpers
never move outside a Kotlin array. Native operations with invalid out-of-allocation pointers or
oversized character counts have no defined contract; Kotlin range checks replace those mechanics.
`containsVariationSelector(ByteArray)` skips invalid leads and stops at truncated characters,
matching the pinned scanner rather than imposing the strict decoder's validation.

`UTF8StringSliceBase` maps to String values plus explicit UTF16 ranges, or ByteArray ranges for
encoded content. Its native `CommonPrefixLength` returns zero when distinct pointers contain a
complete equal prefix, while identical pointers return its length. `commonPrefixEnd` gives a
value-based UTF16 endpoint consistently; pointer identity is expressly not an API compatibility
requirement. Native FNV hash values and architecture-dependent unsigned `npos` are replaced by
Kotlin `hashCode` and `indexOf`'s `-1`. UTF8 sort order is provided by `UTF8Util.compare`, because
plain UTF16 comparison differs for supplementary characters.

Windows wide strings map to Kotlin String. Encoding at filesystem/CLI boundaries is handled by
thin platform adapters. C `FILE*` operations map to provider byte input or tooling byte output;
no native stream ownership escapes into common APIs. Binary segmentation plugin loading maps to
registered `SegmentationFactory` implementations, with resources, options and typed errors. The
pinned native descriptor's creation/segment/free/destroy operations map to factory creation,
segmentation results and Kotlin-managed ownership. This preserves extension capabilities without
requiring dynamic native code loading on browsers or mobile targets.

## Prefix scan operation

`Dict.skipUnmatchable` returns a UTF16 distance for a half-open String range;
`skipUnmatchableUtf8` returns the corresponding byte distance without decoding. Both preserve
upstream's conservative candidate-first-character stop policy: a potential key start stops even
when the entire key would not match. BMP first characters use character granularity; supplementary
and legacy encoded characters use leading-byte filtering. All recognized IDS operators stop scans;
invalid or truncated encoded sequences stop without throwing. Bounded encoded scans include NUL.

Retain `PrefixScan(dict)` for repeated operations. It owns an immutable first-character index,
creates no global cache, and allocates no substrings/results while scanning. Convenience extensions
create that index per call. Native table/fast-path state and pointer-alias requirements map to
immutable Kotlin dictionary values; MARISA decoding currently materializes its packed lookup
snapshot at load, so a native lexicon-reconstruction flag has no Kotlin API counterpart.
`audit_prefix_scan.py` generates 2,790 native skip-distance expectations across nine dictionary
backends. `NativePrefixScanTest` ports the 21 upstream prefix/skip test cases, recording the native
reconstruction-state case as an implementation adaptation rather than asserting native storage.

## Scope and evidence

`python3 tools/audit_upstream.py` regenerates [upstream-inventory.json](upstream-inventory.json)
from the checksum-verified archive. The lexical scanner retains overload declarations, public
nested fields and all conditional platform branches, and excludes private/protected bodies and
internal namespaces. It includes the OpenCC Jieba wrapper; bundled third-party cppjieba headers
are implementation dependencies, not additional OpenCC client operations. This inventory is not
a C++ ABI parser or a claim that each operation has differential coverage. Header hashes and full
declarations remain in JSON so changes to the pinned reference can be reviewed.

`python3 tools/audit_utf8_utilities.py` regenerates 3,796 native utility expectations covering every
lead byte, truncated prefixes, legacy encodings, NULs, byte budgets, selector scans, and IDS depth
limits. `Utf8UtilitiesTest.nativeUtilityGoldenCases` checks those same expectations in common
code; the audit report records the generated fixture digest. Pointer-specific slice mechanics
are documented above rather than compared as native ABI objects.

Each entry below names its Kotlin equivalent. **Family mappings** deliberately name an equivalent
set of operations when inherited overloads or construction/serialization adapters span modules;
consult the linked contract documents for offset, error and optional-module requirements.
Native fast-prefix/support-table/reconstruction-state operations represent lookup optimizations;
Kotlin exposes equivalent lookup and dictionary data, not the native optimizer's storage state.

The same inventory records 273 native cases in 31 test files. It records 193 direct assertion ports, 80 assertion-equivalent reviews, and 0 cases still requiring review. Each reviewed entry names concrete test methods/files or a named process case. Generation validates evidence references; suite-level candidate paths alone never establish coverage. Kotlin range/ownership and platform integration adaptations are recorded per case. These statuses record source review; runtime execution evidence remains in PROGRESS.md and build reports.

## Source operation crosswalk

### `plugins/jieba/include/JiebaSegmentation.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `JiebaSegmentation::JiebaSegmentation` | JiebaSegmentation.fromText / JiebaSegmentationFactory / segment / segmentUtf8; provider-owned resources | family-mapped |
| `JiebaSegmentation::~JiebaSegmentation` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `JiebaSegmentation::Segment` | JiebaSegmentation.fromText / JiebaSegmentationFactory / segment / segmentUtf8; provider-owned resources | family-mapped |

### `src/BinaryDict.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `LooksLikeLegacy64Field` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `BinaryDict::BinaryDict` | BinaryDict.fromBytes / DictionaryWriters.binaryPayload / lexicon / keyMaxLength | family-mapped |
| `BinaryDict::~BinaryDict` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `BinaryDict::SerializeToFile` | BinaryDict.fromBytes / DictionaryWriters.binaryPayload / lexicon / keyMaxLength | family-mapped |
| `BinaryDict::NewFromFile` | BinaryDict.fromBytes / DictionaryWriters.binaryPayload / lexicon / keyMaxLength | family-mapped |
| `BinaryDict::NewFromBuffer` | BinaryDict.fromBytes / DictionaryWriters.binaryPayload / lexicon / keyMaxLength | family-mapped |
| `BinaryDict::GetLexicon` | Dict.lexicon | mapped |
| `BinaryDict::KeyMaxLength` | Dict.keyMaxLength (UTF16) | mapped |

### `src/Common.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `PACKAGE_DATA_DIRECTORY` | Internal declarations or build/platform ABI configuration; Kotlin common engine implements the observable conversion contract | implementation |
| `PACKAGE_SEGMENTATION_PLUGIN_DIRECTORY` | Internal declarations or build/platform ABI configuration; Kotlin common engine implements the observable conversion contract | implementation |

### `src/Config.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `ConfigLoadOptions::ConfigLoadOptions` | Config constructor / newFromString / newFromFile; ConfigLoadOptions | family-mapped |
| `ConfigLoadOptions::includeTofuRiskDictionaries` | Config constructor / newFromString / newFromFile; ConfigLoadOptions | family-mapped |
| `Config::Config` | Config constructor / newFromString / newFromFile; ConfigLoadOptions | family-mapped |
| `Config::~Config` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `Config::NewFromString` | Config constructor / newFromString / newFromFile; ConfigLoadOptions | family-mapped |
| `Config::NewFromFile` | Config constructor / newFromString / newFromFile; ConfigLoadOptions | family-mapped |

### `src/ConfigBasedConverter.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `ConfigBasedConverter::ConfigBasedConverter` | ConfigBasedConverter(normalization, main) / convert / inspect / segmentation / conversionChain / normalization | family-mapped |
| `ConfigBasedConverter::Convert` | ConfigBasedConverter(normalization, main) / convert / inspect / segmentation / conversionChain / normalization | family-mapped |
| `ConfigBasedConverter::Inspect` | ConfigBasedConverter(normalization, main) / convert / inspect / segmentation / conversionChain / normalization | family-mapped |
| `ConfigBasedConverter::GetSegmentation` | Converter.segmentation | mapped |
| `ConfigBasedConverter::GetConversionChain` | Converter.conversionChain | mapped |
| `ConfigBasedConverter::GetNormalizationConverter` | ConfigBasedConverter.normalization | mapped |

### `src/Conversion.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Conversion::Conversion` | Conversion(dict) / convert / appendConverted / dict | family-mapped |
| `Conversion::Convert` | Conversion(dict) / convert / appendConverted / dict | family-mapped |
| `Conversion::AppendConverted` | Conversion(dict) / convert / appendConverted / dict | family-mapped |
| `Conversion::GetDict` | dict property | mapped |

### `src/ConversionAmbiguities.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `AmbiguousSpan::outputOffset` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguousSpan::outputLength` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguousSpan::sourceIndex` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AnnotatedConversion::output` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AnnotatedConversion::sources` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AnnotatedConversion::ambiguities` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AnnotatedConversion::analyzed` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `ConvertWithAmbiguities` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::Chunk::output` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::Chunk::ambiguities` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::Chunk::newSources` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::Chunk::analyzed` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::AmbiguityStream` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::~AmbiguityStream` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `AmbiguityStream::ConvertChunk` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::Finish` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |
| `AmbiguityStream::SourceCount` | convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental) | family-mapped |

### `src/ConversionCandidates.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `GetAllConversions` | Converter.getAllConversions(String) / getAllConversionsUtf8(ByteArray) (experimental; owned discovery-ordered candidates) | family-mapped |

### `src/ConversionChain.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `ConversionChain::ConversionChain` | ConversionChain / convert / convertWithTrace / conversions; Appendable.append(chain.convert(segment)) | family-mapped |
| `ConversionChain::Convert` | ConversionChain / convert / convertWithTrace / conversions; Appendable.append(chain.convert(segment)) | family-mapped |
| `ConversionChain::AppendConvertedSegment` | output.append(chain.convert(segment)); substringBefore(NUL) for native C-string overload | mapped |
| `ConversionChain::ConvertWithTrace` | ConversionChain / convert / convertWithTrace / conversions; Appendable.append(chain.convert(segment)) | family-mapped |
| `ConversionChain::GetConversions` | ConversionChain.conversions | mapped |

### `src/ConversionInspection.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `ConversionInspectionStage::index` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |
| `ConversionInspectionStage::segments` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |
| `ConversionInspectionResult::input` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |
| `ConversionInspectionResult::segments` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |
| `ConversionInspectionResult::stages` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |
| `ConversionInspectionResult::pipelineStages` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |
| `ConversionInspectionResult::output` | ConversionInspectionStage / ConversionInspectionResult matching named properties | family-mapped |

### `src/Converter.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Converter::~Converter` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `Converter::Convert` | Converter.convert / inspect / segmentation / conversionChain; ConverterStream | family-mapped |
| `Converter::Inspect` | Converter.convert / inspect / segmentation / conversionChain; ConverterStream | family-mapped |
| `Converter::GetSegmentation` | Converter.segmentation | mapped |
| `Converter::GetConversionChain` | Converter.conversionChain | mapped |
| `ConverterStream::ConverterStream` | Converter.convert / inspect / segmentation / conversionChain; ConverterStream | family-mapped |
| `ConverterStream::ConvertChunk` | Converter.convert / inspect / segmentation / conversionChain; ConverterStream | family-mapped |
| `ConverterStream::Finish` | Converter.convert / inspect / segmentation / conversionChain; ConverterStream | family-mapped |

### `src/DartsDict.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `DartsDict::~DartsDict` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `DartsDict::KeyMaxLength` | Dict.keyMaxLength (UTF16) | mapped |
| `DartsDict::Match` | DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon | family-mapped |
| `DartsDict::MatchPrefix` | DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon | family-mapped |
| `DartsDict::GetLexicon` | Dict.lexicon | mapped |
| `DartsDict::SupportsFastPrefixMatch` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `DartsDict::MatchPrefixValue` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `DartsDict::SerializeToFile` | DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon | family-mapped |
| `DartsDict::NewFromDict` | DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon | family-mapped |
| `DartsDict::NewFromFile` | DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon | family-mapped |
| `DartsDict::NewFromBuffer` | DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon | family-mapped |

### `src/Dict.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `PrefixMatchView::matched` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `PrefixMatchView::keyLength` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `PrefixMatchView::key` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `PrefixMatchView::value` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `Dict::Match` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `Dict::MatchPrefix` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `Dict::MatchAllPrefixes` | Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry | family-mapped |
| `Dict::KeyMaxLength` | Dict.keyMaxLength (UTF16) | mapped |
| `Dict::GetLexicon` | Dict.lexicon | mapped |
| `Dict::GetDictGroupItems` | Dict.groupItems (nullable List<Dict>) | mapped |
| `Dict::GetMatchPolicy` | Dict.matchPolicy | mapped |
| `Dict::SupportsFastPrefixMatch` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `Dict::MatchPrefixValue` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `Dict::~Dict` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |

### `src/DictConverter.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `ConvertDictionary` | DictionaryConverter.load / write / convert, DictionaryFormat, CLI opencc_dict | family-mapped |

### `src/DictEntry.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `DictEntry::~DictEntry` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `DictEntry::KeyView` | DictEntry.key | mapped |
| `DictEntry::GetDefaultView` | DictEntry.defaultValue | mapped |
| `DictEntry::Key` | DictEntry.key | mapped |
| `DictEntry::Values` | DictEntry.values (copied ordered List<String>) | mapped |
| `DictEntry::GetDefault` | DictEntry.defaultValue | mapped |
| `DictEntry::NumValues` | DictEntry.numValues | mapped |
| `DictEntry::ToString` | toString() on the equivalent Kotlin entry/segments value | mapped |
| `DictEntry::KeyLength` | DictEntry.keyLength (UTF16); keyByteLength for encoded data | mapped |
| `DictEntry::operator<` | DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString | family-mapped |
| `DictEntry::operator==` | DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString | family-mapped |
| `DictEntry::UPtrLessThan` | UTF8Util.compare(a.key, b.key) < 0 | mapped |
| `NoValueDictEntry::NoValueDictEntry` | DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString | family-mapped |
| `NoValueDictEntry::~NoValueDictEntry` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `NoValueDictEntry::Key` | DictEntry.key | mapped |
| `NoValueDictEntry::KeyView` | DictEntry.key | mapped |
| `NoValueDictEntry::GetDefault` | DictEntry.defaultValue | mapped |
| `NoValueDictEntry::GetDefaultView` | DictEntry.defaultValue | mapped |
| `NoValueDictEntry::Values` | DictEntry.values (copied ordered List<String>) | mapped |
| `NoValueDictEntry::NumValues` | DictEntry.numValues | mapped |
| `NoValueDictEntry::ToString` | toString() on the equivalent Kotlin entry/segments value | mapped |
| `SingleValueDictEntry::Value` | DictEntry.defaultValue | mapped |
| `SingleValueDictEntry::ValueView` | DictEntry.defaultValue | mapped |
| `SingleValueDictEntry::GetDefaultView` | DictEntry.defaultValue | mapped |
| `SingleValueDictEntry::Values` | DictEntry.values (copied ordered List<String>) | mapped |
| `SingleValueDictEntry::NumValues` | DictEntry.numValues | mapped |
| `SingleValueDictEntry::ToString` | toString() on the equivalent Kotlin entry/segments value | mapped |
| `StrSingleValueDictEntry::StrSingleValueDictEntry` | DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString | family-mapped |
| `StrSingleValueDictEntry::~StrSingleValueDictEntry` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `StrSingleValueDictEntry::Key` | DictEntry.key | mapped |
| `StrSingleValueDictEntry::KeyView` | DictEntry.key | mapped |
| `StrSingleValueDictEntry::Value` | DictEntry.defaultValue | mapped |
| `StrSingleValueDictEntry::GetDefault` | DictEntry.defaultValue | mapped |
| `StrSingleValueDictEntry::ValueView` | DictEntry.defaultValue | mapped |
| `MultiValueDictEntry::ToString` | toString() on the equivalent Kotlin entry/segments value | mapped |
| `StrMultiValueDictEntry::StrMultiValueDictEntry` | DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString | family-mapped |
| `StrMultiValueDictEntry::~StrMultiValueDictEntry` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `StrMultiValueDictEntry::Key` | DictEntry.key | mapped |
| `StrMultiValueDictEntry::KeyView` | DictEntry.key | mapped |
| `StrMultiValueDictEntry::GetDefault` | DictEntry.defaultValue | mapped |
| `StrMultiValueDictEntry::GetDefaultView` | DictEntry.defaultValue | mapped |
| `StrMultiValueDictEntry::NumValues` | DictEntry.numValues | mapped |
| `StrMultiValueDictEntry::Values` | DictEntry.values (copied ordered List<String>) | mapped |
| `DictEntryFactory::New` | DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString | family-mapped |

### `src/DictGroup.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `DictGroup::DictGroup` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |
| `DictGroup::NewFromDict` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |
| `DictGroup::~DictGroup` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `DictGroup::KeyMaxLength` | Dict.keyMaxLength (UTF16) | mapped |
| `DictGroup::Match` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |
| `DictGroup::MatchPrefix` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |
| `DictGroup::MatchAllPrefixes` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |
| `DictGroup::GetLexicon` | Dict.lexicon | mapped |
| `DictGroup::GetDictGroupItems` | Dict.groupItems (nullable List<Dict>) | mapped |
| `DictGroup::GetDicts` | DictGroup.groupItems | mapped |
| `DictGroup::GetMatchPolicy` | Dict.matchPolicy | mapped |
| `UnionDictGroup::UnionDictGroup` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |
| `UnionDictGroup::MatchPrefix` | DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup | family-mapped |

### `src/Exception.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Exception::Exception` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `Exception::~Exception` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `Exception::what` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `FileNotFound::FileNotFound` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `FileNotWritable::FileNotWritable` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `InvalidFormat::InvalidFormat` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `InvalidTextDictionary::InvalidTextDictionary` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `InvalidUTF8::InvalidUTF8` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |
| `ShouldNotBeHere::ShouldNotBeHere` | OpenCCException subclasses and Throwable.message; Kotlin-owned error data | family-mapped |

### `src/Lexicon.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Lexicon::Lexicon` | Lexicon(entries); copying uses Lexicon(existing.entries) | mapped |
| `Lexicon::operator=` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `Lexicon::Add` | Lexicon(existing.entries + entry) / MutableList<DictEntry>.add during construction | mapped |
| `Lexicon::Sort` | Lexicon.sorted() returning a new value | mapped |
| `Lexicon::IsSorted` | Lexicon.isSorted() | mapped |
| `Lexicon::IsUnique` | Lexicon.isUnique() | mapped |
| `Lexicon::At` | Lexicon[index] | mapped |
| `Lexicon::Length` | Lexicon.size | mapped |
| `Lexicon::begin` | Lexicon.iterator() | mapped |
| `Lexicon::end` | Iterator.hasNext() | mapped |
| `Lexicon::ParseLexiconFromFile` | Lexicon.parse(UTF8Util.decode(provider.read(name))) | mapped |
| `Lexicon::ParseLexiconFromBuffer` | Lexicon.parse(UTF8Util.decode(bytes)) | mapped |

### `src/MarisaDict.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `MarisaDict::~MarisaDict` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `MarisaDict::KeyMaxLength` | Dict.keyMaxLength (UTF16) | mapped |
| `MarisaDict::Match` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::MatchPrefix` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::MatchAllPrefixes` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::GetLexicon` | Dict.lexicon | mapped |
| `MarisaDict::SupportsFastPrefixMatch` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `MarisaDict::MatchPrefixValue` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `MarisaDict::EnumerateKeys` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `MarisaDict::SerializeToFile` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::NewFromDict` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::NewFromFile` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::NewFromBuffer` | MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon | family-mapped |
| `MarisaDict::IsLexiconReconstructed` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |

### `src/MaxMatchSegmentation.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `MaxMatchSegmentation::MaxMatchSegmentation` | MaxMatchSegmentation(dict) / segment / dict | family-mapped |
| `MaxMatchSegmentation::~MaxMatchSegmentation` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `MaxMatchSegmentation::Segment` | MaxMatchSegmentation(dict) / segment / dict | family-mapped |
| `MaxMatchSegmentation::GetDict` | dict property | mapped |

### `src/Optional.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Optional::Optional` | Kotlin nullable types, == null, requireNotNull, null | family-mapped |
| `Optional::IsNull` | Kotlin nullable types, == null, requireNotNull, null | family-mapped |
| `Optional::Get` | Kotlin nullable types, == null, requireNotNull, null | family-mapped |
| `Optional::Null` | Kotlin nullable types, == null, requireNotNull, null | family-mapped |

### `src/PhraseExtract.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `PhraseExtract::PhraseExtract` | PhraseExtract.phraseExtract | mapped |
| `PhraseExtract::~PhraseExtract` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `PhraseExtract::Extract` | PhraseExtract.extract | mapped |
| `PhraseExtract::SetFullText` | PhraseExtract.setFullText | mapped |
| `PhraseExtract::SetWordMinLength` | PhraseExtract.wordMinLength | mapped |
| `PhraseExtract::SetWordMaxLength` | PhraseExtract.wordMaxLength | mapped |
| `PhraseExtract::SetPrefixSetLength` | PhraseExtract.prefixSetLength | mapped |
| `PhraseExtract::SetSuffixSetLength` | PhraseExtract.suffixSetLength | mapped |
| `PhraseExtract::SetPreCalculationFilter` | PhraseExtract.preCalculationFilter | mapped |
| `PhraseExtract::SetPostCalculationFilter` | PhraseExtract.postCalculationFilter | mapped |
| `PhraseExtract::ReleaseSuffixes` | PhraseExtract.releaseSuffixes | mapped |
| `PhraseExtract::ReleasePrefixes` | PhraseExtract.releasePrefixes | mapped |
| `PhraseExtract::Words` | PhraseExtract.words | mapped |
| `PhraseExtract::WordCandidates` | PhraseExtract.wordCandidates | mapped |
| `PhraseExtract::Signals::frequency` | PhraseExtract.frequency | mapped |
| `PhraseExtract::Signals::cohesion` | PhraseExtract.cohesion | mapped |
| `PhraseExtract::Signals::suffixEntropy` | PhraseExtract.suffixEntropy | mapped |
| `PhraseExtract::Signals::prefixEntropy` | PhraseExtract.prefixEntropy | mapped |
| `PhraseExtract::Signal` | PhraseExtract.signal | mapped |
| `PhraseExtract::Cohesion` | PhraseExtract.cohesion | mapped |
| `PhraseExtract::Entropy` | PhraseExtract.entropy | mapped |
| `PhraseExtract::SuffixEntropy` | PhraseExtract.suffixEntropy | mapped |
| `PhraseExtract::PrefixEntropy` | PhraseExtract.prefixEntropy | mapped |
| `PhraseExtract::Frequency` | PhraseExtract.frequency | mapped |
| `PhraseExtract::Probability` | PhraseExtract.probability | mapped |
| `PhraseExtract::LogProbability` | PhraseExtract.logProbability | mapped |
| `PhraseExtract::Reset` | PhraseExtract.reset | mapped |
| `PhraseExtract::ExtractSuffixes` | PhraseExtract.extractSuffixes | mapped |
| `PhraseExtract::ExtractPrefixes` | PhraseExtract.extractPrefixes | mapped |
| `PhraseExtract::ExtractWordCandidates` | PhraseExtract.extractWordCandidates | mapped |
| `PhraseExtract::CalculateFrequency` | PhraseExtract.calculateFrequency | mapped |
| `PhraseExtract::CalculateCohesions` | PhraseExtract.calculateCohesions | mapped |
| `PhraseExtract::CalculateSuffixEntropy` | PhraseExtract.calculateSuffixEntropy | mapped |
| `PhraseExtract::CalculatePrefixEntropy` | PhraseExtract.calculatePrefixEntropy | mapped |
| `PhraseExtract::SelectWords` | PhraseExtract.selectWords | mapped |
| `PhraseExtract::DefaultPreCalculationFilter` | PhraseExtract.defaultPreCalculationFilter | mapped |
| `PhraseExtract::DefaultPostCalculationFilter` | PhraseExtract.defaultPostCalculationFilter | mapped |

### `src/PipelineConverter.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `PipelineConverter::PipelineConverter` | PipelineConverter(converters) / convert / inspect / segmentation / conversionChain | family-mapped |
| `PipelineConverter::Convert` | PipelineConverter(converters) / convert / inspect / segmentation / conversionChain | family-mapped |
| `PipelineConverter::Inspect` | PipelineConverter(converters) / convert / inspect / segmentation / conversionChain | family-mapped |
| `PipelineConverter::GetSegmentation` | Converter.segmentation | mapped |
| `PipelineConverter::GetConversionChain` | Converter.conversionChain | mapped |

### `src/PluginSegmentation.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `CreatePluginSegmentation` | SegmentationFactory registration, JiebaSegmentationFactory; host-owned resources/options | family-mapped |

### `src/PrefixMatch.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `PrefixMatch::Match::matched` | Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths | family-mapped |
| `PrefixMatch::Match::keyLength` | Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths | family-mapped |
| `PrefixMatch::Match::key` | Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths | family-mapped |
| `PrefixMatch::Match::value` | Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths | family-mapped |
| `PrefixMatch::PrefixMatch` | Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths | family-mapped |
| `PrefixMatch::~PrefixMatch` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `PrefixMatch::MatchPrefix` | Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths | family-mapped |
| `PrefixMatch::MatchPrefixView` | Dict prefix/lexicon operations and format readers; native optimization state is not a client contract | implementation |
| `PrefixMatch::SkipUnmatchable` | Dict.skipUnmatchable(String, UTF16 ranges) / skipUnmatchableUtf8(ByteArray); reusable immutable PrefixScan index | mapped |

### `src/ResourceProvider.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `ResourceProvider::Resource::Resource` | ResourceProvider.read / resolve / getResource; Resource.name / bytes / size / cacheKey; FileResourceProvider / ZipResourceProvider | family-mapped |
| `ResourceProvider::Resource::Name` | Resource.name | mapped |
| `ResourceProvider::Resource::Data` | Resource.bytes (defensive ByteArray copy) | mapped |
| `ResourceProvider::Resource::Size` | Resource.size | mapped |
| `ResourceProvider::Resource::CacheKey` | Resource.cacheKey (opaque Any; provider-owned version identity) | mapped |
| `ResourceProvider::~ResourceProvider` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `ResourceProvider::Resolve` | ResourceProvider.resolve; ResourceMetadataProvider.resolve for hosts exposing filesystem locations | mapped |
| `ResourceProvider::GetResource` | ResourceProvider.getResource; ResourceMetadataProvider.getResource for host metadata | mapped |
| `FilesystemResourceProvider::FilesystemResourceProvider` | ResourceProvider.read / resolve / getResource; Resource.name / bytes / size / cacheKey; FileResourceProvider / ZipResourceProvider | family-mapped |
| `FilesystemResourceProvider::Resolve` | ResourceProvider.resolve; ResourceMetadataProvider.resolve for hosts exposing filesystem locations | mapped |
| `ZipResourceProvider::ZipResourceProvider` | ResourceProvider.read / resolve / getResource; Resource.name / bytes / size / cacheKey; FileResourceProvider / ZipResourceProvider | family-mapped |
| `ZipResourceProvider::~ZipResourceProvider` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `ZipResourceProvider::operator=` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `ZipResourceProvider::Resolve` | ResourceProvider.resolve; ResourceMetadataProvider.resolve for hosts exposing filesystem locations | mapped |
| `ZipResourceProvider::GetResource` | ResourceProvider.getResource; ResourceMetadataProvider.getResource for host metadata | mapped |

### `src/Segmentation.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Segmentation::~Segmentation` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `Segmentation::Segment` | Segmentation.segment(String), segmentUtf8(ByteArray) | family-mapped |

### `src/Segments.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `Segments::Segments` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::AddSegment` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::iterator::iterator` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::iterator::operator++` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::iterator::operator==` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::iterator::operator!=` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::iterator::operator*` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::At` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::Length` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::begin` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::end` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |
| `Segments::ToString` | toString() on the equivalent Kotlin entry/segments value | mapped |
| `Segments::ToVector` | List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList | family-mapped |

### `src/SerializableDict.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `OpenSerializableFileUtf8` | standardDictionaryReaders / DictionaryConverter.load / DictionaryWriters and platform file output | family-mapped |
| `SerializableDict::SerializeToFile` | standardDictionaryReaders / DictionaryConverter.load / DictionaryWriters and platform file output | family-mapped |
| `SerializableDict::TryLoadFromFile` | standardDictionaryReaders / DictionaryConverter.load / DictionaryWriters and platform file output | family-mapped |
| `SerializableDict::NewFromFile` | standardDictionaryReaders / DictionaryConverter.load / DictionaryWriters and platform file output | family-mapped |

### `src/SerializedValues.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `SerializedValues::SerializedValues` | DictionaryWriters.serializedValues(lexicon); values retained as Kotlin lists | mapped |
| `SerializedValues::~SerializedValues` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `SerializedValues::SerializeToFile` | DictionaryWriters.serializedValues(lexicon), platform output adapter | mapped |
| `SerializedValues::NewFromFile` | SerializedValues.fromBytes(provider.read(name)) | mapped |
| `SerializedValues::NewFromBuffer` | SerializedValues.fromBytes(bytes); bytesRead property | mapped |
| `SerializedValues::GetLexicon` | Lexicon(values.values.map { DictEntry("", it) }) for decoded value-only storage; retain original Lexicon when constructing | mapped |
| `SerializedValues::KeyMaxLength` | 0, matching upstream value-only dictionary | mapped |

### `src/SimpleConverter.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `SimpleConverter::SimpleConverter` | SimpleConverter / convert / convertPrefix / convertTo / convertUtf8 / convertUtf8To / inspect | family-mapped |
| `SimpleConverter::~SimpleConverter` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `SimpleConverter::Convert` | SimpleConverter / convert / convertPrefix / convertTo / convertUtf8 / convertUtf8To / inspect | family-mapped |
| `SimpleConverter::Inspect` | SimpleConverter / convert / convertPrefix / convertTo / convertUtf8 / convertUtf8To / inspect | family-mapped |

### `src/SingleStageConverter.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `SingleStageConverter::SingleStageConverter` | SingleStageConverter(segmentation, conversionChain) / Converter operations | family-mapped |
| `SingleStageConverter::Convert` | SingleStageConverter(segmentation, conversionChain) / Converter operations | family-mapped |
| `SingleStageConverter::Inspect` | SingleStageConverter(segmentation, conversionChain) / Converter operations | family-mapped |
| `SingleStageConverter::GetSegmentation` | Converter.segmentation | mapped |
| `SingleStageConverter::GetConversionChain` | Converter.conversionChain | mapped |

### `src/TextDict.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `TextDict::TextDict` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |
| `TextDict::~TextDict` | Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership | ownership |
| `TextDict::KeyMaxLength` | Dict.keyMaxLength (UTF16) | mapped |
| `TextDict::Match` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |
| `TextDict::GetLexicon` | Dict.lexicon | mapped |
| `TextDict::SerializeToFile` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |
| `TextDict::NewFromDict` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |
| `TextDict::NewFromFile` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |
| `TextDict::NewFromBuffer` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |
| `TextDict::NewFromSortedFile` | TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text) | family-mapped |

### `src/UTF8StringSlice.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `UTF8StringSliceBase::UTF8StringSliceBase` | String / String.substring with half-open UTF16 ranges; ByteArray.copyOfRange for encoded data | mapped |
| `UTF8StringSliceBase::UTF8Length` | UTF8Util.length(String) / encodedLength(ByteArray) | mapped |
| `UTF8StringSliceBase::ByteLength` | UTF8Util.byteLength(String) / ByteArray.size | mapped |
| `UTF8StringSliceBase::Left` | String.substring(0, UTF16 endpoint); scalar endpoint via UTF8Util.nextIndex | mapped |
| `UTF8StringSliceBase::Right` | String.substring(UTF16 endpoint); scalar endpoint via UTF8Util.previousIndex | mapped |
| `UTF8StringSliceBase::SubString` | String.substring(startIndex, endIndex) | mapped |
| `UTF8StringSliceBase::ToString` | String / UTF8Util.decode(ByteArray) | mapped |
| `UTF8StringSliceBase::CString` | String / UTF8Util.encode(String), owned result | mapped |
| `UTF8StringSliceBase::CommonPrefixLength` | UTF8Util.commonPrefixEnd, UTF16 endpoint; scalar count via length(prefix) | mapped |
| `UTF8StringSliceBase::MoveRight` | startIndex = UTF8Util.nextIndex(text, startIndex) | mapped |
| `UTF8StringSliceBase::MoveLeft` | endIndex = UTF8Util.previousIndex(text, endIndex) | mapped |
| `UTF8StringSliceBase::ReverseCompare` | UTF8Util.reverseCompare | mapped |
| `UTF8StringSliceBase::FindBytePosition` | String.indexOf (UTF16), UTF8Util.byteLength(prefix) when encoded offset is needed | mapped |
| `UTF8StringSliceBase::operator<` | UTF8Util.compare < 0 | mapped |
| `UTF8StringSliceBase::operator>` | UTF8Util.compare > 0 | mapped |
| `UTF8StringSliceBase::operator==` | String.equals | mapped |
| `UTF8StringSliceBase::operator!=` | !String.equals | mapped |
| `UTF8StringSliceBase::Hasher::operator()` | String.hashCode / Kotlin Map<String, …> | mapped |
| `operator<<` | Appendable.append(String) | mapped |

### `src/UTF8Util.hpp`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `UTF8Util::SkipUtf8Bom` | UTF8Util.skipBom(ByteArray) | mapped |
| `UTF8Util::NextCharLengthNoException` | UTF8Util.nextByteLengthOrZero | mapped |
| `UTF8Util::NextCharLength` | UTF8Util.nextByteLength | mapped |
| `UTF8Util::PrevCharLength` | endIndex - UTF8Util.previousByteIndex(bytes, endIndex) | mapped |
| `UTF8Util::NextChar` | UTF8Util.nextByteIndex / nextIndex(String) | mapped |
| `UTF8Util::PrevChar` | UTF8Util.previousByteIndex / previousIndex(String) | mapped |
| `UTF8Util::kFirstIdeographicDescriptionOperator` | 0x2FF0 | mapped |
| `UTF8Util::kLastIdeographicDescriptionOperator` | 0x2FFF | mapped |
| `UTF8Util::IdeographicDescriptionOperatorArity` | UTF8Util.ideographicDescriptionOperatorArity | mapped |
| `UTF8Util::NextIdeographicDescriptionSequenceLength` | UTF8Util.ideographicDescriptionByteEnd - startIndex / ideographicDescriptionEnd(String) | mapped |
| `UTF8Util::IsIncompleteIdeographicDescriptionSequencePrefix` | UTF8Util.isIncompleteIdeographicDescriptionBytePrefix / isIncompleteIdeographicDescriptionPrefix(String) | mapped |
| `UTF8Util::IsVariationSelector` | UTF8Util.isVariationSelector | mapped |
| `UTF8Util::ContainsVariationSelector` | UTF8Util.containsVariationSelector(String/ByteArray) | mapped |
| `UTF8Util::Length` | UTF8Util.encodedLength(ByteArray, mode = NullTerminated) / length(String) | mapped |
| `UTF8Util::FindNextInline` | UTF8Util.findNextInline(ByteArray); String.indexOf plus CR/LF/NUL boundary | mapped |
| `UTF8Util::IsLineEndingOrFileEnding` | char == '\u0000' \|\| char == '\n' \|\| char == '\r' | mapped |
| `UTF8Util::FromSubstr` | ByteArray.copyOfRange / String.substring (UTF16 boundaries) | mapped |
| `UTF8Util::NotShorterThan` | first-NUL byte position >= requestedByteCount | mapped |
| `UTF8Util::TruncateUTF8` | UTF8Util.truncateUtf8(ByteArray) / truncate(String, UTF8 byte budget) | mapped |
| `UTF8Util::ReplaceAll` | String.replace(oldValue, newValue) | mapped |
| `UTF8Util::Join` | Iterable<String>.joinToString(separator) | mapped |
| `UTF8Util::GetByteMap` | UTF8Util.byteMap(String/ByteArray); Kotlin result also includes final endpoint | mapped |
| `UTF8Util::GetPlatformString` | String, platform resource adapters | mapped |
| `UTF8Util::U16ToU8` | UTF8Util.encode(String) | mapped |
| `UTF8Util::U8ToU16` | UTF8Util.decode(ByteArray) | mapped |

### `src/opencc.h`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `opencc_open` | OpenCC.open(configName) | mapped |
| `opencc_open_w` | OpenCC.open(String); native Windows adapter uses UTF16 | mapped |
| `opencc_close` | OpenCCHandle.close() | mapped |
| `opencc_convert_utf8_to_buffer` | OpenCCHandle.convertUtf8To / convertTo | mapped |
| `opencc_convert_utf8` | OpenCCHandle.convertUtf8 / convert(String) | mapped |
| `opencc_convert_utf8_free` | Kotlin-managed returned String / ByteArray ownership, no free call | mapped |
| `opencc_error` | OpenCC.error / lastError (context-scoped) | mapped |

### `src/plugin/OpenCCPlugin.h`

| Upstream operation | Kotlin equivalent | Mapping |
| --- | --- | --- |
| `opencc_kv_pair_t::struct_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_kv_pair_t::key` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_kv_pair_t::value` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_error_t::struct_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_error_t::code` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_error_t::message` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segment_length_array_t::struct_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segment_length_array_t::codepoint_lengths` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segment_length_array_t::segment_count` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_create_args_t::struct_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_create_args_t::config` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_create_args_t::config_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_create_args_t::out` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_create_args_t::error` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_segment_args_t::struct_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_segment_args_t::handle` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_segment_args_t::utf8_text` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_segment_args_t::segment_lengths` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_segment_args_t::error` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::struct_size` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::abi_major` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::abi_minor` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::plugin_name` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::segmentation_type` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::create` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::segment` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::free_segment_lengths` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::destroy` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_segmentation_plugin_v2::free_error` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |
| `opencc_get_segmentation_plugin_v2` | SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound | family-mapped |

