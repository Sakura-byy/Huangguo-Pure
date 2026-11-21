package com.huangguo.pure.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange

/**
 * 拖动手势：按下立刻回调（按压态立即出现），抬起回调结束，移动时逐帧上报位移。
 *
 * 与 upstream `com.kyant.backdrop.catalog.utils.inspectDragGestures` 行为一致：
 * down → onDragStart，move → onDrag，up → onDragEnd，被抢占 → onDragCancel。
 * 即使手指不移动也算一次完整的按下/抬起，所以点击（LiquidBottomTab 的 clickable）
 * 与按压动效可以同时工作。
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        var canceled = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break
                if (event.changes.any { it.isConsumed && it.id != down.id }) {
                    canceled = true
                    break
                }
                val delta = change.positionChange()
                if (delta != Offset.Zero) {
                    onDrag(change, delta)
                    change.consume()
                }
            }
        } finally {
            if (canceled) onDragCancel() else onDragEnd()
        }
    }
}
