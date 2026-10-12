"""Exercise independent Maven consumers against published resources and local HTTP delivery."""
import argparse
import functools
import http.server
import os
from pathlib import Path
import subprocess
import threading

ROOT = Path(__file__).resolve().parents[1]


class CorsResources(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.server.requests.append(self.path)
        super().do_GET()

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def log_message(self, *_):
        pass


def verify(profile, tasks):
    base = [str(ROOT / "gradlew"), "-p", str(ROOT / "consumers"), f"-Popencc.profile={profile}"]
    subprocess.run(base + [":kit:stageResources"], check=True)
    directory = ROOT / f"consumers/build/profiles/{profile}/kit/fixture.bundle"
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 8765), functools.partial(CorsResources, directory=str(directory)))
    server.requests = []
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    environment = os.environ.copy()
    environment["OPENCC_FIXTURE_DATA"] = str(directory / "opencc")
    chrome = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
    if chrome.exists():
        environment["CHROME_BIN"] = str(chrome)
    try:
        subprocess.run(base + tasks, env=environment, check=True)
        web_tests = sum(task.endswith(("jsNodeTest", "wasmJsNodeTest", "jsBrowserTest", "wasmJsBrowserTest")) for task in tasks)
        if web_tests:
            assert server.requests.count("/opencc/missing.ocd2") == web_tests, server.requests
            assert server.requests.count("/opencc/s2t.json") == web_tests, server.requests
        print(f"Published consumers verified; {len(server.requests)} actual HTTP requests")
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", default="one", choices=["one", "multi", "full"])
    parser.add_argument("tasks", nargs="*", default=[":kit:jvmTest", ":kit:jsNodeTest", ":kit:wasmJsNodeTest", ":kit:iosSimulatorArm64Test", ":android:assembleRelease"])
    args = parser.parse_args()
    verify(args.profile, args.tasks)
