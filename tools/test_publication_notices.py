"""Exercise legal payload isolation, KLIB placement, determinism and stale-notice replacement."""
import tempfile
import unittest
from pathlib import Path
import zipfile

from embed_notices import embed, payloads
from verify_publications import check_artifact


class PublicationNoticesTest(unittest.TestCase):
    def test_module_licenses_are_focused(self):
        expected = {
            "opencc-core": {"marisa.txt"},
            "opencc-dictionary-tools": {"marisa.txt", "darts-clone.txt"},
            "opencc-jieba": {"CPPJIEBA-LICENSE"},
            "opencc-cli": {"tclap.txt"},
            "opencc-platform": set(),
        }
        for module, licenses in expected.items():
            actual = {name.removeprefix("licenses/") for name in payloads(module, "test")
                      if name.startswith("licenses/")}
            self.assertEqual(actual, licenses)

    def test_archives_preserve_compiled_bytes_and_replace_stale_notices(self):
        for suffix in [".jar", ".aar", ".klib"]:
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / ("artifact" + suffix)
                prefix = "default/resources/META-INF/opencc/" if suffix == ".klib" else "META-INF/opencc/"
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("compiled", b"\x00\xffcompiled bytes")
                    if suffix == ".klib": archive.writestr("default/manifest", "unique_name=example")
                    archive.writestr(prefix + "NOTICE", "stale")
                embed(path, "opencc-core", "test")
                first = path.read_bytes()
                embed(path, "opencc-core", "test")
                self.assertEqual(path.read_bytes(), first)
                check_artifact(path, "opencc-core")
                with zipfile.ZipFile(path) as archive:
                    self.assertEqual(archive.read("compiled"), b"\x00\xffcompiled bytes")
                    self.assertEqual(archive.namelist().count(prefix + "NOTICE"), 1)


if __name__ == "__main__": unittest.main()
