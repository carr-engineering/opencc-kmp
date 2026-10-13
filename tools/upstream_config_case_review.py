"""Case-level review of pinned ConfigTest.cpp and bundled configuration tests.

Evidence names actual assertions, not merely files or broad differential counts.
Unproven combinations remain explicit gaps. Resource-provider tests alone do not
prove configuration-loader provider precedence or dictionary scoping.
"""
CORE = 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/'
JVM = 'opencc-core/src/jvmTest/kotlin/io/github/opencckmp/'
PLATFORM = 'opencc-platform/src/jvmTest/kotlin/io/github/opencckmp/platform/'
ZIP = 'opencc-zip/src/commonTest/kotlin/io/github/opencckmp/zip/'


def proof(test, file):
    return {'test': test, 'file': file}


def mapped(details, *evidence):
    return {'port_review': 'reviewed-equivalent', 'evidence': list(evidence), 'contract_adaptation': details}


def pending(details, *evidence):
    return {'port_review': 'requires-case-review', 'evidence': list(evidence), 'contract_adaptation': details,
            'remaining': details}


FILE_CONFIG = proof('UpstreamFileConfigTest.filesystemAndZipConfigProviderCasesMatchPinnedReference', PLATFORM + 'UpstreamFileConfigTest.kt')
FILE_LOCAL = proof('FileConfigLoaderTest.filesystemConfigAndRootedDictionaryProvidersKeepSeparateLocations', PLATFORM + 'FileConfigLoaderTest.kt')
FILE_ADJACENT = proof('FileConfigLoaderTest.adjacentParentAndUnicodeConfigurationsLoadWithoutTransitiveData', PLATFORM + 'FileConfigLoaderTest.kt')
FILE_ZIP = proof('FileConfigLoaderTest.providerConfigWinsAndAbsoluteHostConfigFallsBackWithZipResources', PLATFORM + 'FileConfigLoaderTest.kt')
SCHEMA = proof('UpstreamConfigTest.pinnedJsonSchemaWarningsErrorsAndOptions', JVM + 'UpstreamConfigTest.kt')
ENCODED = proof('UpstreamEncodedTest.everyRecordedNativePrefixNulAndMalformedCase', JVM + 'UpstreamEncodedTest.kt')

CASE_REVIEWS = {
    ('ConfigTest', 'Convert'): mapped('JSON-loaded converters compare complete expected output over every upstream testcase/config pair, both text and original ocd2 readers.',
        proof('UpstreamConformanceTest.allUpstreamCasePairs', JVM + 'UpstreamConformanceTest.kt')),
    ('ConfigTest', 'ConvertLength'): mapped('Encoded conversion results compare both bytes and reported byte counts to native; Kotlin ByteArray.size replaces C++ string UTF-8 byte length.', ENCODED),
    ('ConfigTest', 'NonexistingPath'): mapped('Native config/provider integration includes an absolute missing config path and compares FileNotFound kind and complete message.', FILE_CONFIG),
    ('ConfigTest', 'NewFromStringWitoutTrailingSlash'): mapped('FileConfigLoader.newFromString receives a real directory without trailing slash and converts using its adjacent dict; directory joining is owned by the platform adapter.', FILE_ADJACENT),
    ('ConfigTest', 'DefaultConfigPathFindsAdjacentResources'): mapped('Absolute adjacent config and dictionary files load through FileConfigLoader with no explicit provider, compared to native default path loading.', FILE_CONFIG, FILE_ADJACENT),
    ('ConfigTest', 'ExplicitProviderFindsResources'): mapped('Absolute config in config/ loads dict.txt from distinct explicit first/second resource roots, with no automatic config-directory prefix on dictionary names.', FILE_CONFIG, FILE_LOCAL),
    ('ConfigTest', 'ExplicitProviderFindsConfigNameAndResources'): mapped('Bare config.json and its dict.txt are obtained from the explicit first resource root and compared with native provider loading.', FILE_CONFIG),
    ('ConfigTest', 'ZipProviderFindsConfigNameAndResources'): mapped('Native ZIP case supplies bare config.json; separate absolute host-config case obtains a file dictionary from ZIP. Same operations prove config acquisition and dictionary loading through ZIP.', FILE_CONFIG, FILE_ZIP),
    ('ConfigTest', 'ZipProviderDoesNotOverrideAbsoluteConfigPath'): mapped('ZIP config.json returns 乙 while absolute host config returns 甲; native differential and direct assertions preserve host fallback without changing core logical-provider semantics.', FILE_CONFIG, FILE_ZIP),
    ('ConfigTest', 'ExplicitProviderConfigOverridesInstalledOrCwdConfigName'): mapped('Native process runs in a cwd containing conflicting config.json; explicit filesystem provider config wins. Kotlin loader tries the explicit provider before local filesystem fallback.', FILE_CONFIG),
    ('ConfigTest', 'RelativeParentDictionaryPathStillWorks'): mapped('Adjacent nested config declares ../first/dict.txt; native differential and direct nested/../dict.txt assertions preserve literal parent traversal.', FILE_CONFIG, FILE_ADJACENT),
    ('ConfigTest', 'MultipleSearchPathsUseFirstMatch'): mapped('Two explicit roots contain different outputs for dict.txt; first wins, then second wins after first file removal. Config and resource lookup are both asserted.', FILE_CONFIG, FILE_LOCAL),
    ('ConfigTest', 'MissingResourceListsSearchedPaths'): mapped('Exact resourceName includes missing name and both roots; standalone native provider comparison additionally checks the complete FileNotFound message.',
        proof('ResourceProviderTest.resolutionChecksRegularFilesBeforeReadingAndAllowsAbsolutePathsWithNoRoots', PLATFORM + 'ResourceProviderTest.kt'),
        proof('UpstreamResourceTest.filesystemOperationsMatchPinnedReference', PLATFORM + 'UpstreamResourceTest.kt')),
    ('ConfigTest', 'FilesystemResourceCacheKeyIncludesFreshness'): mapped('Snapshot name/size are asserted; same-size file overwrite with changed timestamp must change the opaque equality token, ruling out the old name+size-only key. Kotlin token serialization is not a contract.',
        proof('ResourceProviderTest.resolutionChecksRegularFilesBeforeReadingAndAllowsAbsolutePathsWithNoRoots', PLATFORM + 'ResourceProviderTest.kt'),
        proof('ResourceProviderTest.concurrentMetadataSnapshotsRemainOwnedAndSameSizeUpdatesInvalidateVersions', PLATFORM + 'ResourceProviderTest.kt')),
    ('ConfigTest', 'PluginLikeResourcePathSupplementsMainPath'): mapped('Explicit provider config integration searches an unsuccessful first root and successfully loads the dictionary from the later root. Native testcase itself uses ordinary filesystem roots, not a segmentation plugin.', FILE_CONFIG),
    ('ConfigTest', 'InlineDictBasicConversion'): mapped('Native encoded segmented and unsegmented inline configs compare conversions for matching Chinese/ASCII keys and unmatched surrounding input.', ENCODED),
    ('ConfigTest', 'InlineDictInGroupTakesPriorityOverFollowingFileDict'): mapped('Encoded short_circuit config puts inline 汉=>first before nested ocd2/text 汉=>漢; native output and inspection assertions compare exact first-entry precedence.', ENCODED),
    ('ConfigTest', 'GroupDictWithoutMatchPolicyDefaultsToShortCircuit'): pending('Missing match_policy warnings/acceptance are compared, and direct DictGroup priority is asserted separately; no existing combined JSON-loader test proves overlapping inline/file priority with omitted policy.', SCHEMA,
        proof('EngineTest.groupPoliciesAndTies', CORE + 'EngineTest.kt')),
    ('ConfigTest', 'UnionDictGroupPrefersLaterLongerMatch'): mapped('Encoded union config has early inline a and later dictionary abc; input 汉字abc compares later-longer segmentation/conversion against native, including nested groups.', ENCODED),
    ('ConfigTest', 'InlineDictOutputStillProcessedByLaterChainStep'): mapped('JSON-loaded inline 神=>神明 神祇 feeds a later 明=>明亮 stage; expected 神明亮 神祇 is asserted, proving emitted output continues through the chain.',
        proof('EngineTest.configNormalizationInlineOptionsAndInspection', CORE + 'EngineTest.kt')),
    ('ConfigTest', 'InlineSegmentationDictUsesLongestMatch'): pending('Native encoded segmentation covers inline keys and grouped later-longer keys, but no dedicated flat inline ABC/AB competing-key case exists yet.', ENCODED),
    ('ConfigTest', 'InlineDictPreservesExactStringSemantics'): pending('Inline key/value NUL semantics and candidates are covered, but a padded " A " key distinct from "A" is not explicitly asserted.', SCHEMA),
    ('ConfigTest', 'InlineDictValidationErrors'): mapped('Config audit generates missing entries, entries array, empty key/value, nonstring value and may_output_tofu cases; JVM comparison checks error kinds/messages and first schema warning exactly.', SCHEMA),
    ('ConfigTest', 'InlineDictSupportsJsoncCommentsAndTrailingComma'): mapped('Config fixtures comments, trailing and trailing_nested load inline dictionaries and compare native conversion/error/warning results in string/file modes.', SCHEMA,
        proof('EngineTest.configNormalizationInlineOptionsAndInspection', CORE + 'EngineTest.kt')),
    ('ConfigTest', 'InlineSegmentationAndConversionWorksWithOcd2GroupDicts'): mapped('Encoded group configs combine inline entries with real native ocd2 dictionaries in both segmentation and conversion, under short_circuit and union. Full output/inspection comparisons cover this composition.', ENCODED),
    ('ConfigTest', 'LoadConfigFromUnicodePath'): mapped('FileConfigLoader loads real 設定😀.json and adjacent dictionary through native differential and direct assertions. Windows runtime execution remains a CI-host gate, independent of source-level port mapping.', FILE_CONFIG, FILE_ADJACENT),
    ('ConfigTest', 'NormalizationRunsBeforeSegmentationAndConversion'): mapped('JSON 神=>神 normalization runs before inline 神 segmentation and 神=>神明 chain; result and inspected main-stage segments are asserted.',
        proof('EngineTest.configNormalizationInlineOptionsAndInspection', CORE + 'EngineTest.kt'), ENCODED),
    ('ConfigTest', 'AbsentNormalizationPreservesOriginalBehavior'): mapped('Encoded unsegmented/segmented configs omit normalization and compare native conversion output, while normalized fixture is separate.', ENCODED),
    ('ConfigTest', 'NormalizationMissingFileDictThrows'): pending('Malformed normalization shapes are audited, but a valid normalization chain referring specifically to a missing file dictionary is not generated or asserted.', SCHEMA),
    ('ConfigTest', 'NormalizationGetConversionChainIsNonNull'): pending('Normalized candidate APIs implicitly need a main chain, but no JSON-loaded normalized converter asserts main conversionChain dictionary matches/main chain conversion distinctly from normalization.',
        proof('EncodedCandidateTest.normalizationPrePassApplied', CORE + 'EncodedCandidateTest.kt')),
    ('ConfigTest', 'RimeConvertWordExactExpansionThreadsThroughChain'): pending('Candidate branching/chaining is asserted, but native Rime test helper requires a retained fallback vector and exact-match boolean; its exact three-branch case is not ported as a composed Kotlin assertion.',
        proof('EncodedCandidateTest.chainedExpansionCarriesBranches', CORE + 'EncodedCandidateTest.kt')),
    ('ConfigTest', 'RimeConvertWordPartialFallbackContinuesToNextStage'): pending('Existing candidate partialConversionInLaterDict starts with an exact match and tests later partial conversion. Native case starts with partial conversion then a later exact match, which is a different direction.',
        proof('EncodedCandidateTest.partialConversionInLaterDict', CORE + 'EncodedCandidateTest.kt')),
    ('ConfigTest', 'RimeConvertWordReturnsFalseWithoutAnyExactMatch'): pending('Prefix-only candidate APIs return empty, but the native helper also returns the partial-conversion output vector. Need a composed no-exact fallback assertion, not merely empty candidates.',
        proof('EncodedCandidateTest.prefixOnlyMatchReturnsEmpty', CORE + 'EncodedCandidateTest.kt')),
    ('ConfigDictValidationTest', 'ConvertExpectedOutputs'): mapped('allUpstreamCasePairs reads the same consolidated testcases.json and compares every expected config output using text and original native ocd2 dictionaries.',
        proof('UpstreamConformanceTest.allUpstreamCasePairs', JVM + 'UpstreamConformanceTest.kt')),
    ('ConfigSchemaValidationTest', 'BundledConfigsMatchSchema'): pending('Config schema acceptance/warnings are audited for generated fixtures, but no existing assertion verifies an empty warning set for every bundled standard/Jieba config against the pinned schema.', SCHEMA),
}
