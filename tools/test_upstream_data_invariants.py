#!/usr/bin/env python3
"""Direct ports of the pinned Bazel dictionary data assertions, independent of runtime engine.

Python Unicode code points replace UTF8Util::NextCharLength traversal over valid source files.
All native selected inputs (including generated files) are retained.
"""
import unittest
from upstream import ROOT, fetch

SOURCE = fetch()
DIRECTORY = SOURCE / 'data/dictionary'
GENERATED = ROOT / '.cache/oracle/data'
TEXT_NAMES = {p.name for p in DIRECTORY.glob('*.txt')} - {'CJK_Compatibility_Ideographs.txt'}
TEXT_NAMES |= {'STPhrases_GeneratedFromRegionalPhrases.txt', 'TSCharactersExt.txt', 'TWVariantsRev.txt', 'HKVariantsRev.txt', 'JPShinjitaiCharactersRev.txt'}


def path(name):
    source = DIRECTORY / name
    return source if source.is_file() else GENERATED / name


def entries(name):
    result = []
    for line in path(name).read_text().splitlines():
        if not line or line.startswith('#'): continue
        key, values = line.split('\t', 1)
        result.append((key, values.split(' ')))
    return result


class NativeDictionaryDataAssertions(unittest.TestCase):
    def test_unique_sorted(self):
        for name in sorted(TEXT_NAMES):
            keys = [key.encode() for key, _ in entries(name)]
            self.assertEqual(len(keys), len(set(keys)), name)
            self.assertEqual(keys, sorted(keys), name)

    def test_regular_dictionaries_no_compatibility_ideographs(self):
        self.assertEqual(1002, len(entries('CJK_Compatibility_Ideographs.txt')))
        for name in sorted(TEXT_NAMES):
            for key, values in entries(name):
                self.assertFalse(any(0xF900 <= ord(c) <= 0xFAFF or 0x2F800 <= ord(c) <= 0x2FA1F for c in key + ' '.join(values)), name)

    def test_non_bmp_allowlist(self):
        allowed = {'STPhrases': set('𫖮𫗧𫛭𬴃𣗊𣲘'), 'TSPhrases': {'𫫇'}, 'STPhrases_GeneratedFromRegionalPhrases': {'𫫇'}}
        for name in sorted(TEXT_NAMES - {'STCharacters.txt', 'TSCharacters.txt', 'TSCharactersExt.txt'}):
            for c in path(name).read_text():
                if ord(c) > 0xFFFF: self.assertIn(c, allowed.get(name[:-4], set()), name)

    def test_phrase_substitutions_declared(self):
        for phrases, chars in [('STPhrases', 'STCharacters'), ('TSPhrases', 'TSCharacters'), ('HKVariantsRevPhrases', 'HKVariantsRev'), ('TWVariantsRevPhrases', 'TWVariantsRev')]:
            candidates = {key: {v for v in values if len(v) == 1} for key, values in entries(chars + '.txt') if len(key) == 1}
            for key, values in entries(phrases + '.txt'):
                for value in values:
                    if len(key) != len(value): continue
                    for source, target in zip(key, value):
                        if source != target: self.assertIn(target, candidates.get(source, set()), f'{phrases}: {key} -> {value}')

    def test_hk_phrases_segmentation_coverage(self):
        values = {value for name in ['STPhrases.txt', 'STPhrases_GeneratedFromRegionalPhrases.txt'] for _, vs in entries(name) for value in vs}
        self.assertTrue(values)
        for key, _ in entries('HKPhrases.txt'):
            if any(key in value for value in values): continue
            self.assertFalse(any(2 <= len(value) < len(key) and value in key for value in values), key)

    def test_generated_regional_phrases_valid(self):
        keys = {key for name in ['HKPhrases.txt', 'TWPhrases.txt'] for key, _ in entries(name)}
        for key, values in entries('STPhrases_GeneratedFromRegionalPhrases.txt'):
            self.assertGreaterEqual(len(key), 3, key)
            for value in values: self.assertIn(value, keys)

    def reverse(self, name):
        forward = {key: set(values) for key, values in entries(name + '.txt')}
        reverse = {key: set(values) for key, values in entries(name + 'Rev.txt')}
        for a, b in [(forward, reverse), (reverse, forward)]:
            for key, values in a.items():
                for value in values: self.assertIn(key, b.get(value, set()), name)

    def test_hk_reverse_mapping(self): self.reverse('HKPhrases')
    def test_tw_reverse_mapping(self): self.reverse('TWPhrases')

    def variants(self, name):
        multi = [set(values) for _, values in entries('STCharacters.txt') if len(values) > 1]
        self.assertTrue(multi)
        reverse = {key: values[0] for key, values in entries(name + 'Rev.txt')}
        exceptions = set()
        for key, values in entries(name + '.txt'):
            for value in values:
                if key == value or reverse.get(value) == value: continue
                if any(key in group and value in group for group in multi): exceptions.add(value)
        self.assertTrue(exceptions, name)
        expected = {values[0] for _, values in entries('STPhrases.txt') if len(values[0]) >= 2 and any(c in values[0] for c in exceptions)}
        identity = {key for key, values in entries(name + 'RevPhrases.txt') if key in values}
        self.assertFalse(expected - identity, name)

    def test_hk_variants_complete(self): self.variants('HKVariants')
    def test_tw_variants_complete(self): self.variants('TWVariants')


if __name__ == '__main__': unittest.main()
