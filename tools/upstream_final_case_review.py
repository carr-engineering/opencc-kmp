"""Last direct assertion ports, reviewed against pinned per-case source."""
CORE = 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/'
JVM = 'opencc-core/src/jvmTest/kotlin/io/github/opencckmp/'
JIEBA = 'opencc-jieba/src/jvmTest/kotlin/io/github/opencckmp/jieba/'
CASE_REVIEWS = {}


def add(suite, name, file, method, adaptation='Literal native outputs and metadata retained; String offsets use UTF16 and Kotlin-owned results replace borrowed pointers.', status='ported-assertions'):
    CASE_REVIEWS[(suite, name)] = {'port_review': status, 'evidence': [{'file': file, 'test': method}], 'contract_adaptation': adaptation}


for name in ['SingleStageMultiValue', 'AmbiguityInLaterStageMapsToInputSource', 'UnionPolicyTieBreakMatchesConvert', 'UnionNumValuesComesFromWinningChild', 'EmptyDictionaryValueDoesNotBreakCompose', 'NulBytePreservedWithoutSegmentation', 'StreamChunkCarriesAnalyzedFlag', 'PipelineConverterIsFlaggedUnanalyzed', 'UnmatchedTextHasNoAmbiguities', 'PhraseEntryShadowsAmbiguousCharacter', 'ChunkedStreamMatchesWholeInput', 'DefaultWindowingMatchesConverterStream']:
    add('ConversionAmbiguitiesTest', name, CORE + 'NativeAmbiguityCompositionTest.kt', 'NativeAmbiguityCompositionTest.' + name[0].lower() + name[1:])
for name in ['GroupDictWithoutMatchPolicyDefaultsToShortCircuit', 'InlineSegmentationDictUsesLongestMatch', 'InlineDictPreservesExactStringSemantics', 'NormalizationMissingFileDictThrows', 'NormalizationGetConversionChainIsNonNull', 'RimeConvertWordExactExpansionThreadsThroughChain', 'RimeConvertWordPartialFallbackContinuesToNextStage', 'RimeConvertWordReturnsFalseWithoutAnyExactMatch']:
    add('ConfigTest', name, CORE + 'NativeConfigCompositionTest.kt', 'NativeConfigCompositionTest.' + name[0].lower() + name[1:], 'Literal native assertions retained. The private upstream Rime test helper maps to public getAllConversions plus main-chain fallback; its boolean/vector are asserted without introducing a redundant public helper.')
add('ConfigSchemaValidationTest', 'BundledConfigsMatchSchema', CORE + 'NativeBundledSchemaTest.kt', 'NativeBundledSchemaTest.bundledConfigsMatchSchema', 'Every standard, Jieba and config-test source JSON is checked directly by the pinned specialized schema validator, without loading resource payloads.')
add('ConversionTest', 'DifferentialFuzzAgainstReferenceLoop', CORE + 'NativeConversionFuzzTest.kt', 'NativeConversionFuzzTest.matchesReferenceLoopOnFuzzedUtf8', 'Exact mt19937 seed20260724/native600inputs retain byte output and InvalidUTF8 behavior for group/table and MARISA paths; native oracle separately checks optimized loops against the one-character reference loop.')
for name in ['Convert', 'Multithreading', 'CInterface']:
    add('SimpleConverterTest', name, JVM + 'UpstreamFacadeAssertionsTest.kt', 'UpstreamFacadeAssertionsTest.simpleConverter' + name, 'Exact poem, independent concurrent construction, UTF8 owned output/buffer byte count, close and literal missing-path error asserted. Kotlin GC replaces free; handle/error contexts are caller-owned.')
for name, method in [('SimpleConverter_s2t', 'simpleConverterS2t'), ('SimpleConverter_t2s', 'simpleConverterT2s'), ('CInterface_s2t', 'cInterfaceS2t'), ('CInterface_t2s', 'cInterfaceT2s')]:
    add('BazelOpenccTest', name, JVM + 'UpstreamFacadeAssertionsTest.kt', 'UpstreamFacadeAssertionsTest.' + method, 'Exact Simplified/Traditional and tofu-risk default outputs asserted through both façades; explicit providers replace Bazel runfiles/cwd/global environment, which belong to host resource delivery.')
for name in ['ConvertFromJsonCases', 'ConvertWithMergedOcd2JiebaDictionary', 'ConvertFromUnicodePluginPath']:
    add('JiebaPluginIntegrationTest', name, JIEBA + 'UpstreamJiebaLiteralTest.kt', 'UpstreamJiebaLiteralTest.' + name[0].lower() + name[1:], '43 source JSON expected pairs and exact merged-dictionary regression asserted. Registered Kotlin factory replaces native DLL/plugin ABI discovery; Unicode host resource directory remains tested. Windows native DLL-search mechanics are outside pure KMP operation compatibility.', 'reviewed-equivalent' if name == 'ConvertFromUnicodePluginPath' else 'ported-assertions')
CASE_REVIEWS[('JiebaPluginIntegrationTest', 'ConvertFromJsonCases')]['port_review'] = 'reviewed-equivalent'
CASE_REVIEWS[('JiebaPluginIntegrationTest', 'ConvertFromJsonCases')]['contract_adaptation'] += ' All43pairs are checked against pinned C++ at runtime;42 retain source expected outputs. One pinned-source tw2sp fixture expects 慰借 while that release dictionary and native CLI produce 慰藉: both values are explicitly asserted and docs/jieba-literal-fixture-audit.json records this drift. Kotlin follows native output.'

for suite, name, method in [
    ('DictionaryTest', 'UniqueSortedTest', 'test_unique_sorted'),
    ('DictionaryCompatibilityIdeographsTest', 'RegularDictionariesDoNotContainCompatibilityIdeographs', 'test_regular_dictionaries_no_compatibility_ideographs'),
    ('DictionaryNonBmpTest', 'OnlyAllowlistedCharactersOutsideCharacterDicts', 'test_non_bmp_allowlist'),
    ('DictionaryPhraseCharacterDependencyTest', 'PhraseSubstitutionsRemainDeclaredInCharacterDictionaries', 'test_phrase_substitutions_declared'),
    ('DictionaryPhraseSegmentationTest', 'HKPhrasesKeysAreCoveredBySTPhraseValues', 'test_hk_phrases_segmentation_coverage'),
    ('DictionaryPhraseSegmentationTest', 'GeneratedSTPhrasesFromRegionalPhrasesAreValid', 'test_generated_regional_phrases_valid'),
    ('DictionaryReverseMappingTest', 'HKPhrasesReverseMapping', 'test_hk_reverse_mapping'),
    ('DictionaryReverseMappingTest', 'TWPhrasesReverseMapping', 'test_tw_reverse_mapping'),
    ('DictionaryVariantRevPhrasesTest', 'HKVariantsAreComplete', 'test_hk_variants_complete'),
    ('DictionaryVariantRevPhrasesTest', 'TWVariantsAreComplete', 'test_tw_variants_complete')]:
    add(suite, name, 'tools/test_upstream_data_invariants.py', 'NativeDictionaryDataAssertions.' + method, 'Identical native selected/generated dictionary inputs and allowlists checked by Python Unicode scalar traversal; filesystem/runfiles and UTF8 pointer iteration are build-tool implementation details.')
