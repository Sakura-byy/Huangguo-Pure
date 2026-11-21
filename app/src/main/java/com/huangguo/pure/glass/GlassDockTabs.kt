package com.huangguo.pure.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangguo.pure.DockBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.math.roundToInt

/**
 * Dock 玻璃栏的 Compose 入口（纯 Java 壳 + Compose 玻璃 Tab）。
 */
@Composable
fun GlassDockTabs(
    source: DockBackdrop,
    labels: List<String>,
    iconRes: List<Int>,
    isLight: Boolean,
    iconTint: Color,
    labelColor: Color,
    accent: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Java 侧采样完成 → +1，draw 阶段读到状态变化即重绘 backdrop
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(source) {
        source.setOnChange { version++ }
        source.refresh()
        onDispose { source.setOnChange(null) }
    }

    val canvasBackdrop = rememberCanvasBackdrop {
        val v = version
        if (v >= 0) {
            val bmp = source.bitmap
            if (bmp != null && !bmp.isRecycled) {
                val img: ImageBitmap = bmp.asImageBitmap()
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                if (w > 0 && h > 0) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w, h),
                        filterQuality = FilterQuality.Low
                    )
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        LiquidBottomTabs(
            selectedTabIndex = { selected },
            onTabSelected = onSelect,
            backdrop = canvasBackdrop,
            tabsCount = labels.size,
            isLightTheme = isLight,
            accentColor = accent,
            containerColor = if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.40f)
            else Color(0xFF121212).copy(alpha = 0.40f),
            modifier = Modifier.fillMaxSize()
        ) {
            labels.forEachIndexed { index, label ->
                LiquidBottomTab({ onSelect(index) }) {
                    TabIcon(
                        painter = painterResource(iconRes[index]),
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                    BasicText(
                        text = label,
                        style = TextStyle(
                            color = labelColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(
    painter: Painter,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint)
    )
}
