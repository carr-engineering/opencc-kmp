"""Exercise legal payload isolation, KLIB placement, determinism and stale-notice replacement."""
import tempfile
import unittest
import io
from pathlib import Path
import zipfile

from embed_notices import embed, payloads
from verify_publications import check_artifact, check_root_variants


class PublicationNoticesTest(unittest.TestCase):
    def test_target_artifacts_do_not_substitute_for_root_variant_routing(self):
        data = {"variants": [{"available-at": {"group": "io.github.opencckmp", "module": "opencc-core-jvm"}}]}
        check_root_variants("opencc-core", data, ["jvm"])
        with self.assertRaisesRegex(AssertionError, "omits target variants"):
            check_root_variants("opencc-core", data, ["jvm", "js"])

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

    def test_aar_compresses_across_classes_without_changing_jar_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "artifact.aar"
            classes = io.BytesIO()
            data = {f"package/Class{index}.class": b"shared constant pool and code" * 100 + bytes([index])
                    for index in range(30)}
            data["META-INF/module.kotlin_module"] = b"Kotlin module metadata"
            with zipfile.ZipFile(classes, "w", compression=zipfile.ZIP_DEFLATED) as jar:
                for name, content in data.items(): jar.writestr(name, content)
            with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("classes.jar", classes.getvalue())
                archive.writestr("AndroidManifest.xml", b"<manifest />")
            embed(path, "opencc-core", "test")
            first = path.read_bytes()
            embed(path, "opencc-core", "test")
            self.assertEqual(path.read_bytes(), first)
            check_artifact(path, "opencc-core")
            with zipfile.ZipFile(path) as archive:
                self.assertEqual(archive.read("AndroidManifest.xml"), b"<manifest />")
                with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as jar:
                    self.assertEqual(set(jar.namelist()), set(data))
                    for info in jar.infolist():
                        self.assertEqual(info.compress_type, zipfile.ZIP_STORED)
                        self.assertEqual(jar.read(info.filename), data[info.filename])


if __name__ == "__main__": unittest.main()
