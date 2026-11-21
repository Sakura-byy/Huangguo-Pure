package com.huangguo.pure.glass

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView

/** Java 侧回调：Tab 被点击或被拖动选中 */
fun interface TabSelectListener {
    fun onSelect(index: Int)
}

/**
 * 把一个 ComposeView 变成液态玻璃 Dock。
 *
 * Java 侧只需要：new ComposeView → addView → 调这个方法 → 之后用 DockState 控制选中与配色。
 */
fun ComposeView.installGlassDock(
    state: DockState,
    source: com.huangguo.pure.DockBackdrop,
    labels: List<String>,
    icons: List<Int>,
    listener: TabSelectListener
) {
    setContent {
        GlassDockTabs(
            source = source,
            labels = labels,
            iconRes = icons,
            isLight = state.light,
            iconTint = Color(state.iconTint),
            labelColor = Color(state.labelColor),
            accent = Color(state.accent),
            selected = state.selected,
            onSelect = { listener.onSelect(it) },
            modifier = Modifier.fillMaxSize()
        )
    }
}
