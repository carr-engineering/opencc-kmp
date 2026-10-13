"""Reviewed Kotlin operation mappings; native ownership/optimization helpers are explicit."""
UTF8 = {
 'SkipUtf8Bom':'UTF8Util.skipBom(ByteArray)', 'NextCharLengthNoException':'UTF8Util.nextByteLengthOrZero',
 'NextCharLength':'UTF8Util.nextByteLength', 'PrevCharLength':'endIndex - UTF8Util.previousByteIndex(bytes, endIndex)',
 'NextChar':'UTF8Util.nextByteIndex / nextIndex(String)', 'PrevChar':'UTF8Util.previousByteIndex / previousIndex(String)',
 'kFirstIdeographicDescriptionOperator':'0x2FF0', 'kLastIdeographicDescriptionOperator':'0x2FFF',
 'IdeographicDescriptionOperatorArity':'UTF8Util.ideographicDescriptionOperatorArity',
 'NextIdeographicDescriptionSequenceLength':'UTF8Util.ideographicDescriptionByteEnd - startIndex / ideographicDescriptionEnd(String)',
 'IsIncompleteIdeographicDescriptionSequencePrefix':'UTF8Util.isIncompleteIdeographicDescriptionBytePrefix / isIncompleteIdeographicDescriptionPrefix(String)',
 'IsVariationSelector':'UTF8Util.isVariationSelector', 'ContainsVariationSelector':'UTF8Util.containsVariationSelector(String/ByteArray)',
 'Length':'UTF8Util.encodedLength(ByteArray, mode = NullTerminated) / length(String)',
 'FindNextInline':'UTF8Util.findNextInline(ByteArray); String.indexOf plus CR/LF/NUL boundary',
 'IsLineEndingOrFileEnding':"char == '\\u0000' || char == '\\n' || char == '\\r'",
 'FromSubstr':'ByteArray.copyOfRange / String.substring (UTF16 boundaries)',
 'NotShorterThan':'first-NUL byte position >= requestedByteCount',
 'TruncateUTF8':'UTF8Util.truncateUtf8(ByteArray) / truncate(String, UTF8 byte budget)',
 'ReplaceAll':'String.replace(oldValue, newValue)', 'Join':'Iterable<String>.joinToString(separator)',
 'GetByteMap':'UTF8Util.byteMap(String/ByteArray); Kotlin result also includes final endpoint',
 'GetPlatformString':'String, platform resource adapters', 'U16ToU8':'UTF8Util.encode(String)', 'U8ToU16':'UTF8Util.decode(ByteArray)',
}
SLICE = {
 'UTF8StringSliceBase':'String / String.substring with half-open UTF16 ranges; ByteArray.copyOfRange for encoded data',
 'UTF8Length':'UTF8Util.length(String) / encodedLength(ByteArray)', 'ByteLength':'UTF8Util.byteLength(String) / ByteArray.size',
 'Left':'String.substring(0, UTF16 endpoint); scalar endpoint via UTF8Util.nextIndex',
 'Right':'String.substring(UTF16 endpoint); scalar endpoint via UTF8Util.previousIndex',
 'SubString':'String.substring(startIndex, endIndex)', 'ToString':'String / UTF8Util.decode(ByteArray)', 'CString':'String / UTF8Util.encode(String), owned result',
 'CommonPrefixLength':'UTF8Util.commonPrefixEnd, UTF16 endpoint; scalar count via length(prefix)',
 'MoveRight':'startIndex = UTF8Util.nextIndex(text, startIndex)', 'MoveLeft':'endIndex = UTF8Util.previousIndex(text, endIndex)',
 'ReverseCompare':'UTF8Util.reverseCompare', 'FindBytePosition':'String.indexOf (UTF16), UTF8Util.byteLength(prefix) when encoded offset is needed',
 'operator<':'UTF8Util.compare < 0', 'operator>':'UTF8Util.compare > 0', 'operator==':'String.equals',
 'operator!=':'!String.equals', 'operator()':'String.hashCode / Kotlin Map<String, …>', 'operator<<':'Appendable.append(String)',
}
FAMILY = {
 'BinaryDict':'BinaryDict.fromBytes / DictionaryWriters.binaryPayload / lexicon / keyMaxLength',
 'Config':'Config constructor / newFromString / newFromFile; ConfigLoadOptions',
 'ConfigBasedConverter':'ConfigBasedConverter(normalization, main) / convert / inspect / segmentation / conversionChain / normalization',
 'Conversion':'Conversion(dict) / convert / appendConverted / dict',
 'ConversionAmbiguities':'convertWithAmbiguities / AnnotatedConversion / AmbiguousSpan / AmbiguityStream (experimental)',
 'ConversionCandidates':'Converter.getAllConversions(String) / getAllConversionsUtf8(ByteArray) (experimental; owned discovery-ordered candidates)',
 'ConversionChain':'ConversionChain / convert / convertWithTrace / conversions; Appendable.append(chain.convert(segment))',
 'ConversionInspection':'ConversionInspectionStage / ConversionInspectionResult matching named properties',
 'Converter':'Converter.convert / inspect / segmentation / conversionChain; ConverterStream',
 'DartsDict':'DartsDict.fromBytes / DictionaryWriters.ocd / Dict lookup and lexicon',
 'Dict':'Dict.match / matchPrefix / matchAllPrefixes / keyMaxLength / lexicon / groupItems / matchPolicy; nullable DictEntry',
 'DictConverter':'DictionaryConverter.load / write / convert, DictionaryFormat, CLI opencc_dict',
 'DictEntry':'DictEntry(key, values) / key / defaultValue / values / values.size / key.length / toString',
 'DictGroup':'DictGroup / UnionDictGroup / groupItems / matchPolicy / Dict lookup',
 'Exception':'OpenCCException subclasses and Throwable.message; Kotlin-owned error data',
 'Lexicon':'Lexicon(entries) / entries / size / parse; immutable sorted snapshot',
 'MarisaDict':'MarisaDict.fromBytes / DictionaryWriters.ocd2 / Dict lookup and lexicon',
 'MaxMatchSegmentation':'MaxMatchSegmentation(dict) / segment / dict',
 'Optional':'Kotlin nullable types, == null, requireNotNull, null',
 'PhraseExtract':'PhraseExtract properties and same-named lowerCamelCase methods; fullText/wordMinLength/wordMaxLength/prefixSetLength/suffixSetLength filters',
 'PipelineConverter':'PipelineConverter(converters) / convert / inspect / segmentation / conversionChain',
 'PluginSegmentation':'SegmentationFactory registration, JiebaSegmentationFactory; host-owned resources/options',
 'PrefixMatch':'Dict.matchPrefix / matchPrefixUtf8; nullable DictEntry supplies key/defaultValue and lengths',
 'ResourceProvider':'ResourceProvider.read / resolve / getResource; Resource.name / bytes / size / cacheKey; FileResourceProvider / ZipResourceProvider',
 'JiebaSegmentation':'JiebaSegmentation.fromText / JiebaSegmentationFactory / segment / segmentUtf8; provider-owned resources',
 'Segmentation':'Segmentation.segment(String), segmentUtf8(ByteArray)',
 'Segments':'List<String> / List<ByteArray>; listOf, MutableList.add, get, size, iterator, joinToString, toList',
 'SerializableDict':'standardDictionaryReaders / DictionaryConverter.load / DictionaryWriters and platform file output',
 'SerializedValues':'SerializedValues.fromBytes / lexicon / keyMaxLength; DictionaryWriters.serializedValues',
 'SimpleConverter':'SimpleConverter / convert / convertPrefix / convertTo / convertUtf8 / convertUtf8To / inspect',
 'SingleStageConverter':'SingleStageConverter(segmentation, conversionChain) / Converter operations',
 'TextDict':'TextDict(Lexicon.parse(text)) / Dict operations / DictionaryConverter.write(format = Text)',
 'OpenCCPlugin':'SegmentationFactory.create and Segmentation.segment/segmentUtf8; typed options/resources/errors and Kotlin-owned results; native descriptor ABI is platform-bound',
 'opencc':'OpenCC.open / OpenCCHandle.convert, convertUtf8, convertTo, convertUtf8To, close / OpenCC.error; returned values owned by Kotlin',
}


def mapping(header, operation):
    stem = header.rsplit('/', 1)[-1].split('.')[0]
    symbol = operation['symbol']
    if symbol.startswith('~') or symbol == 'operator=':
        return 'Kotlin-managed lifetime and value/reference assignment; no manual destruction/copy ownership', 'ownership'
    if stem == 'UTF8Util': return UTF8[symbol], 'mapped'
    if stem == 'UTF8StringSlice': return SLICE[symbol], 'mapped'
    if symbol in ('SupportsFastPrefixMatch', 'MatchPrefixValue', 'MatchPrefixView', 'EnumerateKeys', 'IsLexiconReconstructed', 'LooksLikeLegacy64Field'):
        return 'Dict prefix/lexicon operations and format readers; native optimization state is not a client contract', 'implementation'
    specific = {
        'Key': 'DictEntry.key', 'KeyView': 'DictEntry.key', 'GetDefault': 'DictEntry.defaultValue',
        'GetDefaultView': 'DictEntry.defaultValue', 'Value': 'DictEntry.defaultValue', 'ValueView': 'DictEntry.defaultValue',
        'Values': 'DictEntry.values (copied ordered List<String>)', 'NumValues': 'DictEntry.numValues',
        'KeyLength': 'DictEntry.keyLength (UTF16); keyByteLength for encoded data',
        'UPtrLessThan': 'UTF8Util.compare(a.key, b.key) < 0',
        'GetLexicon': 'Dict.lexicon', 'KeyMaxLength': 'Dict.keyMaxLength (UTF16)',
        'GetDictGroupItems': 'Dict.groupItems (nullable List<Dict>)', 'GetDicts': 'DictGroup.groupItems',
        'GetMatchPolicy': 'Dict.matchPolicy', 'GetDict': 'dict property',
        'GetSegmentation': 'Converter.segmentation', 'GetConversionChain': 'Converter.conversionChain',
        'GetNormalizationConverter': 'ConfigBasedConverter.normalization', 'GetConversions': 'ConversionChain.conversions',
        'opencc_open': 'OpenCC.open(configName)', 'opencc_open_w': 'OpenCC.open(String); native Windows adapter uses UTF16',
        'opencc_close': 'OpenCCHandle.close()', 'opencc_convert_utf8': 'OpenCCHandle.convertUtf8 / convert(String)',
        'opencc_convert_utf8_to_buffer': 'OpenCCHandle.convertUtf8To / convertTo',
        'opencc_convert_utf8_free': 'Kotlin-managed returned String / ByteArray ownership, no free call',
        'opencc_error': 'OpenCC.error / lastError (context-scoped)',
        'Resolve': 'ResourceProvider.resolve; ResourceMetadataProvider.resolve for hosts exposing filesystem locations',
        'GetResource': 'ResourceProvider.getResource; ResourceMetadataProvider.getResource for host metadata',
        'Name': 'Resource.name', 'Data': 'Resource.bytes (defensive ByteArray copy)', 'Size': 'Resource.size',
        'CacheKey': 'Resource.cacheKey (opaque Any; provider-owned version identity)',
        'SkipUnmatchable': 'Dict.skipUnmatchable(String, UTF16 ranges) / skipUnmatchableUtf8(ByteArray); reusable immutable PrefixScan index',
        'AppendConvertedSegment': 'output.append(chain.convert(segment)); substringBefore(NUL) for native C-string overload',
        'ToString': 'toString() on the equivalent Kotlin entry/segments value',
    }
    if stem == 'SerializedValues':
        values_mapping = {
            'SerializedValues': 'DictionaryWriters.serializedValues(lexicon); values retained as Kotlin lists',
            'GetLexicon': 'Lexicon(values.values.map { DictEntry("", it) }) for decoded value-only storage; retain original Lexicon when constructing',
            'KeyMaxLength': '0, matching upstream value-only dictionary',
            'NewFromFile': 'SerializedValues.fromBytes(provider.read(name))',
            'NewFromBuffer': 'SerializedValues.fromBytes(bytes); bytesRead property',
            'SerializeToFile': 'DictionaryWriters.serializedValues(lexicon), platform output adapter',
        }
        return values_mapping[symbol], 'mapped'
    if stem == 'PhraseExtract':
        props = {'SetWordMinLength': 'wordMinLength', 'SetWordMaxLength': 'wordMaxLength',
                 'SetPrefixSetLength': 'prefixSetLength', 'SetSuffixSetLength': 'suffixSetLength',
                 'SetPreCalculationFilter': 'preCalculationFilter', 'SetPostCalculationFilter': 'postCalculationFilter'}
        return 'PhraseExtract.' + props.get(symbol, symbol[0].lower() + symbol[1:]), 'mapped'
    if symbol in specific: return specific[symbol], 'mapped' 
    if stem == 'Lexicon':
        equivalent = {
            'Lexicon': 'Lexicon(entries); copying uses Lexicon(existing.entries)',
            'Add': 'Lexicon(existing.entries + entry) / MutableList<DictEntry>.add during construction',
            'Sort': 'Lexicon.sorted() returning a new value', 'IsSorted': 'Lexicon.isSorted()', 'IsUnique': 'Lexicon.isUnique()',
            'At': 'Lexicon[index]', 'Length': 'Lexicon.size', 'begin': 'Lexicon.iterator()', 'end': 'Iterator.hasNext()',
            'ParseLexiconFromFile': 'Lexicon.parse(UTF8Util.decode(provider.read(name)))',
            'ParseLexiconFromBuffer': 'Lexicon.parse(UTF8Util.decode(bytes))',
        }
        return equivalent[symbol], 'mapped'
    if stem in ('Common' , 'Export', 'StreamWindow', 'Utf8SkipScan'):
        return 'Internal declarations or build/platform ABI configuration; Kotlin common engine implements the observable conversion contract', 'implementation'
    if stem not in FAMILY: raise ValueError('Unclassified public header ' + header)
    return FAMILY[stem], 'family-mapped'
