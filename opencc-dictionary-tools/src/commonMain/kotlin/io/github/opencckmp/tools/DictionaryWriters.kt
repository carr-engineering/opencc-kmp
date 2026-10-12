/* OpenCC serialization adapted from Copyright 2010-2026 Carbo Kuo and contributors. Apache-2.0. */
package io.github.opencckmp.tools

import io.github.opencckmp.*

object DictionaryWriters {
    /** Default OpenCC MARISA build (three tries, weight order, normal cache, text tails). */
    fun ocd2(dict: Dict, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): ByteArray {
        val (trie, ordered) = MarisaBuilder().build(dict.lexicon, byteOrder)
        val output = BinaryOutput(byteOrder)
        output.append("OPENCC_MARISA_0.2.5".encodeToByteArray())
        output.append(trie)
        output.append(serializedValues(ordered, byteOrder))
        return output.toByteArray()
    }
    fun text(dict: Dict): ByteArray = UTF8Util.encode(buildString {
        for (entry in dict.lexicon) { append(entry.toString()); append('\n') }
    })

    /** Current fixed uint32 format, canonical little-endian by default. */
    fun ocd(dict: Dict, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): ByteArray {
        val lexicon = dict.lexicon.sorted()
        if (!lexicon.isUnique()) throw InvalidFormat("Dictionary contains duplicate keys")
        val keys = lexicon.entries.map { UTF8Util.encode(it.key) }
        val units = DartsBuilder(keys).build()
        val output = BinaryOutput(byteOrder)
        output.append("OPENCCDARTS1".encodeToByteArray())
        output.integer(units.size.toLong() * 4, 4)
        for (unit in units) output.word32(unit)
        output.append(binaryPayload(lexicon, byteOrder))
        return output.toByteArray()
    }

    fun binaryPayload(lexicon: Lexicon, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): ByteArray {
        val keys = BinaryOutput(byteOrder)
        val values = BinaryOutput(byteOrder)
        val offsets = BinaryOutput(byteOrder)
        for (entry in lexicon) {
            offsets.integer(entry.numValues.toLong(), 4)
            offsets.integer(keys.size.toLong(), 4)
            keys.append(UTF8Util.encode(entry.key)); keys.byte(0)
            for (candidate in entry.values) {
                offsets.integer(values.size.toLong(), 4)
                values.append(UTF8Util.encode(candidate)); values.byte(0)
            }
        }
        val output = BinaryOutput(byteOrder)
        output.integer(lexicon.size.toLong(), 4)
        output.integer(keys.size.toLong(), 4); output.append(keys.toByteArray())
        output.integer(values.size.toLong(), 4); output.append(values.toByteArray())
        output.append(offsets.toByteArray())
        return output.toByteArray()
    }

    fun serializedValues(lexicon: Lexicon, byteOrder: DictionaryByteOrder = DictionaryByteOrder.LittleEndian): ByteArray {
        val data = BinaryOutput(byteOrder)
        val lengths = BinaryOutput(byteOrder)
        for (entry in lexicon) {
            lengths.integer(entry.numValues.toLong(), 2)
            for (candidate in entry.values) {
                val encoded = UTF8Util.encode(candidate)
                lengths.integer(encoded.size.toLong() + 1, 2)
                data.append(encoded); data.byte(0)
            }
        }
        val output = BinaryOutput(byteOrder)
        output.integer(lexicon.size.toLong(), 4)
        output.integer(data.size.toLong(), 4); output.append(data.toByteArray())
        output.append(lengths.toByteArray())
        return output.toByteArray()
    }
}
