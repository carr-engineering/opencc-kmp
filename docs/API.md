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
| ResourceProvider buffer access | ResourceProvider.read / MemoryResourceProvider | ByteArray, host-owned lifetime; memory snapshots copy byte arrays |
| Exceptions | OpenCCException subclasses | Typed Kotlin errors; malformed UTF-16 has an explicit Kotlin error |
| opencc_open / convert / close / error | OpenCC.open → OpenCCHandle.convert / close; OpenCC.error | Null failure, context-scoped last error; automatic ownership; no manual string free |
| Platform-native wide strings / paths | String and platform resource adapters | No separate wchar_t representation required |

`prepareConfig` is a Kotlin extension: suspending host resource acquisition followed by synchronous
conversion. It requires no coroutine or network dependency in core. Low-level encoded C-string
terminator conventions require explicit host adaptation; the Kotlin text API does not truncate
strings merely because they contain a NUL.

## Remaining parity work

| Requirement | Current status |
| --- | --- |
| Full advisory config schema diagnostics | Basic root diagnostics implemented; recursive parity pending |
| Filesystem/environment/executable search paths and resource zip support | Platform adapters pending |
| `.ocd` / `.ocd2` writers/builders | Runtime readers implemented; deterministic writers and builders pending |
| Additional MARISA image layouts | Native little-endian images verified; big-endian/32-bit MARISA fixtures pending |
| Full upstream UTF8Util / UTF8StringSlice operation inventory | Conversion helpers implemented; remaining utility mappings pending |
| Dictionary builders / conversion tool / phrase extraction | Pending in separate tooling modules |
| Complete CLI options, diagnostics, exit codes | Pending; CLI currently a scaffold |
| Bundled Jieba segmentation extension | Common extension interface implemented; algorithm/data port pending |
| Experimental candidate lookup / ambiguity annotations | Pending, explicitly separate from stable upstream APIs |
| Segment map / optional script detection | Pending |
| Cache lifecycle and cross-load sharing | Per-load dictionary sharing implemented; owned cache implementations pending |

No row marked pending is claimed as implemented. The source inventory and this table are refined
with each milestone; conformance counts in PROGRESS.md describe exactly what was tested.
