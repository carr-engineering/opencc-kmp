# Binary corruption and legacy interoperability

The crafted cases in `BinaryDictTest`, `SerializedValuesTest`, `DartsDictTest` and
`MarisaDictTest` from pinned OpenCC `ver.1.4.2` are reproduced with their actual
integer widths, declared lengths, buffers and offsets. `tools/audit_binary_malformed.py`
checks 33 profiles through both native FILE and buffer loaders: 15 accepted and
18 `InvalidFormat` results, giving 66 independent loader observations. No process
termination occurred for these bounded inputs. This is a specific regression
matrix, not a claim that every malformed binary input has identical native behaviour.

The matrix includes all nine existing native zero-candidate images, fixed-width
12-byte empty BinaryDict input, huge declared key/value/Darts sizes with tiny
backing buffers, invalid offsets, missing terminators, zero value byte lengths,
cumulative value overflow, invalid Darts roots/terminal values, exactly 128 corrupt
MARISA bytes, and valid current/legacy images. The 65-nested-key Darts fixture
proves that legacy prefix search returns `v65` and exposes all 65 matches.
Generated fixtures are under test source sets and are absent from library downloads.

Kotlin receives owned `ByteArray` images. Readers check declared counts and offsets
against available storage before allocation, and report typed `InvalidFormat`
errors. Native `FILE` and allocation ownership are represented by platform resource
loading and these owned readers. The validation does not reproduce unsafe native
allocation or out-of-bounds mechanics.

Legacy export has an explicit adaptation. C++ rejects `SerializeToFile` on a Darts
object retaining legacy storage. The separate Kotlin tooling operation
`DictionaryWriters.ocd(loadedDict)` rebuilds the dictionary as a current image with
four-byte integer fields. It preserves ordered keys, candidate data, lookups and
conversion. Tests check the actual field layout and payload size, then read the
result with both Kotlin and the native CLI. Core does not retain a native legacy
pointer flag solely to impose that serialization limitation.

Reproduce the native matrix after building `binary_malformed_probe` against the
pinned library with `python3 tools/audit_binary_malformed.py`. Run common core and
dictionary-tools tests on all supported runtimes; `upstreamWriterConformance`
also executes the legacy re-export native-reader assertion.
