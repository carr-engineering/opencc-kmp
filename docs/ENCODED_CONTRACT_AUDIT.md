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
first reference run yielded 5,760 successes and 72 errors. This is native-reference evidence, not
Kotlin conformance. The current Kotlin encoded API uses strict decoding and has identified
differences that must be corrected before declaring parity.

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

This corpus starts the next differential implementation checks. Original binary dictionary readers,
nested groups, encoded streaming/inspection and C-facade operations need additional cases before the
full encoded-input contract can be marked complete.
