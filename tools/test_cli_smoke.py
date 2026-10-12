"""Execute locally built JVM/Native tool entry points with Unicode files, independent of an oracle.

Used on Windows where POSIX differential-probe infrastructure is unavailable. This is a smoke
check, not a substitute for the process-contract matrix against the pinned reference.
"""
import argparse
import json
import subprocess
import tempfile
from pathlib import Path

from test_cli import ROOT, TOOLS


def verify(native_target):
    classpath = (ROOT / "opencc-cli/build/reports/jvm-classpath.txt").read_text()
    extension = ".exe" if native_target == "mingwX64" else ".kexe"
    with tempfile.TemporaryDirectory(prefix="opencc-cli-smoke-") as directory:
        folder = Path(directory)
        config = folder / "配置.json"
        config.write_text(json.dumps({"name": "smoke", "conversion_chain": [
            {"dict": {"type": "inline", "entries": {"汉": "漢", "字": "字"}}}]}), encoding="utf-8")
        source = folder / "输入.txt"
        source.write_bytes("汉字\n".encode())
        checks = 0
        for backend in ["jvm", "native"]:
            def run(tool, arguments, data=b""):
                command = ["java", "-cp", classpath, TOOLS[tool]] if backend == "jvm" else [
                    str(ROOT / "opencc-cli/build/bin" / native_target / (tool + "ReleaseExecutable") / (tool + extension))]
                result = subprocess.run(command + arguments, input=data, capture_output=True, cwd=folder)
                assert result.returncode == 0, (backend, tool, arguments, result.stderr)
                return result.stdout

            for tool in TOOLS:
                assert b"1.4.2" in run(tool, ["--version"])
                assert b"usage:" in run(tool, ["--help"]).lower()
                checks += 2
            assert run("opencc", ["-c", str(config)], "汉字𠀀\n".encode()) == "漢字𠀀\n".encode()
            output = folder / (backend + "输出.txt")
            assert run("opencc", ["-c", str(config), "-i", str(source), "-o", str(output)]) == b""
            assert output.read_bytes() == "漢字\n".encode()
            assert run("opencc", ["-c", str(config), "--in-place", "-i", str(output), "-o", str(output)]) == b""
            assert output.read_bytes() == "漢字\n".encode()
            dictionary = folder / "词典.txt"
            dictionary.write_bytes("汉\t漢\n里\t里 裏\n".encode())
            image = folder / (backend + ".ocd2")
            decoded = folder / (backend + ".txt")
            run("opencc_dict", ["-i", str(dictionary), "-o", str(image), "-f", "text", "-t", "ocd2"])
            run("opencc_dict", ["-i", str(image), "-o", str(decoded), "-f", "ocd2", "-t", "text"])
            assert decoded.read_bytes() == dictionary.read_bytes()
            checks += 5
        print(json.dumps({"native_target": native_target, "checks": checks}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-target", required=True, choices=["macosArm64", "macosX64", "linuxX64", "mingwX64"])
    verify(parser.parse_args().native_target)
