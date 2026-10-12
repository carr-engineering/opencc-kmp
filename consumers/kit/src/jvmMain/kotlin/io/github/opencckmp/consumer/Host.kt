package io.github.opencckmp.consumer

internal actual fun clockMillis(): Double = System.nanoTime() / 1_000_000.0
internal actual fun memorySnapshotBytes(): Double? = java.lang.management.ManagementFactory.getMemoryPoolMXBeans().sumOf { it.peakUsage?.used ?: 0 }.toDouble()
internal actual fun publishReport(report: String) { println(report) }
