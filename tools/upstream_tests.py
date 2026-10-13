"""Upstream test-case discovery with honest suite-level Kotlin traceability.

A candidate suite is not proof that an individual native test was ported. Keeping
that distinction explicit lets release review track unreviewed cases rather than
inferring full coverage from passing broad differential suites.
"""
import ast
import hashlib
import re
from upstream_cli_tests import cli_case_review
from upstream_existing_case_review import CASE_REVIEWS


def validate_review(record, root):
    for evidence in record.get("evidence", []):
        path = root / evidence["file"]
        assert path.is_file(), evidence
        method = evidence["test"].rsplit(".", 1)[-1]
        if path.suffix == ".py":
            assert any(isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == method for node in ast.walk(ast.parse(path.read_text()))), evidence
        else:
            assert re.search(r"\bfun\s+" + re.escape(method) + r"\s*\(", path.read_text()), evidence
    return record

CANDIDATES = {
 'UTF8UtilTest': ['NativeUtf8UtilAssertionsTest', 'Utf8UtilitiesTest'],
 'UTF8StringSliceTest': ['Utf8UtilitiesTest', 'EngineTest'],
 'ConversionAmbiguitiesTest': ['EncodedAmbiguitiesTest', 'UpstreamAnnotationTest'],
 'ConversionCandidatesTest': ['EngineTest', 'UpstreamAnnotationTest'],
 'ConversionInspectionTest': ['EncodedTest', 'UpstreamEncodedTest', 'EngineTest'],
 'ConfigTest': ['ConfigContractTest', 'UpstreamConfigTest'],
 'SimpleConverterTest': ['EncodedTest', 'UpstreamConformanceTest', 'UpstreamEncodedTest'],
 'PhraseExtractTest': ['PhraseExtractTest'],
 'JiebaPluginIntegrationTest': ['JiebaTest', 'UpstreamJiebaTest'],
 'CommandLineConvertTest': ['tools/audit_cli.py', 'tools/test_cli.py'],
}

# Explicit assertion ports, reviewed against the pinned native source. Unlisted cases
# remain review-required even when candidate suites happen to exercise their area.
PORTED = {
    'ConversionTest': ('NativeBasicContractsTest', 'conversion', {
        'ConvertString', 'ConvertCString', 'ConvertMixedAsciiAndChinese', 'PreserveDictGroupPriority',
        'PreserveNestedDictGroupPriority', 'PreserveIdeographicDescriptionSequenceComponents',
        'PreserveComplexIdeographicDescriptionSequenceComponents', 'DoesNotPreserveIsolatedIdeographicDescriptionCharacters',
        'TruncatedUtf8Sequence'}),
    'DictGroupTest': ('NativeBasicContractsTest', 'group', {
        'KeyMaxLength', 'MatchPolicyDefaultsToShortCircuit', 'ExplicitMatchPolicy', 'UnionMatchPolicy',
        'UnionMatchPrefixPrefersLaterLongerMatch', 'SimpleGroupTest', 'TaiwanPhraseGroupTest'}),
    'ConversionChainTest': ('NativeBasicContractsTest', 'chain', {
        'Convert', 'StreamKeepsIncompleteIdeographicDescriptionSequence',
        'StreamKeepsIncompleteIdeographicDescriptionSequenceBeforeKeepLimit'}),
    'PipelineConverterTest': ('NativeBasicContractsTest', 'pipeline', {
        'ConvertChainsStagesInOrder', 'ConvertEmptyPipelineIsIdentity', 'GetSegmentationReturnsLastStage',
        'GetSegmentationEmptyPipelineReturnsNullptr', 'InspectPopulatesPipelineStages',
        'InspectEmptyPipelineReturnsTrivialResult', 'InspectOutputMatchesConvert'}),
    'MaxMatchSegmentationTest': ('NativeInspectionSegmentationTest', '', {'Segment', 'MixedAsciiAndChinese', 'EmptyString', 'StringViewSlice', 'SingleCharacter', 'PreserveIdeographicDescriptionSequence', 'TruncatedUtf8Sequence'}),
    'SegmentsToVectorTest': ('NativeInspectionSegmentationTest', '', {'ManagedSegments', 'UnmanagedSegments', 'MixedSegments', 'EmptySegments'}),
    'ConversionInspectionTest': ('NativeInspectionSegmentationTest', '', {'OutputMatchesConvert', 'InputPreserved', 'SegmentsNonEmpty', 'StagesCountMatchesChain', 'StageIndexStartsAtOne', 'EmptyInput'}),
    'TextDictTest': ('NativeBasicContractsTest', 'textDict', {'DictTest', 'DeserializationEmptyLine', 'ExactMatch', 'MatchPrefix'}),
    'LexiconAnnotationTest': ('UpstreamLexiconSerializationTest', '', {
        'ParseCommentLines', 'ParseAttachedComment', 'ParseFloatingComment', 'ParseFooterComment',
        'SerializeIgnoresComments', 'SortIgnoresComments', 'DefaultBehaviorIgnoresComments', 'DefaultBehaviorAcceptsCommentLines'}),
}


def reviewed_case(suite, name, root):
    if suite == 'CommandLineConvertTest':
        cli = cli_case_review(root, name)
        if cli: return cli
    try:
        from upstream_config_case_review import CASE_REVIEWS as config_reviews
    except ModuleNotFoundError:
        config_reviews = {}
    from upstream_final_case_review import CASE_REVIEWS as final_reviews
    from upstream_binary_case_review import CASE_REVIEWS as binary_reviews
    if (suite, name) in final_reviews:
        return validate_review(final_reviews[(suite, name)], root)
    if (suite, name) in binary_reviews:
        return validate_review(binary_reviews[(suite, name)], root)
    if (suite, name) in config_reviews:
        return validate_review(config_reviews[(suite, name)], root)
    if (suite, name) in CASE_REVIEWS:
        return validate_review(CASE_REVIEWS[(suite, name)], root)
    if suite in ('PrefixMatchTest', 'PrefixMatchSkipTest', 'Utf8SkipScanTest'):
        method = {
            'MarisaLazyLoadFastPathDoesNotReconstruct': 'marisaLoadedStorageIsOwned',
            'MatchPrefixViewFastPathReturnsViews': 'matchPrefixViewFastPathReturnsOwnedValues',
            'MatchPrefixViewTablePathReturnsViews': 'matchPrefixViewTablePathReturnsOwnedValues',
        }.get(name, name[0].lower() + name[1:])
        target = 'NativePrefixScanTest.' + method
    elif suite == 'ConversionCandidatesTest':
        target = 'EncodedCandidateTest.' + name[0].lower() + name[1:]
    elif suite.startswith('UTF8Util'):
        target = 'NativeUtf8UtilAssertionsTest.' + name[0].lower() + name[1:]
    elif suite == 'UTF8StringSliceTest':
        method = {'UTF8Length': 'utf8Length', 'ToString': 'toStringValue'}.get(name, name[0].lower() + name[1:])
        target = 'NativeStringSliceAssertionsTest.' + method
    elif suite == 'TextDictTest' and name in ('Serialization', 'Deserialization'):
        target = 'UpstreamLexiconSerializationTest.textDict' + name
    elif suite in PORTED and name in PORTED[suite][2]:
        cls, prefix, _ = PORTED[suite]
        target = cls + '.' + (prefix + name if prefix else name[0].lower() + name[1:])
    else:
        return {'port_review': 'requires-case-review'}
    cls, method = target.split('.')
    paths = list(root.glob('opencc-*/src/commonTest/kotlin/**/' + cls + '.kt'))
    assert len(paths) == 1, target
    assert re.search(r'\bfun\s+' + re.escape(method) + r'\s*\(', paths[0].read_text()), target
    return {'port_review': 'reviewed-equivalent' if name == 'MarisaLazyLoadFastPathDoesNotReconstruct' else 'ported-assertions', 'kotlin_test': target, 'kotlin_test_file': str(paths[0].relative_to(root)),
            'contract_adaptation': ('MARISA decoding creates an immutable Kotlin lookup snapshot; native reconstruction-state flags are representation details. Query key/value/encoded length and stable owned entry identity are asserted after caller input is mutated.' if name == 'MarisaLazyLoadFastPathDoesNotReconstruct' else 'Native observable assertions retained; Kotlin UTF16/byte ranges, ownership and provider/tooling equivalents replace pointer/FILE mechanics. Borrowed native prefix views become stable owned DictEntry strings.')}


def test_inventory(source, root):
    tests = {}
    candidates = sorted(list((source / 'src').glob('*Test.cpp')) +
                        list((source / 'test').glob('*.cpp')) +
                        list((source / 'data').glob('*/*Test.cpp')) +
                        list((source / 'plugins').glob('*/tests/*Test.cpp')))
    for path in candidates:
        text = path.read_text()
        cases = re.findall(r'\b(TEST(?:_F|_P)?|TYPED_TEST)\s*\(\s*(\w+)\s*,\s*(\w+)\s*\)', text)
        if not cases: continue
        names = CANDIDATES.get(path.stem)
        if names is None:
            if 'Dict' in path.stem or 'SerializedValues' in path.stem:
                names = ['BinaryReaderTest', 'UpstreamBinaryFormatTest', 'EngineTest', 'tools/generate_data.py']
            else: names = ['EngineTest', 'UpstreamConformanceTest']
        files = []
        for name in names:
            files += [name] if '/' in name and (root / name).exists() else [str(p.relative_to(root)) for p in root.glob('opencc-*/src/*Test/kotlin/**/' + name + '.kt')]
        tests[str(path.relative_to(source))] = {
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'kotlin_candidate_suites': sorted(set(files)),
            'cases': [{'macro': macro, 'suite': suite, 'name': name, **reviewed_case(suite, name, root)}
                      for macro, suite, name in cases],
        }
    return tests
