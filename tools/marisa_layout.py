"""Translate numeric fields in a native LE64 OpenCC image to another MARISA layout.

Test oracle only: this preserves the native builder's graph, IDs and acceleration tables;
it does not build dictionaries or call the Kotlin implementation.
"""
import argparse
from pathlib import Path


def translate(image: bytes, endian: str, word_size: int) -> bytes:
    assert endian in ("little", "big") and word_size in (32, 64)
    position = 0
    output = bytearray()

    def take(count):
        nonlocal position
        data = image[position:position + count]
        assert len(data) == count
        position += count
        return data

    def number(width):
        value = int.from_bytes(take(width), "little")
        output.extend(value.to_bytes(width, endian))
        return value

    def vector(width=None):
        length = number(8)
        data = take(length)
        if width is not None:
            assert length % width == 0
            data = b"".join(int.from_bytes(data[i:i + width], "little").to_bytes(width, endian)
                            for i in range(0, length, width))
        output.extend(data)
        output.extend(take((-length) % 8))
        return length

    def bits():
        vector(word_size // 8)
        number(4)
        ones = number(4)
        vector(4)  # rank records are three uint32 fields
        vector(4)
        vector(4)
        return ones

    def graph():
        bits()
        bits()
        links = bits()
        vector()  # node byte labels
        vector(word_size // 8)  # packed link extras
        number(4)
        number(4)
        number(8)
        tail_size = vector()
        bits()
        if links and not tail_size:
            graph()
        vector(4)  # cache records are three uint32 fields
        number(4)
        number(4)

    expected_header = b"OPENCC_MARISA_0.2.5We love Marisa.\0"
    header = take(len(expected_header))
    assert header == expected_header
    output.extend(header)
    graph()
    count = number(4)
    output.extend(take(number(4)))
    for _ in range(count):
        values = number(2)
        for _ in range(values):
            number(2)
    assert position == len(image)
    return bytes(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for file in sorted(args.source.glob("*.ocd2")):
        for endian in ("little", "big"):
            for width in (32, 64):
                (args.output / f"{file.stem}-{endian}-{width}.ocd2").write_bytes(translate(file.read_bytes(), endian, width))
