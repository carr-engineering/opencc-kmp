#!/usr/bin/env python3
"""Generate small upstream-produced binary images embedded only in common tests."""
import hashlib
from pathlib import Path
import subprocess
from upstream import ROOT, LOCK

entries = {
    "a": ["A", "B"], "ab": ["X"], "abc": ["Y", "Z"],
    "long_shared_prefix_one_suffix": ["one"], "long_shared_prefix_two_suffix": ["two"],
    "汉": ["漢"], "汉字": ["漢字"], "词": ["詞"], "⿰龙马": ["龘"],
    "😀": ["smile"], "𠀀": ["甲", "乙"],
}
work = ROOT / ".cache/conformance/images"
work.mkdir(parents=True, exist_ok=True)
source = work / "dictionary.txt"
source.write_text("".join(key + "\t" + " ".join(values) + "\n" for key, values in sorted(entries.items())))
tool = ROOT / ".cache/oracle/src/tools/opencc_dict"
lines = ["// Upstream-generated images for tests only; never packaged in the runtime.",
         "// Reference commit: " + LOCK["commit"], "package io.github.opencckmp", "", "internal object BinaryTestImages {"]
for extension in ("ocd", "ocd2"):
    output = work / ("dictionary." + extension)
    subprocess.run([str(tool), "-i", str(source), "-o", str(output), "-f", "text", "-t", extension], check=True)
    binary = output.read_bytes()
    hex_data = binary.hex()
    lines += ["    // SHA-256: " + hashlib.sha256(binary).hexdigest(), "    val " + extension + ": ByteArray get() = decode(\"\"\""]
    lines += ["        " + hex_data[index:index + 128] for index in range(0, len(hex_data), 128)]
    lines += ["    \"\"\")", ""]
lines += ["    private fun decode(hex: String): ByteArray {", "        val data = hex.filterNot { it.isWhitespace() }",
          "        return ByteArray(data.length / 2) { data.substring(it * 2, it * 2 + 2).toInt(16).toByte() }",
          "    }", "}", ""]
output = ROOT / "opencc-core/src/commonTest/kotlin/io/github/opencckmp/BinaryTestImages.kt"
output.write_text("\n".join(lines))
print(output)

