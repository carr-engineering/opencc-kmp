#!/usr/bin/env python3
"""Acceptance checks for bundle closure, hashes, deduplication and reproducible archives."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from build_data import create_bundle, inventory, references
from upstream import ROOT, fetch


class DataBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.oracle = ROOT / ".cache/oracle"
        if not (cls.oracle / "src/tools/opencc_dict").exists():
            raise RuntimeError("Run tools/build_oracle.py before data acceptance tests")
        cls.source = fetch()

    def test_selected_closure_has_optional_data_without_unrelated_regions(self):
        with tempfile.TemporaryDirectory(prefix="opencc-pack-") as directory:
            output = Path(directory) / "opencc"
            result = create_bundle(output, ["t2s"], self.oracle)
            manifest = json.loads((output / "manifest.json").read_text())
            self.assertEqual(["t2s"], result["configs"])
            self.assertIn("TSCharactersExt.ocd2", manifest["files"])
            self.assertNotIn("TWPhrases.ocd2", manifest["files"])
            self.assertNotIn("jieba_dict/jieba_merged.ocd2", manifest["files"])
            self.assertEqual(set(manifest["configs"]["t2s"]),
                             {name for name in manifest["files"] if name.endswith(".ocd2")})
            for name, spec in manifest["files"].items():
                data = (output / name).read_bytes()
                self.assertEqual(spec["size"], len(data), name)
                self.assertEqual(spec["sha256"], hashlib.sha256(data).hexdigest(), name)

    def test_shared_resources_are_deduplicated_and_archive_is_reproducible(self):
        with tempfile.TemporaryDirectory(prefix="opencc-pack-") as directory:
            first = create_bundle(Path(directory) / "first", ["s2t", "s2tw", "s2t"], self.oracle)
            second = create_bundle(Path(directory) / "second", ["s2tw", "s2t"], self.oracle)
            self.assertEqual(first["zip_sha256"], second["zip_sha256"])
            manifest = json.loads((Path(directory) / "first/manifest.json").read_text())
            required = set().union(*map(set, manifest["configs"].values()))
            self.assertEqual(len(required), first["resource_count"])
            with zipfile.ZipFile(first["archive"]) as archive:
                self.assertEqual(len(archive.namelist()), len(set(archive.namelist())))
                self.assertFalse(any(name.endswith(".zip") for name in archive.namelist()))

    def test_complete_bundle_contains_every_config_dictionary_and_plugin_resource(self):
        with tempfile.TemporaryDirectory(prefix="opencc-pack-") as directory:
            output = Path(directory) / "opencc"
            result = create_bundle(output, oracle=self.oracle)
            configs, resources = inventory(self.source, self.oracle)
            manifest = json.loads((output / "manifest.json").read_text())
            self.assertEqual(23, len(result["configs"]))
            self.assertTrue(set(resources).issubset(manifest["files"]))
            self.assertEqual(set(configs), set(manifest["configs"]))
            self.assertIn("jieba_dict/hmm_model.utf8", manifest["files"])
            self.assertIn("licenses/cppjieba.txt", manifest["files"])

    def test_jieba_selected_closure_includes_auxiliary_files_and_deduplicates_models(self):
        with tempfile.TemporaryDirectory(prefix="opencc-pack-") as directory:
            output = Path(directory) / "opencc"
            create_bundle(output, ["s2t_jieba", "s2tw_jieba"], self.oracle)
            manifest = json.loads((output / "manifest.json").read_text())
            auxiliary = {"jieba_dict/idf.utf8", "jieba_dict/stop_words.utf8",
                         "jieba_dict/hmm_model.utf8", "jieba_dict/jieba_merged.ocd2"}
            self.assertTrue(auxiliary.issubset(manifest["configs"]["s2t_jieba"]))
            self.assertTrue(auxiliary.issubset(manifest["configs"]["s2tw_jieba"]))
            self.assertTrue(auxiliary.issubset(manifest["files"]))
            self.assertNotIn("jieba_dict/jieba.dict.utf8", manifest["files"])
            self.assertNotIn("TSCharacters.ocd2", manifest["files"])
            with zipfile.ZipFile(output.with_suffix(".zip")) as archive:
                self.assertEqual(len(archive.namelist()), len(set(archive.namelist())))

    def test_unknown_config_is_rejected_and_reselection_removes_stale_resources(self):
        with tempfile.TemporaryDirectory(prefix="opencc-pack-") as directory:
            output = Path(directory) / "opencc"
            with self.assertRaisesRegex(ValueError, "Unknown configurations"):
                create_bundle(output, ["missing"], self.oracle)
            create_bundle(output, ["s2t", "s2twp"], self.oracle)
            self.assertTrue((output / "TWPhrases.ocd2").exists())
            create_bundle(output, ["s2t"], self.oracle)
            self.assertFalse((output / "TWPhrases.ocd2").exists())


if __name__ == "__main__":
    unittest.main()
