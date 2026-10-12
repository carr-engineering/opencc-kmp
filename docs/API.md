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
| Encoded input / explicit length conversion | convertUtf8(ByteArray, startIndex, endIndex) | Half-open UTF-8 byte range; no implicit NUL truncation; malformed encoding raises InvalidUTF8 |
| Buffer output | convertTo(String, Appendable/CharArray), convertUtf8To | Counts UTF-16 units for text, bytes for encoded output; insufficient capacity fails before mutation |
| Converter::Inspect | inspect / ConversionInspectionResult | Initial segments, indexed stage outputs, recursive pipeline trace |
| Converter accessors | segmentation / conversionChain | Config normalization delegates accessors to the main converter |
| ConverterStream::ConvertChunk / Finish | ConverterStream.convertChunk / finish | Caller-owned session; default keep-tail 16 code points; final input flushes in one conversion |
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
| ResourceProvider buffer access | ResourceProvider.read / MemoryResourceProvider | ByteArray, host-owned lifetime; memory snapshots copy byte arrays |
| Exceptions | OpenCCException subclasses | Typed Kotlin errors; malformed UTF-16 has an explicit Kotlin error |
| opencc_open / convert / close / error | OpenCC.open → OpenCCHandle.convert / close; OpenCC.error | Null failure, context-scoped last error; automatic ownership; no manual string free |
| Platform-native wide strings / paths | String and platform resource adapters | No separate wchar_t representation required |
| Filesystem/provider-backed resources | FileResourceProvider / ClasspathResourceProvider / AndroidAssetResourceProvider / AppleBundleResourceProvider | Platform I/O stays outside core; Windows paths use UTF-16 at the boundary |

`prepareConfig` is a Kotlin extension: suspending host resource acquisition followed by synchronous
conversion. It requires no coroutine or network dependency in core. Low-level encoded C-string
terminator conventions require explicit host adaptation; the Kotlin text API does not truncate
strings merely because they contain a NUL.

## Remaining parity work

| Requirement | Current status |
| --- | --- |
| Full advisory config schema diagnostics | Basic root diagnostics implemented; recursive parity pending |
| Environment/executable search paths, browser/Node resource helpers and zip adapters | Basic filesystem/classpath/Android asset/Apple bundle providers implemented; remaining adapters pending |
| Full upstream UTF8Util / UTF8StringSlice operation inventory | Conversion helpers implemented; remaining utility mappings pending |
| Phrase extraction | Pending in its separate tooling module; dictionary format conversion implemented |
| Complete CLI options, diagnostics, exit codes | Pending; CLI currently a scaffold |
| Bundled Jieba segmentation extension | Common extension interface implemented; algorithm/data port pending |
| Experimental candidate lookup / ambiguity annotations | Pending, explicitly separate from stable upstream APIs |
| Segment map / optional script detection | Pending |
| Cache lifecycle and cross-load sharing | Per-load dictionary sharing implemented; owned cache implementations pending |

No row marked pending is claimed as implemented. The source inventory and this table are refined
with each milestone; conformance counts in PROGRESS.md describe exactly what was tested.

The complete data generator includes 16 built-in configs and 7 experimental Jieba configs/assets.
The latter remain unavailable for conversion until their segmentation factory is ported; data
presence is not a claim of algorithm conformance. Selective bundles preserve each selected config's
full resource closure, including optional dictionaries.
