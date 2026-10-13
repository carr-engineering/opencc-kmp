"""Assertion-level correspondences reviewed against pinned native tests.

Different fixtures are recorded as reviewed-equivalent, not literal assertion ports.
Malformed-layout and optimization-state cases without direct evidence remain open.
"""
CASE_REVIEWS = {}


def review(suite, names, evidence, detail):
    for name in names:
        CASE_REVIEWS[(suite, name)] = {'port_review': 'reviewed-equivalent', 'evidence': evidence,
                                     'contract_adaptation': detail}


def proof(test, file): return {'test': test, 'file': file}


phrase_common = 'opencc-phrase-extract/src/commonTest/kotlin/io/github/opencckmp/phrase/PhraseExtractTest.kt'
for names, method in [
    (['ExtractSuffixes', 'ExtractPrefixes'], 'nativeSuffixAndPrefixSequences'),
    (['CalculateFrequency', 'ExtractWordCandidates'], 'nativeFrequenciesProbabilitiesAndCandidateOrdering'),
    (['CalculateCohesions', 'CalculateSuffixEntropy', 'CalculatePrefixEntropy'], 'nativeCohesionsAndBothEntropies'),
    (['SelectWords', 'Punctuation'], 'nativeFiltersSelectionPunctuationAndReset'),
]:
    review('PhraseExtractTest', names, [proof('PhraseExtractTest.' + method, phrase_common)],
           'Same native corpus, expected ordered slices/words and numeric constants; all native assertions present. Kotlin String ranges and immutable snapshots replace slices. Double comparisons use 1e-12 tolerance.')

reader_common = 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/BinaryReaderTest.kt'
reader_native = 'opencc-core/src/jvmTest/kotlin/io/github/opencckmp/UpstreamBinaryFormatTest.kt'
writer_common = 'opencc-dictionary-tools/src/commonTest/kotlin/io/github/opencckmp/tools/WriterTest.kt'
writer_native = 'opencc-dictionary-tools/src/jvmTest/kotlin/io/github/opencckmp/tools/UpstreamWriterTest.kt'
for suite in ['DartsDictTest', 'MarisaDictTest']:
    review(suite, ['DictTest', 'Deserialization', 'ExactMatch'], [
        proof('BinaryReaderTest.nativeImagesDecodeOnEveryKotlinTarget', reader_common),
        proof('UpstreamBinaryFormatTest.everyDictionaryAndCandidateMatchesTheUpstreamReader', reader_native)],
        'Equivalent lookup invariant on native fixtures: complete ordered keys/candidates, exact hits/misses, longest prefix and descending all-prefix results. Whole lexicons from all 22 native dictionaries also compared. FILE construction maps to owned byte-reader input.')
review('DartsDictTest', ['Serialization'], [proof('UpstreamWriterTest.everyDartsDictionaryIsByteIdenticalToUpstream', writer_native)],
       'Pure Kotlin writer produces all 22 native byte-identical Darts images; native reader exports are compared byte-for-byte. Tooling byte output replaces FILE ownership.')
review('MarisaDictTest', ['Serialization'], [proof('UpstreamWriterTest.everyMarisaDictionaryIsByteIdenticalToUpstream', writer_native)],
       'Pure Kotlin writer produces all 22 native byte-identical MARISA images; native reader exports every entry/candidate. Tooling byte output replaces FILE ownership.')
review('DartsDictTest', ['LoadsLegacy64bitFormat'], [proof('BinaryReaderTest.legacyAndBigEndianDartsImagesDecode', reader_common)],
       'Same legacy serialization transform: Darts uint32 units and binary offset/count fields widened to eight bytes. Checks every key/candidate plus longest prefix for both byte orders; native fixture corpus differs from TextDictTestBase.')
review('MarisaDictTest', ['MatchPrefix'], [
    proof('UpstreamEncodedTest.everyRecordedNativePrefixNulAndMalformedCase', 'opencc-core/src/jvmTest/kotlin/io/github/opencckmp/UpstreamEncodedTest.kt'),
    proof('BinaryReaderTest.compressedPrefixesRangesAndOwnedImageBytes', reader_common)],
       'Native .ocd2 configurations exercise bounded byte-prefix conversion at every encoded cut, including incomplete second characters; direct reader tests cover exact/longest prefix and half-open ranges. Oversized native pointer extents map to complete bounded Kotlin input, not out-of-array reads.')
review('BinaryDictTest', ['Serialization', 'SerializedLayoutIsWordSizeIndependent'], [
    proof('UpstreamWriterTest.everyDartsDictionaryIsByteIdenticalToUpstream', writer_native)],
       'The fixed-width BinaryDict payload is embedded in each byte-identical Darts image. Complete output bytes pin field widths, offsets and length accounting; byte output replaces architecture-sized FILE state.')
review('BinaryDictTest', ['Deserialization'], [proof('UpstreamBinaryFormatTest.everyDictionaryAndCandidateMatchesTheUpstreamReader', reader_native)],
       'Darts reader delegates to BinaryDict payload decoding; every ordered key and candidate from all 22 native images is compared, including lookup through decoded payloads.')
review('BinaryDictTest', ['EmptyDictRoundTrip'], [proof('WriterTest.valuesRoundTripAndEmptyDictionary', writer_common)],
       'Empty BinaryDict payload is explicitly exactly 12 bytes and loads to an empty lexicon; distinguishes fixed-width all-zero prefix from legacy layout.')
review('SerializedValuesTest', ['Serialization', 'Deserialization'], [proof('WriterTest.valuesRoundTripAndEmptyDictionary', writer_common)],
       'Values-only writer/reader round trip checks complete ordered candidate lists and empty string candidates in both byte orders; stronger than native NumValues-only comparison. Owned arrays replace FILE state.')

alignment = 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/AlignmentTest.kt'
review('ConversionAmbiguitiesTest', ['SourcesAreDeduplicated', 'LaterStageAmbiguityKeepsMatchPrecision'], [
    proof('AlignmentTest.ambiguitiesRetainPrecisionSourcesAndUtf16Units', alignment)],
       'Two occurrences in one input assert one source identity and precise one-unit ambiguity spans after a nonmatching first stage, including a supplementary scalar before the second occurrence. Native ASCII byte offsets map to UTF16 offsets.')
review('ConversionAmbiguitiesTest', ['StraddlingMatchCoarsensSpan'], [proof('AlignmentTest.straddledAmbiguityAndDeletionSurviveLaterStages', alignment)],
       'Same a->x, b->{y,z}, xy->Q dictionaries/input ab: output Q, source ab and span(0,1,0) asserted. Null segmentation keeps one complete input segment, equivalent to native ab segmentation entry.')
review('ConversionAmbiguitiesTest', ['UnionPolicyDictGroupMatchesConvert'], [
    proof('AlignmentTest.ambiguityGroupsAndNormalizedSourcesMatchConversion', alignment),
    proof('EngineTest.groupPoliciesAndTies', 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/EngineTest.kt')],
       'Direct Union-policy group chooses the later longer key and annotated output equals ordinary conversion; separate conversion assertions establish longest-key and earlier-child tie policy.')
review('ConversionAmbiguitiesTest', ['Utf8OffsetsAreBytes'], [
    proof('EncodedAmbiguitiesTest.byteOffsetsComposeAndMatchEquivalentStringSpans', 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/EncodedAmbiguitiesTest.kt'),
    proof('UpstreamAnnotationTest.allBuiltinConfigsMatchNativeCandidatesAndAmbiguities', 'opencc-core/src/jvmTest/kotlin/io/github/opencckmp/UpstreamAnnotationTest.kt')],
       'Byte offsets and lengths are checked against encoded prefix/span sizes for BMP and supplementary sources; native byte spans compared for all 16 configurations, including 文丑 repeated. Ordinary text annotations retain natural UTF16 units.')

review('DictionaryTest', ['BinaryTest'], [proof('UpstreamBinaryFormatTest.everyDictionaryAndCandidateMatchesTheUpstreamReader', reader_native)],
       'All 22 built-in native binary images match text source keys and complete ordered candidates and all-prefix results. This compares more than native entry key/count/default fields.')
