package com.huangguo.pure.glass

import android.graphics.BlendMode
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 按压进度 + 触点位置 + 径向提亮 shader + 拖动手势。
 *
 * upstream 用 `com.kyant.backdrop.RuntimeShader` 包装 AGSL；backdrop 1.0.6 未导出该包装，
 * 这里直接用平台 `android.graphics.RuntimeShader`（API 33+）挂到原生 Paint 上以 Plus 混合绘制。
 * API < 33 时退化为整面 +25% 白色 Plus。
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader: android.graphics.RuntimeShader? =
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                android.graphics.RuntimeShader(
                    """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
                )
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= 29) {
            blendMode = BlendMode.PLUS
        } else {
            @Suppress("DEPRECATION")
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (shader != null) {
                    drawRect(
                        Color.White.copy(0.08f * progress),
                        blendMode = ComposeBlendMode.Plus
                    )
                    val pos = position(size, positionAnimation.value)
                    shader.setFloatUniform("size", size.width, size.height)
                    val alpha = (255f * 0.15f * progress).toInt().coerceIn(0, 255)
                    shader.setColorUniform("color", (alpha shl 24) or 0x00FFFFFF)
                    shader.setFloatUniform("radius", size.minDimension * 1.5f)
                    shader.setFloatUniform(
                        "position",
                        pos.x.fastCoerceIn(0f, size.width),
                        pos.y.fastCoerceIn(0f, size.height)
                    )
                    paint.shader = shader
                    drawContext.canvas.nativeCanvas.drawRect(
                        0f, 0f, size.width, size.height, paint
                    )
                } else {
                    drawRect(
                        Color.White.copy(0.25f * progress),
                        blendMode = ComposeBlendMode.Plus
                    )
                }
            }
            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
