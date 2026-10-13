"""Exact crafted binary cases, with explicit Kotlin legacy-export adaptation."""
CASE_REVIEWS = {}
CORE = 'opencc-core/src/commonTest/kotlin/io/github/opencckmp/BinaryMalformedContractsTest.kt'


def review(suite, name, method, detail):
    CASE_REVIEWS[(suite, name)] = {
        'port_review': 'reviewed-equivalent',
        'evidence': [{'test': 'BinaryMalformedContractsTest.' + method, 'file': CORE},
                     {'test': 'main', 'file': 'tools/audit_binary_malformed.py'}],
        'contract_adaptation': detail + ' Exact native fields are independently checked through both FILE and owned-buffer readers; Kotlin uses owned ByteArray input and rejects impossible bounds with InvalidFormat before allocating declared storage.'}


for name, method, detail in [
    ('RejectsHugeKeyTotalLength', 'binaryRejectsHugeKeyTotalLength', 'numItems=1; empty key buffer; declared length 0x70000000/0x7000000000 in current/legacy layouts; no item records.'),
    ('RejectsHugeValueTotalLength', 'binaryRejectsHugeValueTotalLength', 'key buffer k+NUL; empty value buffer; declared length 0x70000000/0x7000000000 in current/legacy layouts; no item records.'),
    ('RejectsKeyOffsetOutOfBounds', 'binaryRejectsKeyOffsetOutOfBounds', 'One hi/v entry; key offset 100 exceeds three-byte key buffer.'),
    ('RejectsValueOffsetOutOfBounds', 'binaryRejectsValueOffsetOutOfBounds', 'One hi/v entry; value offset 100 exceeds two-byte value buffer.'),
    ('AcceptsWellFormedFile', 'binaryAcceptsWellFormedFile', 'Fixed-width one-item hi/v payload with both offsets zero; complete key and candidate data asserted.'),
    ('AcceptsLegacy64BitLayout', 'binaryAcceptsLegacy64BitLayout', 'Identical hi/v payload with every integer widened to uint64; complete key and candidate data asserted.'),
]:
    review('BinaryDictTest', name, method, detail)

for name, method, detail in [
    ('RejectsValueOffsetOutOfBounds', 'serializedValuesRejectsValueOffsetOutOfBounds', 'Four-byte ab+NUL+NUL buffer and claimed ten-byte value.'),
    ('RejectsValueNotNullTerminated', 'serializedValuesRejectsValueNotNullTerminated', 'Four-byte abc+NUL buffer and claimed three-byte value with non-NUL final byte.'),
    ('RejectsZeroValueBytes', 'serializedValuesRejectsZeroValueBytes', 'a+NUL buffer and zero-byte value length.'),
    ('RejectsCumulativeOffsetOverflow', 'serializedValuesRejectsCumulativeOffsetOverflow', 'Six-byte ab+NUL+cd+NUL buffer with lengths 3,5 exceeding cumulative remaining bytes.'),
    ('AcceptsWellFormedFile', 'serializedValuesAcceptsWellFormedFile', 'Two entries with ab/cd candidates, each claimed length three; complete values and consumed bytes asserted.'),
    ('RejectsHugeValueTotalLength', 'serializedValuesRejectsHugeValueTotalLength', 'One item, declared total length 0xffffffff and no backing bytes/item records.'),
]:
    review('SerializedValuesTest', name, method, detail)

for name, method, detail in [
    ('RejectsHugeDartsSize', 'dartsRejectsHugeDartsSize', 'OPENCCDARTS1 header, uint32 size 0x13000000 and fake root 0x400.'),
    ('RejectsMisalignedDartsSize', 'dartsRejectsMisalignedDartsSize', 'OPENCCDARTS1 header, uint32 size one, fake root 0x400 and trailing zero byte.'),
    ('RejectsInvalidDartsRoot', 'dartsRejectsInvalidDartsRoot', 'Verified native current image first unit at offset 16 replaced with zero.'),
    ('RejectsInvalidDartsValue', 'dartsRejectsInvalidDartsValue', 'Verified native current image first terminal unit (unit & 0x800000ff > 255) replaced with 0xffffffff.'),
    ('LoadsLegacy64bitFormat', 'dartsLoadsLegacy64bitFormat', 'Verified native fixture units and every BinaryDict field widened exactly as upstream; complete ordered lexicon and each exact/longest-prefix hit asserted.'),
    ('RejectsInvalidLegacy64Root', 'dartsRejectsInvalidLegacy64Root', 'Verified native legacy image first eight-byte unit at offset 20 replaced with zero.'),
    ('Legacy64PrefixSearchBeyond64Matches', 'dartsLegacy64PrefixSearchBeyond64Matches', 'Native-generated a through a*65 keys with v1 through v65 candidates, widened to legacy; longest match v65 and all 65 descending prefixes asserted.'),
]:
    review('DartsDictTest', name, method, detail)
review('MarisaDictTest', 'RejectsCorruptTrieData', 'marisaRejectsCorruptTrieData',
       'OPENCC_MARISA_0.2.5 header followed by exactly 128 bytes of 0xff.')

CASE_REVIEWS[('DartsDictTest', 'Legacy64SerializeToFileThrows')] = {
    'port_review': 'reviewed-equivalent',
    'evidence': [
        {'test': 'LegacyDartsExportContractTest.legacyReaderToWriterRebuildsFixedWidthCurrentImage',
         'file': 'opencc-dictionary-tools/src/commonTest/kotlin/io/github/opencckmp/tools/LegacyDartsExportContractTest.kt'},
        {'test': 'UpstreamWriterTestLegacyDarts.legacyLoadedDictionaryReencodesCurrentLayoutAndNativeReaderPreservesEntries',
         'file': 'opencc-dictionary-tools/src/jvmTest/kotlin/io/github/opencckmp/tools/LegacyDartsExportTest.kt'},
        {'test': 'main', 'file': 'tools/audit_binary_malformed.py'}],
    'contract_adaptation': 'Explicit idiomatic adaptation, not literal error parity: native refuses serialization of retained legacy Darts storage. Kotlin DictionaryWriters.ocd reconstructs a current fixed-uint32 interoperable image from an immutable loaded Dict; common tests assert actual field width, total payload size, ordered keys/candidates, lookups and conversion, and native CLI reads the generated image. No source-pointer flag is retained in core merely to reproduce the C++ storage limitation.'}
