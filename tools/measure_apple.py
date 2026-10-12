"""Build and run a Swift app linking an independently resolved release KMP consumer framework.

Uses an isolated temporary simulator, verifies bundled data and measures application container
bytes. Simulator timing/RSS are evidence for this host, not physical-iPhone release gates.
"""
import argparse
import io
import json
from pathlib import Path
import platform
import shutil
import subprocess
import time
import zipfile
from measure_web import validate_resources

ROOT = Path(__file__).resolve().parents[1]
IDENTIFIER = "io.github.opencckmp.consumer.apple"
PROFILES = ("baseline", "engine", "one", "multi", "full")


def command(args, **options):
    return subprocess.run(args, text=True, check=True, **options)


def create_simulator():
    available = json.loads(command(["xcrun", "simctl", "list", "runtimes", "-j"], capture_output=True).stdout)
    runtimes = [item for item in available["runtimes"] if item.get("isAvailable") and item["name"].startswith("iOS")]
    runtime = max(runtimes, key=lambda item: tuple(map(int, item["version"].split('.'))))
    types = json.loads(command(["xcrun", "simctl", "list", "devicetypes", "-j"], capture_output=True).stdout)["devicetypes"]
    device = next(item for item in types if item["name"] == "iPhone 17 Pro")
    identity = command(["xcrun", "simctl", "create", "OpenCC temporary consumer fixture", device["identifier"], runtime["identifier"]], capture_output=True).stdout.strip()
    try:
        command(["xcrun", "simctl", "boot", identity], capture_output=True)
        command(["xcrun", "simctl", "bootstatus", identity, "-b"])
    except BaseException:
        subprocess.run(["xcrun", "simctl", "delete", identity], capture_output=True)
        raise
    return identity, runtime["name"], device["name"]


def app_sizes(app, profile):
    files = {file.relative_to(app).as_posix(): file.read_bytes() for file in sorted(app.rglob('*')) if file.is_file()}
    validate_resources(files, profile)
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, data in files.items():
            target.writestr("Payload/Consumer.app/" + name, data)
    assert not any(name.endswith('.zip') for name in files)
    return {"application_raw_bytes": sum(map(len, files.values())), "zip_container_bytes": len(archive.getvalue()),
            "executable_bytes": len(files["Consumer"]), "data_raw_bytes": sum(len(value) for name, value in files.items() if name.startswith("opencc/"))}


def build_app(profile, small):
    build_profile = profile + ("-small" if small else "")
    command([str(ROOT / "gradlew"), "-p", str(ROOT / "consumers"), f"-Popencc.profile={profile}",
             f"-Popencc.native.smallBinary={str(small).lower()}", ":kit:stageResources", ":kit:linkReleaseFrameworkIosSimulatorArm64"])
    kit = ROOT / f"consumers/build/profiles/{build_profile}/kit"
    framework = kit / "bin/iosSimulatorArm64/releaseFramework"
    xcode = ROOT / f".cache/consumer-xcode/{build_profile}"
    log = ROOT / f".cache/consumer-xcode/{build_profile}.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open('w') as stream:
        result = subprocess.run(["xcodebuild", "-project", str(ROOT / "consumers/appleApp/Consumer.xcodeproj"), "-scheme", "Consumer",
                    "-configuration", "Release", "-sdk", "iphonesimulator", "-derivedDataPath", str(xcode),
                    "ARCHS=arm64", "ONLY_ACTIVE_ARCH=YES", "CODE_SIGNING_ALLOWED=NO", f"OPENCC_FRAMEWORK_DIR={framework}"],
                    text=True, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(log.read_text()[-10000:])
    app = xcode / "Build/Products/Release-iphonesimulator/Consumer.app"
    data = app / "opencc"
    if data.exists():
        shutil.rmtree(data)
    bundled = kit / "fixture.bundle/opencc"
    if bundled.exists():
        shutil.copytree(bundled, data)
    return app


def run_app(device, app, profile):
    subprocess.run(["xcrun", "simctl", "uninstall", device, IDENTIFIER], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    command(["xcrun", "simctl", "install", device, str(app)], capture_output=True)
    container = Path(command(["xcrun", "simctl", "get_app_container", device, IDENTIFIER, "data"], capture_output=True).stdout.strip())
    preference = container / f"Library/Preferences/{IDENTIFIER}.plist"
    command(["xcrun", "simctl", "launch", "--terminate-running-process", device, IDENTIFIER], capture_output=True)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if preference.exists():
            result = subprocess.run(["plutil", "-extract", "OpenCCFixtureReport", "raw", "-o", "-", str(preference)], text=True, capture_output=True)
            if result.returncode == 0 and result.stdout.strip():
                report = json.loads(result.stdout)
                assert report["status"] == "pass" and report["output"] == "漢字轉換", report
                assert report["profile"] == profile
                return report
        time.sleep(0.05)
    raise RuntimeError(f"App did not publish a report: {preference}")


def measure(output, profiles, small):
    device = None
    try:
        device, runtime, model = create_simulator()
        results = {}
        for profile in profiles:
            app = build_app(profile, small)
            report = app_sizes(app, profile)
            report["runs"] = [run_app(device, app, profile) for _ in range(3)]
            results[profile] = report
        if "baseline" in results:
            for report in results.values():
                for key in ("application_raw_bytes", "zip_container_bytes", "executable_bytes"):
                    report["incremental_" + key] = report[key] - results["baseline"][key]
        report = {"schema_version": 1, "environment": {"host": platform.platform(), "machine": platform.machine(),
                  "simulator": model, "runtime": runtime, "kotlin": "2.4.0", "smallBinary": small,
                  "xcode": command(["xcodebuild", "-version"], capture_output=True).stdout.strip()},
                  "limitations": ["Simulator arm64 release app, not an App Store/device-specific download measurement",
                                   "Three launches per profile; not a statistically robust physical-device p95",
                                   "sampled_memory_bytes is Darwin process peak RSS, including UIKit/system/runtime overhead"], "profiles": results}
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, sort_keys=True, indent=2) + '\n')
        print(json.dumps({profile: {key: row[key] for key in ("application_raw_bytes", "zip_container_bytes", "executable_bytes")}
                          for profile, row in results.items()}, indent=2))
        return report
    finally:
        if device:
            subprocess.run(["xcrun", "simctl", "shutdown", device], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            command(["xcrun", "simctl", "delete", device], capture_output=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "measurements/apple-initial.json")
    parser.add_argument("--profiles", default=','.join(PROFILES))
    parser.add_argument("--small-binary", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check and args.output == ROOT / "measurements/apple-initial.json":
        args.output = ROOT / ".cache/measurements/apple-current.json"
    report = measure(args.output, args.profiles.split(','), args.small_binary)
    if args.check:
        rules = json.loads((ROOT / "measurements/apple-budgets.json").read_text())
        baseline = json.loads((ROOT / "measurements" / rules["baseline"]).read_text())
        for profile in PROFILES[1:]:
            metric = rules["metric"]
            limit = int(baseline["profiles"][profile][metric] * (1 + rules["relative_allowance"])) + rules["absolute_allowance_bytes"]
            assert report["profiles"][profile][metric] <= limit, f"Size regression: {profile}"
