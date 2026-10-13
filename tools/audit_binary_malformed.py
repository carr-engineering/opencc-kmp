"""Reproduce pinned upstream binary corruption fields and legacy layouts exactly.

Each native input is isolated; native process termination is recorded separately from
InvalidFormat. Huge declared sizes have tiny backing images, never huge allocations.
Generated fixtures belong only to commonTest and are absent from published artifacts.
"""
import hashlib
import json
import re
import resource
import struct
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / '.cache/probes/binary_malformed_probe'


def integer(value, width=4):
    return value.to_bytes(width, 'little')


def binary(width=4, keys=b'hi\0', values=b'v\0', key_offset=0, value_offset=0,
           key_length=None, value_length=None, items=1, write_items=True):
    return (integer(items, width) + integer(len(keys) if key_length is None else key_length, width)
            + keys + integer(len(values) if value_length is None else value_length, width) + values
            + (integer(1, width) + integer(key_offset, width) + integer(value_offset, width) if items and write_items else b''))


def values(data, lengths, declared=None):
    return integer(len(lengths)) + integer(len(data) if declared is None else declared) + data + b''.join(
        integer(len(entry), 2) + b''.join(integer(length, 2) for length in entry) for entry in lengths)


def legacy(image):
    position = 12
    def field():
        nonlocal position
        value = struct.unpack_from('<I', image, position)[0]
        position += 4
        return value
    count = field() // 4
    result = image[:12] + integer(count * 8, 8)
    result += b''.join(integer(field(), 8) for _ in range(count))
    entries = field()
    result += integer(entries, 8)
    for _ in range(2):
        length = field()
        result += integer(length, 8) + image[position:position + length]
        position += length
    for _ in range(entries):
        length = field()
        result += integer(length, 8) + integer(field(), 8)
        result += b''.join(integer(field(), 8) for _ in range(length))
    assert position == len(image)
    return result


def profiles():
    records = []
    def add(name, operation, image, expected):
        records.append((name, operation, image, expected))
    add('binary-empty', 'binary', bytes(12), 'OK')
    for width in (4, 8):
        add(f'binary-huge-keys-{width}', 'binary', binary(width, b'', b'', key_length=0x70000000 if width == 4 else 0x7000000000, write_items=False), 'InvalidFormat')
        add(f'binary-huge-values-{width}', 'binary', binary(width, b'k\0', b'', value_length=0x70000000 if width == 4 else 0x7000000000, write_items=False), 'InvalidFormat')
        add(f'binary-valid-{width}', 'binary', binary(width), 'OK|6869:76')
    add('binary-key-offset', 'binary', binary(key_offset=100), 'InvalidFormat')
    add('binary-value-offset', 'binary', binary(value_offset=100), 'InvalidFormat')
    for name, data, lengths in [('offset', b'ab\0\0', [[10]]), ('terminator', b'abc\0', [[3]]),
                                ('zero', b'a\0', [[0]]), ('cumulative', b'ab\0cd\0', [[3, 5]])]:
        add('values-' + name, 'values', values(data, lengths), 'InvalidFormat')
    add('values-valid', 'values', values(b'ab\0cd\0', [[3], [3]]), 'OK|:6162|:6364')
    # Upstream's writer writes a count, declared length and no backing buffer/items.
    add('values-huge', 'values', integer(1) + integer(0xffffffff), 'InvalidFormat')
    add('darts-huge', 'darts', b'OPENCCDARTS1' + integer(0x13000000) + integer(0x400), 'InvalidFormat')
    add('darts-alignment', 'darts', b'OPENCCDARTS1' + integer(1) + integer(0x400) + b'\0', 'InvalidFormat')
    fixture = (ROOT / 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/BinaryTestImages.kt').read_text()
    current = bytes.fromhex(re.search(r'val ocd:.*?decode\("""(.*?)"""', fixture, re.S).group(1))
    old = legacy(current)
    for name, image, offset, width in [('root', current, 16, 4), ('legacy-root', old, 20, 8)]:
        corrupted = image[:offset] + bytes(width) + image[offset + width:]
        add('darts-' + name, 'darts', corrupted, 'InvalidFormat')
    corrupted = bytearray(current)
    size = struct.unpack_from('<I', current, 12)[0]
    for offset in range(16, 16 + size, 4):
        unit = struct.unpack_from('<I', current, offset)[0]
        if (unit & 0x800000ff) > 255:
            struct.pack_into('<I', corrupted, offset, 0xffffffff)
            break
    else:
        raise AssertionError('Fixture must have a terminal value unit')
    add('darts-value', 'darts', bytes(corrupted), 'InvalidFormat')
    # Full ordered lexicon is queried from current native image, then independently compared for legacy.
    expected = native('darts', current)
    assert expected.startswith('OK|')
    add('darts-legacy', 'darts', old, expected)
    nested = legacy(bytes.fromhex(subprocess.check_output([str(PROBE), 'make65'], text=True).strip()))
    add('darts-prefix65', 'prefix65', nested, 'OK|763635')
    add('darts-legacy-serialize', 'serialize', old, 'InvalidFormat')
    add('marisa-corrupt', 'marisa', b'OPENCC_MARISA_0.2.5' + bytes([255]) * 128, 'InvalidFormat')
    zero_source = (ROOT / 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/NativeCandidateCases.kt').read_text()
    zeros = re.search(r'nativeZeroValueCases = """(.*?)"""', zero_source, re.S).group(1).strip().splitlines()
    for index, row in enumerate(zeros):
        operation, image, status, *expected_counts = row.split('|')
        raw = bytes.fromhex(image)
        output = native(operation, raw)
        fields = output.split('|')
        actual_counts = []
        for entry in fields[1:]:
            key, candidates = entry.split(':')
            actual_counts.append(key + ':' + str(len(candidates.split(',')) if candidates else 0))
        assert fields[0] == status == 'OK' and actual_counts == expected_counts, (row, output)
        add(f'zero-{index}', operation, raw, output)
    assert len(zeros) == 9
    return records


def native(operation, image):
    def limits():
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    result = subprocess.run([str(PROBE), operation], input=image.hex() + '\n', capture_output=True,
                            text=True, timeout=10, preexec_fn=limits)
    return result.stdout.strip() if result.returncode == 0 else f'NativeTermination:{result.returncode}'


def main():
    records = profiles()
    report = []
    for name, operation, image, expected in records:
        actual = native(operation, image)
        assert actual == expected, (name, expected, actual)
        file_actual = native(operation + '-file', image)
        assert file_actual == actual, (name, 'file/buffer', actual, file_actual)
        report.append({'name': name, 'operation': operation, 'bytes': len(image),
                       'sha256': hashlib.sha256(image).hexdigest(), 'native_result': actual,
                       'native_file_result': file_actual})
    target = ROOT / 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/NativeBinaryMalformedCases.kt'
    # The large 65-key legacy image is test-only. All expected values are native observations.
    lines = ['// Generated by tools/audit_binary_malformed.py; pinned ver.1.4.2; test data only.',
             'package io.github.opencckmp', '', 'internal val nativeBinaryMalformedCases = mapOf(']
    for name, operation, image, expected in records:
        lines.append(f'    "{name}" to Triple("{operation}", "{image.hex()}", "{expected}"),')
    lines.append(')\n')
    target.write_text('\n'.join(lines))
    legacy_hex = next(image.hex() for name, _, image, _ in records if name == 'darts-legacy')
    tools_fixture = ROOT / 'opencc-dictionary-tools/src/commonTest/kotlin/io/github/opencckmp/tools/NativeLegacyDartsImage.kt'
    tools_fixture.write_text('// Generated by tools/audit_binary_malformed.py; pinned ver.1.4.2; test data only.\n'
                             'package io.github.opencckmp.tools\n\n'
                             'internal fun nativeLegacyDartsImage(): ByteArray =\n'
                             f'    "{legacy_hex}".chunked(2).map {{ it.toInt(16).toByte() }}.toByteArray()\n')
    folder = ROOT / '.cache/conformance'
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'binary-malformed-report.json').write_text(json.dumps(report, indent=2) + '\n')
    (folder / 'binary-legacy.ocd').write_bytes(next(image for name, _, image, _ in records if name == 'darts-legacy'))
    print(f'{len(report)} native profiles: {sum(r["native_result"].startswith("OK") for r in report)} accepted, '
          f'{sum(r["native_result"] == "InvalidFormat" for r in report)} InvalidFormat, no native termination')


if __name__ == '__main__':
    main()
