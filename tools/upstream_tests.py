"""Upstream test-case discovery with honest suite-level Kotlin traceability.

A candidate suite is not proof that an individual native test was ported. Keeping
that distinction explicit lets release review track unreviewed cases rather than
inferring full coverage from passing broad differential suites.
"""
import hashlib
import re

CANDIDATES = {
 'UTF8UtilTest': ['UpstreamUtf8UtilTest', 'Utf8UtilitiesTest'],
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
        reviewed = path.stem == 'UTF8UtilTest'
        tests[str(path.relative_to(source))] = {
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'kotlin_candidate_suites': sorted(set(files)),
            'cases': [{'macro': macro, 'suite': suite, 'name': name,
                       'port_review': 'ported-assertions' if reviewed else 'requires-case-review',
                       **({'kotlin_test': 'UpstreamUtf8UtilTest.' + name[0].lower() + name[1:],
                           'contract_adaptation': 'Kotlin offset/range/ownership equivalents; native assertions retained'} if reviewed else {})}
                      for macro, suite, name in cases],
        }
    return tests
