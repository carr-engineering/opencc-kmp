@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package io.github.opencckmp.consumer

import kotlin.js.*

@JsFun("() => performance.now()")
internal actual external fun clockMillis(): Double
@JsFun("() => performance.memory ? performance.memory.usedJSHeapSize : -1")
private external fun memoryBytes(): Double
internal actual fun memorySnapshotBytes(): Double? = memoryBytes().takeIf { it >= 0 }
@JsFun("(report) => { globalThis.openccFixtureReport = report; if (typeof document !== 'undefined') document.body.textContent = report; }")
internal actual external fun publishReport(report: String)
