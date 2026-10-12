# Pinned encoded-input behavior audit

`tools/audit_encoded.py` builds a test-only C++ probe against verified OpenCC 1.4.2. It records 5,832
cases for explicit-length converter views, SimpleConverter prefix conversion, null-terminated input
and buffer output. Four configs cover inline conversion, max-match segmentation, normalization plus
segmentation and an empty chain. Inputs include every byte prefix of Chinese/ASCII/supplementary/IDS
examples, NULs, invalid leading bytes, partial sequences, overlong/surrogate/out-of-range encodings
and legacy five/six-byte sequences.

```sh
python3 tools/audit_encoded.py
```

The generated corpus is `.cache/conformance/encoded-cases.json`. It retains input bytes, prefix
length, operation, output bytes/reported count or exception message, config and upstream pin. The
first reference run yielded 5,760 successes and 72 errors. The expanded Kotlin differential suite
now passes 71,775 cases across 29 configs, including all 16 built-ins, native binary dictionaries,
nested groups, inspection traces and streaming. [ENCODED_API.md](ENCODED_API.md) specifies the implementation.

For the probe dictionaries (`汉 → 漢`, `字 → Z`, `abc → ABC`), these are observable upstream results:

| Encoded input/operation | Native result |
| --- | --- |
| `汉` followed by NUL and `abc字`, explicit-length unsegmented conversion | `漢` followed by NUL and `ABCZ` |
| Same input, explicit-length segmented conversion | `漢ABCZ`; unmatched segment NUL truncation removes that NUL |
| Same input, explicit-length SimpleConverter buffer conversion | `漢`; the buffer overload truncates input at NUL |
| Byte `e2`, explicit-length conversion | Preserved as byte `e2`, although it is a partial UTF-8 sequence |
| Bytes `c0 80`, `ed a0 80`, `f4 90 80 80` or `e2 61 62` | Preserved; the engine does not perform strict Unicode validation |
| Standalone byte `80`, nonempty conversion chain | InvalidUTF8 |
| Standalone byte `80`, empty unsegmented chain | Preserved; no conversion step validates it |

Native traversal primarily uses leading-byte lengths and bounds-clamps partial sequences. Kotlin
String operations retain the explicitly documented well-formed UTF-16 contract. ByteArray operations
need a faithful byte-preserving path rather than routing every input through strict Unicode decoding.
Map explicit lengths and null termination to clear Kotlin options; safe output capacities and Kotlin
ownership remain required. Native pointer/allocation mechanics are not a reason to reproduce unsafe
writes. Segmentation, normalization and error timing need separate tests because they affect bytes.

The expanded corpus covers native binary dictionaries, nested groups and encoded streaming/inspection;
portable facade tests cover byte modes/errors/close. Experimental candidate/ambiguity NUL comparisons
and composed-map checks also pass. Jieba, malformed resource formats, complete utility inventory and
production size/release gates still require their separate acceptance work.
