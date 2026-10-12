# Dictionary interoperability

The reference is OpenCC 1.4.2. Runtime readers are in core; builders, writers and conversion
tools are in `opencc-dictionary-tools`. Importing core does not retain the builders.

| Format | Accepted layouts | Writer |
| --- | --- | --- |
| text | UTF-8, tab between key/candidates, ASCII spaces between ordered candidates | UTF-8 with LF; current lexicon order |
| text_space | UTF-8, first ASCII space between key/candidates | Input only, matching upstream |
| ocd | OPENCCDARTS1 header, current uint32 fields and legacy uint64 fields; little/big endian | Current uint32; explicit byte order, little endian default |
| ocd2 | OPENCC_MARISA_0.2.5 container, MARISA nested LOUDS, text/binary tails; little/big endian with 32/64-bit packed words | Upstream defaults: three tries, weight order, normal cache; explicit endian and word size, LE64 default |
| cppjieba_utf8 | Base file plus optional user files; native token/metadata/override rules | Input only, matching upstream |

`DartsDict.fromBytes` and `MarisaDict.fromBytes` detect byte order. MARISA also detects packed word
layout; callers may specify both explicitly. MARISA LE32 and LE64 have the same bytes because the
native 32-bit vectors pad their word pairs. BE32 stores the low word before the high word, with each
word big endian; BE64 stores one big-endian uint64. Vector lengths, rank tables, cache records and
value counts have their own fixed-width fields and must not be swapped as opaque whole images.

`DictionaryWriters.ocd2(dictionary, byteOrder, wordSize)` chooses the binary image layout, not Kotlin
data types or client ownership. Both writers retain all candidates and native ID ordering. Native
serialization limits (uint16 candidate lengths/counts for ocd2, uint32 sizes for current ocd) fail
with typed `InvalidFormat` before returning an image. NUL bytes in dictionary keys are rejected by
the writers because OpenCC's text dictionary keys use C-string semantics.

The original native LE64 images and Kotlin outputs are compared byte for byte for all 22 built-in
dictionaries and the merged Jieba dictionary. Native OpenCC loads Kotlin-generated built-in images;
26 varied custom dictionaries exercise both deterministic writers. Endian/word variants derive
independently from the native graphs using `tools/marisa_layout.py`; all 88 built-in variants match
the Kotlin writer and load with identical keys/candidates. Common tests load an independently
translated BE32 image on JVM, JS, Wasm, macOS and the iOS simulator, as well as current/legacy Darts
layouts. A native big-endian machine and a native 32-bit machine have not been used for these checks.

Upstream's direct ocd2 → ocd path can fail when the MARISA lexicon IDs are not lexically ordered.
Kotlin sorts keys before building Darts; the reference fixture uses native ocd2 → text → ocd.
This makes the operation useful to Kotlin clients while preserving dictionary entries, candidates
and lookup behaviour. It does not reproduce the upstream tool's sorting defect.
