"""Execute locally built JVM/Native tool entry points with Unicode files, independent of an oracle.

Used on Windows where POSIX differential-probe infrastructure is unavailable. This is a smoke
check, not a substitute for the process-contract matrix against the pinned reference.
"""
import argparse
import json
import os
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
        windows_identity = dict.fromkeys(["real_file", "symlink", "long_path"], "SKIP non-Windows host")
        for backend in ["jvm", "native"]:
            def run(tool, arguments, data=b"", expected_status=0):
                command = ["java", "-cp", classpath, TOOLS[tool]] if backend == "jvm" else [
                    str(ROOT / "opencc-cli/build/bin" / native_target / (tool + "ReleaseExecutable") / (tool + extension))]
                result = subprocess.run(command + arguments, input=data, capture_output=True, cwd=folder)
                assert result.returncode == expected_status, (backend, tool, arguments, result.stderr)
                if expected_status:
                    assert b"cannot verify the file's hard-link count" in result.stderr
                    assert b"Native Windows CLI" in result.stderr
                return result.stdout

            for tool in TOOLS:
                assert b"1.4.2" in run(tool, ["--version"])
                assert b"usage:" in run(tool, ["--help"]).lower()
                checks += 2
            assert run("opencc", ["-c", str(config)], "汉字𠀀\n".encode()) == "漢字𠀀\n".encode()
            output = folder / (backend + "输出.txt")
            assert run("opencc", ["-c", str(config), "-i", str(source), "-o", str(output)]) == b""
            assert output.read_bytes() == "漢字\n".encode()
            assert run("opencc", ["-c", str(config), "--in-place", "-i", str(output), "-o", str(output)],
                       expected_status=1 if backend == "jvm" and os.name == "nt" else 0) == b""
            assert output.read_bytes() == "漢字\n".encode()
            if backend == "native" and os.name == "nt":
                target = folder / "实际文件.txt"
                target.write_bytes("汉字\n".encode())
                spelling_directory = folder / "路径"
                spelling_directory.mkdir()
                alternate = str(spelling_directory) + os.sep + ".." + os.sep + target.name
                assert run("opencc", ["-c", str(config), "--in-place", "-i", str(target),
                                      "-o", alternate]) == b""
                assert target.read_bytes() == "漢字\n".encode()
                checks += 1
                windows_identity["real_file"] = "PASS canonical spelling and in-place replacement"
                link = folder / "链接.txt"
                try:
                    link.symlink_to(target.name)
                except OSError as error:
                    windows_identity["symlink"] = "SKIP symlink creation unavailable: " + str(error)
                else:
                    target.write_bytes("汉字\n".encode())
                    link_destination = os.readlink(link)
                    assert run("opencc", ["-c", str(config), "--in-place", "-i", str(target),
                                          "-o", str(link)]) == b""
                    assert target.read_bytes() == "漢字\n".encode()
                    assert link.is_symlink() and os.readlink(link) == link_destination
                    assert link.read_bytes() == target.read_bytes()
                    checks += 1
                    windows_identity["symlink"] = "PASS target converted and symlink retained"
                try:
                    long_directory = Path("\\\\?\\" + str(folder.resolve()))
                    for index in range(8):
                        long_directory /= "长路径" * 12 + str(index)
                        long_directory.mkdir()
                    long_target = long_directory / "输入输出.txt"
                    long_target.write_bytes("汉字\n".encode())
                except OSError as error:
                    windows_identity["long_path"] = "SKIP extended Unicode path creation unavailable: " + str(error)
                else:
                    assert len(str(long_target)) > 260
                    assert run("opencc", ["-c", str(config), "--in-place", "-i", str(long_target),
                                          "-o", str(long_target)]) == b""
                    assert long_target.read_bytes() == "漢字\n".encode()
                    assert not list(long_directory.glob(".opencc-*.tmp"))
                    checks += 1
                    windows_identity["long_path"] = "PASS extended Unicode path in-place replacement and cleanup"
            dictionary = folder / "词典.txt"
            dictionary.write_bytes("汉\t漢\n里\t里 裏\n".encode())
            image = folder / (backend + ".ocd2")
            decoded = folder / (backend + ".txt")
            run("opencc_dict", ["-i", str(dictionary), "-o", str(image), "-f", "text", "-t", "ocd2"])
            run("opencc_dict", ["-i", str(image), "-o", str(decoded), "-f", "ocd2", "-t", "text"])
            assert decoded.read_bytes() == dictionary.read_bytes()
            checks += 5
        print(json.dumps({"native_target": native_target, "checks": checks,
                          "windows_native_identity": windows_identity}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-target", required=True, choices=["macosArm64", "macosX64", "linuxX64", "mingwX64"])
    verify(parser.parse_args().native_target)
