"""Compare the nested AAR layouts using identical classes and metadata; writes reproducible evidence."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import time
import zipfile
from embed_notices import stored_jar
from upstream import ROOT


def jar_variant(data, level):
    if level == 0: return stored_jar(data)
    output = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, "w") as target:
        target.comment = source.comment
        for info in source.infolist():
            packed = copy.copy(info)
            packed.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(packed, source.read(info.filename), compresslevel=level)
    return output.getvalue()


def audit(path, output):
    rows = []
    with zipfile.ZipFile(path) as source:
        classes = source.read("classes.jar")
        with zipfile.ZipFile(io.BytesIO(classes)) as jar:
            entries = {info.filename: jar.read(info.filename) for info in jar.infolist()}
        for level in [0, 6, 9]:
            before = time.perf_counter()
            inner = jar_variant(classes, level)
            image = io.BytesIO()
            with zipfile.ZipFile(image, "w") as target:
                target.comment = source.comment
                for info in source.infolist():
                    packed = copy.copy(info)
                    packed.compress_type = zipfile.ZIP_DEFLATED
                    target.writestr(packed, inner if info.filename == "classes.jar" else source.read(info.filename), compresslevel=9)
            build_ms = (time.perf_counter() - before) * 1000
            # Exercise the same two standard ZIP readers used to extract and read classes.
            before = time.perf_counter()
            with zipfile.ZipFile(image) as aar, zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as jar:
                assert jar.namelist() == list(entries)
                for name, content in entries.items(): assert jar.read(name) == content, name
            read_ms = (time.perf_counter() - before) * 1000
            rows.append({"inner_deflate_level": level, "inner_jar_bytes": len(inner),
                         "aar_bytes": len(image.getvalue()), "build_ms": build_ms, "read_all_entries_ms": read_ms})
    result = {"schema_version": 1, "method": "Identical entry bytes; stored/deflated inner JAR; outer standard DEFLATE level 9",
              "artifact_sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "entry_count": len(entries),
              "entry_sha256": {name: hashlib.sha256(content).hexdigest() for name, content in sorted(entries.items())},
              "variants": rows,
              "limitations": ["Python host ZIP timing is a packaging diagnostic, not Android startup or build performance",
                              "Stored inner JAR uses more intermediate disk space; deployed APK is measured separately",
                              "Level 6/9 alternatives are reconstructed from the same compiled payload, not historical Maven artifacts"]}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print(json.dumps(rows, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "measurements/aar-compression.json")
    args = parser.parse_args()
    path = args.artifact or max((ROOT / ".cache/maven/io/github/opencckmp/opencc-core-android/0.1.0-SNAPSHOT").glob("*.aar"), key=lambda file: file.stat().st_mtime_ns)
    audit(path, args.output)
