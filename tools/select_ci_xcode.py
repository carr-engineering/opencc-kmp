"""Select the newest installed Xcode 26.x on an Apple hosted runner, without fixed bundle names."""
import plistlib
import subprocess
from pathlib import Path


def select():
    candidates = []
    for application in Path("/Applications").glob("Xcode*.app"):
        with (application / "Contents/Info.plist").open("rb") as stream:
            version = plistlib.load(stream)["CFBundleShortVersionString"]
        parts = tuple(int(part) for part in version.split("."))
        if parts[0] == 26: candidates.append((parts, application))
    if not candidates: raise SystemExit("CI requires an installed Xcode 26.x with iOS simulator runtime")
    _, application = max(candidates)
    subprocess.run(["sudo", "xcode-select", "--switch", str(application / "Contents/Developer")], check=True)


if __name__ == "__main__": select()
