"""Measure release APK profiles using independent, published-artifact Android consumers.

This measures host-built APK bytes and dependency artifact downloads. It does not claim Play
delivery, device performance or memory measurements; those require their own fixtures/gates.
"""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROFILES = ("baseline", "engine", "one", "multi", "full")


def inspect_apk(path, profile):
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        assert len(names) == len(set(names)), "Duplicate APK entries"
        assert not any(name.startswith("lib/") for name in names), "Unexpected native runtime dependency"
        assert not any(name.endswith(".zip") for name in names), "Data archive duplicated inside APK"
        assets = [item for item in apk.infolist() if item.filename.startswith("assets/opencc/")]
        if profile in ("one", "multi", "full"):
            manifest = json.loads(apk.read("assets/opencc/manifest.json"))
            expected = set(manifest["files"]) | {"manifest.json"}
            assert {item.filename.removeprefix("assets/opencc/") for item in assets} == expected
            for name, spec in manifest["files"].items():
                data = apk.read("assets/opencc/" + name)
                assert len(data) == spec["size"] and hashlib.sha256(data).hexdigest() == spec["sha256"], name
            configs = sorted(manifest["configs"])
            if profile == "one":
                assert configs == ["s2t"]
            elif profile == "multi":
                assert configs == ["s2t", "t2s"]
            else:
                assert len(configs) == 23
            images = sorted(name for name in expected if name.endswith(".ocd2"))
        else:
            assert not assets, "Engine/baseline must not include data"
            configs, images = [], []
        dex = [item for item in apk.infolist() if item.filename.endswith(".dex")]
        return {
            "apk_bytes": path.stat().st_size,
            "uncompressed_entry_bytes": sum(item.file_size for item in apk.infolist()),
            "data_raw_bytes": sum(item.file_size for item in assets),
            "data_compressed_entry_bytes": sum(item.compress_size for item in assets),
            "dex_raw_bytes": sum(item.file_size for item in dex),
            "dex_compressed_entry_bytes": sum(item.compress_size for item in dex),
            "configs": configs, "dictionaries": images,
        }


def measure(output):
    results = {}
    target = ROOT / ".cache/measurements/android"
    target.mkdir(parents=True, exist_ok=True)
    for profile in PROFILES:
        subprocess.run([str(ROOT / "gradlew"), "-p", str(ROOT / "consumers"), f"-Popencc.profile={profile}",
                        ":android:assembleRelease", ":android:reportDependencyDownloads"], check=True)
        source = ROOT / "consumers/android/build/outputs/apk/release/android-release.apk"
        artifact = target / f"{profile}.apk"
        shutil.copyfile(source, artifact)
        result = inspect_apk(artifact, profile)
        dependencies = json.loads((ROOT / "consumers/android/build/reports/dependency-downloads.json").read_text())
        library_modules = {item["coordinate"].split(':')[1] for item in dependencies
                           if item["coordinate"].startswith("io.github.opencckmp:")}
        expected_modules = set() if profile == "baseline" else {"opencc-core-android"}
        if profile in ("one", "multi", "full"):
            packs = {"one": "opencc-data-s2t", "multi": "opencc-data-s2t-t2s", "full": "opencc-data"}
            expected_modules |= {"opencc-platform-android", packs[profile]}
        assert library_modules == expected_modules, (profile, library_modules, expected_modules)
        result["dependencies"] = dependencies
        result["dependency_artifact_bytes"] = sum(item["bytes"] for item in dependencies)
        results[profile] = result
    for profile, result in results.items():
        result["incremental_apk_bytes"] = result["apk_bytes"] - results["baseline"]["apk_bytes"]
        result["incremental_dependency_artifact_bytes"] = result["dependency_artifact_bytes"] - results["baseline"]["dependency_artifact_bytes"]
    report = {
        "schema_version": 1,
        "environment": {"host": platform.platform(), "machine": platform.machine(), "kotlin": "2.4.0",
                        "gradle": "8.14.3", "agp": "8.13.2", "compile_sdk": 36, "min_sdk": 23,
                        "shrinking": "R8, default proguard-android-optimize; no OpenCC keep rules"},
        "method": "Signed release APK; ZIP compressed entries; identical Activity and Kotlin baseline; published Maven modules",
        "limitations": ["APK measurement is not Play/device-specific download size", "Maven metadata/build-tool downloads excluded",
                        "Device startup, peak memory and performance not measured in this report"],
        "profiles": results,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
    print(json.dumps({name: {key: row[key] for key in ("apk_bytes", "incremental_apk_bytes", "dependency_artifact_bytes")}
                      for name, row in results.items()}, indent=2))
    return report


def check_budgets(report):
    rules = json.loads((ROOT / "measurements/android-budgets.json").read_text())
    baseline = json.loads((ROOT / "measurements" / rules["baseline"]).read_text())
    for profile in PROFILES[1:]:
        for metric in ("incremental_apk_bytes", "incremental_dependency_artifact_bytes"):
            previous = baseline["profiles"][profile][metric]
            limit = int(previous * (1 + rules["relative_allowance"])) + rules["absolute_allowance_bytes"]
            actual = report["profiles"][profile][metric]
            assert actual <= limit, f"{profile}/{metric}: {actual} exceeds measured regression budget {limit}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "measurements/android-initial.json")
    parser.add_argument("--check", action="store_true", help="Compare current measurements with checked-in regression budgets")
    args = parser.parse_args()
    if args.check and args.output == ROOT / "measurements/android-initial.json":
        args.output = ROOT / ".cache/measurements/android-current.json"
    report = measure(args.output)
    if args.check:
        check_budgets(report)
