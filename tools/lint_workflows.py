"""Run checksum-pinned actionlint against this repository's GitHub Actions workflows."""
import hashlib
import io
import platform
import subprocess
import tarfile
import urllib.request
from upstream import ROOT

VERSION = "1.7.12"
CHECKSUMS = {
    ("Darwin", "arm64"): ("darwin_arm64", "aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f"),
    ("Linux", "x86_64"): ("linux_amd64", "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"),
}


def lint():
    architecture, checksum = CHECKSUMS[(platform.system(), platform.machine())]
    folder = ROOT / ".cache/actionlint" / (VERSION + "-" + architecture)
    archive = folder / "archive.tar.gz"
    folder.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        url = f"https://github.com/rhysd/actionlint/releases/download/v{VERSION}/actionlint_{VERSION}_{architecture}.tar.gz"
        with urllib.request.urlopen(url, timeout=90) as response:
            data = response.read()
        assert hashlib.sha256(data).hexdigest() == checksum, "Actionlint download checksum mismatch"
        archive.write_bytes(data)
    data = archive.read_bytes()
    assert hashlib.sha256(data).hexdigest() == checksum, "Actionlint cache checksum mismatch"
    binary = folder / "actionlint"
    with tarfile.open(fileobj=io.BytesIO(data)) as bundle:
        with bundle.extractfile("actionlint") as stream: binary.write_bytes(stream.read())
    binary.chmod(0o755)
    subprocess.run([str(binary)], cwd=ROOT, check=True)


if __name__ == "__main__": lint()
