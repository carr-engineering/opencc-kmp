/*
 * MARISA LOUDS/keyset construction adapted from Copyright (c) 2010-2025 Susumu Yata.
 * BSD-2-Clause; see licenses/marisa.txt. No native MARISA calls or embedded reference output.
 */
package io.github.opencckmp.tools

import io.github.opencckmp.*

internal class MarisaBuilder {
    internal data class Key(val bytes: ByteArray, val reverse: Boolean, val weight: Float = 1f, var id: Int = 0, var terminal: Int = 0) {
        val size: Int get() = bytes.size
        fun label(index: Int): Int = bytes[if (reverse) bytes.lastIndex - index else index].toInt() and 255
        fun slice(start: Int, end: Int): ByteArray = if (reverse) bytes.copyOfRange(size - end, size - start) else bytes.copyOfRange(start, end)
    }
    private data class Range(val begin: Int, val end: Int, val position: Int, val node: Int, val weight: Float = 0f)
    private data class Cache(var parent: Int = 0, var child: Int = 0, var weight: Float = Float.fromBits(0x00800000), var link: Int = 0x00800000)
    private class Trie(val level: Int) {
        val louds = MarisaBits()
        var terminalFlags = MarisaBits()
        val links = MarisaBits()
        val bases = mutableListOf<Int>()
        var extras = IntArray(0)
        var tail = ByteArray(0)
        var tailEnds = MarisaBits()
        var next: Trie? = null
        var cache = emptyArray<Cache>()
        var firstLevelNodes = 0
        var count = 1
        var tailMode = 0x1000
        var terminals = IntArray(0)

        fun write(output: BinaryOutput) {
            louds.write(output, select0 = level == 1, select1 = true)
            terminalFlags.write(output, built = level == 1, select1 = level == 1)
            links.write(output)
            output.vector(bases.map { it.toByte() }.toByteArray())
            writeMarisaFlat(output, extras)
            output.vector(tail)
            tailEnds.write(output, built = false)
            next?.write(output)
            val encodedCache = output.sibling()
            for (item in cache) {
                encodedCache.word32(item.parent); encodedCache.word32(item.child); encodedCache.word32(item.link)
            }
            output.vector(encodedCache.toByteArray())
            output.integer(firstLevelNodes.toLong(), 4)
            output.integer((count or tailMode or 0x20000).toLong(), 4)
        }
    }

    fun build(lexicon: Lexicon, byteOrder: DictionaryByteOrder): Pair<ByteArray, Lexicon> {
        if (!lexicon.isUnique()) throw InvalidFormat("Dictionary contains duplicate keys")
        val entries = lexicon.entries
        val keys = entries.map { entry ->
            val bytes = UTF8Util.encode(entry.key)
            if (bytes.any { it == 0.toByte() }) throw InvalidFormat("MARISA OpenCC keys cannot contain a NUL")
            Key(bytes, false)
        }
        val trie = buildTrie(keys, 1)
        val terminalNodes = trie.terminals.toSet()
        repeat(trie.bases.size) { trie.terminalFlags.add(it in terminalNodes) }
        trie.terminalFlags.add(false)
        val ids = IntArray(trie.bases.size)
        var rank = 0
        for (node in 0 until trie.bases.size) { ids[node] = rank; if (trie.terminalFlags[node]) rank++ }
        val sorted = entries.indices.sortedBy { ids[trie.terminals[it]] }.map { entries[it] }
        val output = BinaryOutput(byteOrder)
        output.append("We love Marisa.\u0000".encodeToByteArray())
        trie.write(output)
        return output.toByteArray() to Lexicon(sorted)
    }

    private fun compare(left: Key, right: Key): Int {
        for (index in 0 until minOf(left.size, right.size)) {
            val difference = left.label(index) - right.label(index)
            if (difference != 0) return difference
        }
        return left.size.compareTo(right.size)
    }

    private fun buildTrie(input: List<Key>, level: Int): Trie {
        val trie = Trie(level)
        input.forEachIndexed { index, key -> key.id = index }
        val keys = input.sortedWith { left, right -> compare(left, right) }
        var unique = 0
        for (index in keys.indices) if (index == 0 || compare(keys[index - 1], keys[index]) != 0) unique++
        var cacheSize = if (level == 1) 256 else 1
        while (cacheSize < unique / 512) cacheSize *= 2
        trie.cache = Array(cacheSize) { Cache() }
        trie.louds.add(true); trie.louds.add(false)
        trie.bases.add(0); trie.links.add(false)
        val queue = ArrayDeque<Range>()
        queue.addLast(Range(0, keys.size, 0, 0))
        val nextKeys = mutableListOf<Key>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            var begin = current.begin
            while (begin < current.end && keys[begin].size == current.position) {
                keys[begin].terminal = current.node
                begin++
            }
            if (begin == current.end) { trie.louds.add(false); continue }
            val ranges = mutableListOf<Range>()
            var first = begin
            var weight = keys[begin].weight.toDouble()
            for (index in begin + 1 until current.end) {
                if (keys[index - 1].label(current.position) != keys[index].label(current.position)) {
                    ranges.add(Range(first, index, current.position, 0, weight.toFloat()))
                    first = index
                    weight = 0.0
                }
                weight += keys[index].weight.toDouble()
            }
            ranges.add(Range(first, current.end, current.position, 0, weight.toFloat()))
            // Stable ties retain ascending label order, as std::stable_sort does upstream.
            val ordered = ranges.sortedByDescending { it.weight }
            if (current.node == 0) trie.firstLevelNodes = ordered.size
            for (range in ordered) {
                var end = range.position + 1
                while (end < keys[range.begin].size) {
                    var different = false
                    for (index in range.begin + 1 until range.end) {
                        if (end >= keys[index].size || keys[index - 1].label(end) != keys[index].label(end)) { different = true; break }
                    }
                    if (different) break
                    end++
                }
                val child = trie.bases.size
                val label = keys[range.begin].label(range.position)
                val cacheId = if (level == 1) (current.node xor (current.node shl 5) xor label) and (cacheSize - 1) else child and (cacheSize - 1)
                val cached = trie.cache[cacheId]
                if (range.weight > cached.weight) { cached.parent = current.node; cached.child = child; cached.weight = range.weight }
                if (end == range.position + 1) {
                    trie.bases.add(label); trie.links.add(false)
                } else {
                    trie.bases.add(0); trie.links.add(true)
                    nextKeys.add(Key(keys[range.begin].slice(range.position, end), true, range.weight))
                }
                queue.addLast(Range(range.begin, range.end, end, child))
                trie.louds.add(true)
            }
            trie.louds.add(false)
        }
        trie.louds.add(false)
        trie.terminals = IntArray(input.size)
        for (key in keys) trie.terminals[key.id] = key.terminal
        var nextTerminals = IntArray(0)
        if (nextKeys.isNotEmpty()) {
            if (level == 3) nextTerminals = buildTail(nextKeys, trie)
            else {
                trie.next = buildTrie(nextKeys, level + 1)
                nextTerminals = trie.next!!.terminals
            }
        }
        if (trie.next != null) {
            trie.count = trie.next!!.count + 1
            trie.tailMode = trie.next!!.tailMode
        }
        var link = 0
        val extra = IntArray(nextTerminals.size)
        val linksByNode = IntArray(trie.bases.size) { -1 }
        for (node in trie.bases.indices) if (trie.links[node]) {
            trie.bases[node] = nextTerminals[link] and 255
            extra[link] = nextTerminals[link] ushr 8
            linksByNode[node] = link++
        }
        trie.extras = extra
        for (item in trie.cache) {
            if (item.child != 0) {
                val base = trie.bases[item.child]
                val extension = if (trie.links[item.child]) extra[linksByNode[item.child]] else 0xFFFFFF
                item.link = base or (extension shl 8)
            } else { item.parent = -1; item.child = -1 }
        }
        return trie
    }

    private fun buildTail(keys: List<Key>, trie: Trie): IntArray {
        // Entry ordering is ascending lexicographic comparison of reversed physical bytes.
        val ordered = keys.indices.sortedWith { left, right ->
            compare(Key(keys[left].bytes, true), Key(keys[right].bytes, true))
        }
        val offsets = IntArray(keys.size)
        val buffer = BinaryOutput()
        var previous = -1
        val binary = keys.any { key -> key.bytes.any { it == 0.toByte() } }
        for (index in ordered.asReversed()) {
            val current = keys[index].bytes
            val last = if (previous < 0) ByteArray(0) else keys[previous].bytes
            var matched = 0
            while (matched < current.size && matched < last.size && current[current.lastIndex - matched] == last[last.lastIndex - matched]) matched++
            if (matched == current.size && last.isNotEmpty()) offsets[index] = offsets[previous] + last.size - matched
            else {
                offsets[index] = buffer.size
                buffer.append(current)
                if (binary) {
                    repeat(current.size - 1) { trie.tailEnds.add(false) }
                    trie.tailEnds.add(true)
                } else buffer.byte(0)
            }
            previous = index
        }
        trie.tail = buffer.toByteArray()
        trie.tailMode = if (binary) 0x2000 else 0x1000
        return offsets
    }
}
