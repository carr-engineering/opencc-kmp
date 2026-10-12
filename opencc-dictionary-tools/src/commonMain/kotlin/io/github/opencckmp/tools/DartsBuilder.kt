/*
 * Darts-clone keyset builder adapted from Copyright (c) 2008-2014 Susumu Yata.
 * BSD-2-Clause; see licenses/darts-clone.txt. No native Darts code is invoked.
 */
package io.github.opencckmp.tools

import io.github.opencckmp.InvalidFormat

/** Matches upstream's default no-explicit-values keyset path, including free-block placement. */
internal class DartsBuilder(private val keys: List<ByteArray>) {
    private var units = IntArray(256)
    private var unitCount = 0
    private val previous = IntArray(4096)
    private val next = IntArray(4096)
    private val fixed = BooleanArray(4096)
    private val used = BooleanArray(4096)
    private var head = 0
    private data class Task(val begin: Int, val end: Int, val depth: Int, val node: Int)
    private fun extra(id: Int): Int = id and 4095
    private fun label(key: Int, depth: Int): Int = if (depth < keys[key].size) keys[key][depth].toInt() and 255 else 0
    private fun setLabel(node: Int, value: Int) { units[node] = (units[node] and 255.inv()) or (value and 255) }
    private fun setOffset(node: Int, value: Int) {
        if (value < 0 || value >= 1 shl 29) throw InvalidFormat("Darts offset exceeds format capacity")
        units[node] = units[node] and (Int.MIN_VALUE or 256 or 255)
        units[node] = units[node] or if (value < 1 shl 21) value shl 10 else (value shl 2) or 512
    }

    fun build(): IntArray {
        reserve(0)
        used[0] = true
        setOffset(0, 1)
        setLabel(0, 0)
        val tasks = ArrayDeque<Task>()
        if (keys.isNotEmpty()) tasks.addLast(Task(0, keys.size, 0, 0))
        while (tasks.isNotEmpty()) {
            val task = tasks.removeLast()
            val labels = mutableListOf<Int>()
            var value = -1
            for (index in task.begin until task.end) {
                val label = label(index, task.depth)
                if (label == 0) {
                    if (task.depth < keys[index].size) throw InvalidFormat("Darts keys cannot contain a NUL")
                    if (value == -1) value = index
                }
                if (labels.lastOrNull() != label) {
                    if (labels.isNotEmpty() && label < labels.last()) throw InvalidFormat("Darts keys must be in UTF-8 order")
                    labels.add(label)
                }
            }
            val offset = findOffset(task.node, labels)
            setOffset(task.node, task.node xor offset)
            for (label in labels) {
                val child = offset xor label
                reserve(child)
                if (label == 0) {
                    units[task.node] = units[task.node] or 256
                    units[child] = value or Int.MIN_VALUE
                } else setLabel(child, label)
            }
            used[extra(offset)] = true
            var begin = task.begin
            while (begin < task.end && label(begin, task.depth) == 0) begin++
            val children = mutableListOf<Task>()
            while (begin < task.end) {
                val first = begin
                val label = label(begin, task.depth)
                while (++begin < task.end && label(begin, task.depth) == label) { /* partition */ }
                children.add(Task(first, begin, task.depth + 1, offset xor label))
            }
            for (index in children.indices.reversed()) tasks.addLast(children[index])
        }
        for (block in maxOf(0, unitCount / 256 - 16) until unitCount / 256) fixBlock(block)
        return units.copyOf(unitCount)
    }

    private fun findOffset(node: Int, labels: List<Int>): Int {
        if (head < unitCount) {
            var unfixed = head
            do {
                val offset = unfixed xor labels.first()
                val relative = node xor offset
                var valid = !used[extra(offset)] && !(relative and 255 != 0 && relative and (255 shl 21) != 0)
                if (valid) for (index in 1 until labels.size) if (fixed[extra(offset xor labels[index])]) { valid = false; break }
                if (valid) return offset
                unfixed = next[extra(unfixed)]
            } while (unfixed != head)
        }
        return unitCount or (node and 255)
    }

    private fun reserve(id: Int) {
        if (id >= unitCount) expand()
        val slot = extra(id)
        if (id == head) {
            head = next[slot]
            if (head == id) head = unitCount
        }
        next[extra(previous[slot])] = next[slot]
        previous[extra(next[slot])] = previous[slot]
        fixed[slot] = true
    }

    private fun expand() {
        val oldSize = unitCount
        val oldBlocks = oldSize / 256
        val size = oldSize + 256
        if (oldBlocks + 1 > 16) fixBlock(oldBlocks - 16)
        if (size > units.size) units = units.copyOf(maxOf(size, units.size * 2))
        unitCount = size
        if (oldBlocks + 1 > 16) for (id in oldSize until size) {
            used[extra(id)] = false
            fixed[extra(id)] = false
        }
        for (id in oldSize + 1 until size) {
            next[extra(id - 1)] = id
            previous[extra(id)] = id - 1
        }
        previous[extra(oldSize)] = size - 1
        next[extra(size - 1)] = oldSize
        previous[extra(oldSize)] = previous[extra(head)]
        next[extra(size - 1)] = head
        next[extra(previous[extra(head)])] = oldSize
        previous[extra(head)] = size - 1
    }

    private fun fixBlock(block: Int) {
        val begin = block * 256
        val end = begin + 256
        var unusedOffset = 0
        for (offset in begin until end) if (!used[extra(offset)]) { unusedOffset = offset; break }
        for (id in begin until end) if (!fixed[extra(id)]) {
            reserve(id)
            setLabel(id, id xor unusedOffset)
        }
    }
}
