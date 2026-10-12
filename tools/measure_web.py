"""Measure independent production JS/Wasm app profiles and execute them in headless Chrome."""
import argparse
import functools
import gzip
import hashlib
import http.server
import io
import json
from pathlib import Path
import platform
import subprocess
import threading
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROFILES = ("baseline", "engine", "one", "multi", "full")


def distribution_files(directory):
    return {file.relative_to(directory).as_posix(): file.read_bytes() for file in sorted(directory.rglob("*")) if file.is_file()}


def validate_resources(files, profile):
    if profile in ("baseline", "engine"):
        assert not any(name.startswith("opencc/") for name in files)
        return
    manifest = json.loads(files["opencc/manifest.json"])
    expected = {"opencc/" + name for name in manifest["files"]} | {"opencc/manifest.json"}
    assert {name for name in files if name.startswith("opencc/")} == expected
    for name, spec in manifest["files"].items():
        content = files["opencc/" + name]
        assert len(content) == spec["size"] and hashlib.sha256(content).hexdigest() == spec["sha256"], name
    if profile == "one":
        assert sorted(manifest["configs"]) == ["s2t"]
    elif profile == "multi":
        assert sorted(manifest["configs"]) == ["s2t", "t2s"]
    else:
        assert len(manifest["configs"]) == 23


def sizes(files):
    assert not any(name.endswith((".map", ".zip")) for name in files), "Debug maps or duplicated data archive in production app"
    categories = {"code": {}, "data": {}, "other": {}}
    for name, content in files.items():
        category = "data" if name.startswith("opencc/") else "code" if name.endswith((".js", ".wasm", ".mjs")) else "other"
        categories[category][name] = content
    report = {category: {"raw_bytes": sum(map(len, entries.values())),
                         "gzip_bytes": sum(len(gzip.compress(value, compresslevel=9, mtime=0)) for value in entries.values())}
              for category, entries in categories.items()}
    report["raw_bytes"] = sum(len(value) for value in files.values())
    report["gzip_transport_bytes"] = sum(len(gzip.compress(value, compresslevel=9, mtime=0)) for value in files.values())
    image = io.BytesIO()
    with zipfile.ZipFile(image, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in files.items():
            archive.writestr(name, content)
    report["zip_container_bytes"] = len(image.getvalue())
    report["files"] = {name: {"bytes": len(value), "sha256": hashlib.sha256(value).hexdigest()} for name, value in files.items()}
    return report


class CompressedApp(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        name = self.path.split('?', 1)[0].lstrip('/') or "index.html"
        item = self.server.compressed.get(name)
        if item is None:
            self.send_error(404)
            return
        self.server.delivered.append((name, len(item)))
        self.send_response(200)
        self.send_header("Content-Type", "application/wasm" if name.endswith('.wasm') else "text/javascript" if name.endswith(('.js', '.mjs')) else "text/html" if name.endswith('.html') else "application/octet-stream")
        self.send_header("Content-Encoding", "gzip")
        self.send_header("Content-Length", str(len(item)))
        self.end_headers()
        self.wfile.write(item)

    def log_message(self, *_):
        pass


def run_browser(files, chrome):
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), CompressedApp)
    server.compressed = {name: gzip.compress(value, compresslevel=9, mtime=0) for name, value in files.items()}
    server.delivered = []
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        result = subprocess.run(["node", str(ROOT / "tools/browser_probe.mjs"), f"http://127.0.0.1:{server.server_port}/", str(chrome)],
                                text=True, capture_output=True, timeout=90)
        assert result.returncode == 0, result.stdout + result.stderr
        report = json.loads(result.stdout)
        report["actual_http_payload_bytes"] = sum(size for _, size in server.delivered)
        report["actual_http_files"] = [name for name, _ in server.delivered]
        return report
    finally:
        server.shutdown()
        server.server_close()


def measure(output, profiles, chrome):
    results = {}
    for profile in profiles:
        subprocess.run([str(ROOT / "gradlew"), "-p", str(ROOT / "consumers"), f"-Popencc.profile={profile}",
                        ":kit:jsBrowserDistribution", ":kit:wasmJsBrowserDistribution"], check=True)
        root = ROOT / f"consumers/build/profiles/{profile}/kit/dist"
        results[profile] = {}
        for target in ("js", "wasmJs"):
            files = distribution_files(root / target / "productionExecutable")
            assert files, f"Missing {target} distribution"
            validate_resources(files, profile)
            report = sizes(files)
            report["runs"] = [run_browser(files, chrome) for _ in range(3)]
            results[profile][target] = report
    if "baseline" in results:
        for row in results.values():
            for target, report in row.items():
                report["incremental_gzip_transport_bytes"] = report["gzip_transport_bytes"] - results["baseline"][target]["gzip_transport_bytes"]
                report["incremental_raw_bytes"] = report["raw_bytes"] - results["baseline"][target]["raw_bytes"]
    report = {"schema_version": 1, "environment": {"host": platform.platform(), "machine": platform.machine(),
              "kotlin": "2.4.0", "webpack": "5.101.3", "compression": "individual files gzip level 9; browser decodes transport; no engine decoder"},
              "limitations": ["Three cold browser navigations per profile; not a statistically robust device/browser p95",
                               "Browser memory is sampled JS heap and excludes unmeasured process/Wasm linear-memory peaks",
                               "Full packaged data size and lazily requested initial resources are reported separately"], "profiles": results}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, sort_keys=True, indent=2) + '\n')
    print(json.dumps({profile: {target: {key: value[key] for key in ('raw_bytes', 'gzip_transport_bytes')}
                              for target, value in row.items()} for profile, row in results.items()}, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "measurements/web-initial.json")
    parser.add_argument("--profiles", default=",".join(PROFILES))
    parser.add_argument("--chrome", type=Path, default=Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check and args.output == ROOT / "measurements/web-initial.json":
        args.output = ROOT / ".cache/measurements/web-current.json"
    report = measure(args.output, args.profiles.split(','), args.chrome)
    if args.check:
        rules = json.loads((ROOT / "measurements/web-budgets.json").read_text())
        baseline = json.loads((ROOT / "measurements" / rules["baseline"]).read_text())
        for profile in PROFILES[1:]:
            for target in ("js", "wasmJs"):
                metric = rules["metric"]
                limit = int(baseline["profiles"][profile][target][metric] * (1 + rules["relative_allowance"])) + rules["absolute_allowance_bytes"]
                assert report["profiles"][profile][target][metric] <= limit, f"Size regression: {profile}/{target}"
