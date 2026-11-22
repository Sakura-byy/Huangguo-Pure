package com.huangguo.pure.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 底部标签栏（upstream `LiquidBottomTabs` 的三层结构与参数逐字保留）。
 *
 * 三层：
 *  1. 容器 Row（64dp，Capsule）：vibrancy + blur(8dp) + lens(24dp, 24dp)，按下时整体微放大；
 *  2. 着色层 Row（56dp，alpha=0f，layerBackdrop(tabsBackdrop)）：整层 tint(accentColor)，
 *     只给指示器提供「被着色后的背景」，本身不可见；
 *  3. 指示器 Box（56dp → 按压时真实放大到 78dp）：Capsule 玻璃，采样
 *     combined(背景, tabsBackdrop, 栏体边框画布)，按下开 lens + 色散。
 *
 * 与 upstream 的差别：
 *  - 手势统一收在整条 Dock 上（`dockGesture`），按下任意 Tab 立即把指示器弹到该 Tab
 *    并进入按压态（`holdValue` 不释放 press），横向拖动跟手、松手吸附；
 *  - 指示器用**真实尺寸**放大（而不是 `layerBlock` 缩放大），才能越过 64dp 栏体上下边；
 *  - 指示器的 backdrop 里多挂一层 `rememberCanvasBackdrop`，按视图坐标 1:1 画出栏体那一圈
 *    亮边 + 内侧暗线。lens 采样它时会把这条边一起掰弯 —— 胶囊里于是能看见「被折射变形
 *    的栏体边框」，而不是被胶囊自身的玻璃盖掉。
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isLightTheme: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF),
    containerColor: Color = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f),
    content: @Composable RowScope.() -> Unit
) {
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        val barHeightPx = with(density) { BAR_HEIGHT.toPx() }
        val viewWidthPx = constraints.maxWidth.toFloat()
        // 宿主 ComposeView 比栏体高（栏体 64dp + 上下各 14dp 溢出余量），栏体在视图里居中
        val viewHeightPx = constraints.maxHeight.toFloat()
        val barTopPx = (viewHeightPx - barHeightPx) / 2f

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PRESSED_SCALE,
                onDragStarted = {},
                onDragStopped = {
                    animateToValue(
                        targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1).toFloat()
                    )
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                }
            )
        }

        // 光纹动态：按下位置决定高光起始角度，拖动方向与速度让它持续变形。
        // 角度走弹簧而不是直接赋值，手指急停时高光会回弹一下，不那么「贴纸感」。
        // 手指是否还按在栏上：拖动切页时 externalIndex 会变，
        // 若这时再走 animateToValue 就会 press → release，把拖动中的放大态按回去
        var pointerDown by remember { mutableIntStateOf(0) }
        var glowAngleTarget by remember { mutableFloatStateOf(45f) }
        var glowSpeedTarget by remember { mutableFloatStateOf(0f) }
        val glowAngle by animateFloatAsState(
            targetValue = glowAngleTarget,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f),
            label = "glowAngle"
        )
        val glowSpeed by animateFloatAsState(
            targetValue = glowSpeedTarget,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
            label = "glowSpeed"
        )

        // 滑动速度（归一化到 -1..1）：只驱动胶囊果冻拉伸。
        // 用 Animatable 由手势协程直接驱动，不在 composition 里读状态 ——
        // 否则每来一个 MOVE 都会重组一次 Dock，跟手感直接被拖没。
        val velocityFlex = remember { Animatable(0f) }
        val pushFlex: (Float) -> Unit = { target ->
            animationScope.launch {
                velocityFlex.animateTo(
                    target.fastCoerceIn(-1f, 1f),
                    spring(dampingRatio = 0.52f, stiffness = 260f)
                )
            }
        }

        // 外部选中（点击 / 返回键 / 页面内跳转）把指示器拉过去
        val externalIndex = selectedTabIndex()
        LaunchedEffect(externalIndex) {
            currentIndex = externalIndex
            if (pointerDown == 0 && abs(dampedDragAnimation.targetValue - externalIndex) > 0.15f) {
                dampedDragAnimation.animateToValue(externalIndex.toFloat())
            }
        }

        // 栏体内缩量（与容器玻璃的 padding(4.dp) 一致）：槽位、指示器、按压高光都用它做起始偏移
        val barInsetPx = with(density) { BAR_INSET.toPx() }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) barInsetPx + (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - barInsetPx - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 整条 Dock 的手势：按下吸附到所按 Tab + 按压态；横向拖动跟手；松手吸附并回调。
        // key 用 Unit 而不是 (tabsCount, tabWidth)：切页会让 tabWidth 重新计算，
        // 手势协程被取消 → 拖动中途 release → 放大态消失。参数走 rememberUpdatedState。
        val tabWidthState = rememberUpdatedState(tabWidth)
        val tabsCountState = rememberUpdatedState(tabsCount)
        val dockGesture = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val tw = tabWidthState.value
                val count = tabsCountState.value
                val down = awaitFirstDown(requireUnconsumed = false)
                val edge = 4f.dp.toPx()
                val slot = ((down.position.x - edge) / tw)
                    .toInt().fastCoerceIn(0, count - 1)
                pointerDown++
                dampedDragAnimation.press()
                dampedDragAnimation.holdValue(slot.toFloat())
                // 手指落点在胶囊的左/右半边 → 高光从那一侧斜着撩过来
                glowAngleTarget = 45f +
                        ((down.position.x - slot * tw) / tw - 0.5f) * 150f
                glowSpeedTarget = 0f
                pushFlex(0f)
                val startX = down.position.x
                var lastX = startX
                var lastT = down.uptimeMillis
                var dragged = false
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (c.changedToUpIgnoreConsumed()) break
                    if (abs(c.position.x - startX) > viewConfiguration.touchSlop) dragged = true
                    val dx = c.position.x - lastX
                    lastX = c.position.x
                    val dt = (c.uptimeMillis - lastT).coerceAtLeast(1L)
                    lastT = c.uptimeMillis
                    if (dx != 0f) {
                        // 光纹跟手：方向随拖动边转，速度决定高光扩散与折射强度
                        glowSpeedTarget = (dx / tw).fastCoerceIn(-1f, 1f) * 7f
                        glowAngleTarget = wrapDegrees(glowAngleTarget + dx / tw * 70f)
                        // 胶囊形变跟手：每秒移动几个 Tab，6 Tab/s 记满
                        pushFlex((dx / dt) / tw * 1000f / 9f)
                    } else {
                        pushFlex(0f)
                    }
                    if (dragged && dx != 0f) {
                        // 跟手：位置直接按「按下时的槽位 + 手指总位移」算，不用增量累加，
                        // 也不经过弹簧（弹簧只在抬手吸附时用）—— 胶囊中心与手指 1:1
                        dampedDragAnimation.trackValue(
                            (slot + (c.position.x - startX) / tw)
                                .fastCoerceIn(0f, (count - 1).toFloat())
                        )
                        c.consume()
                    }
                }
                glowSpeedTarget = 0f
                pushFlex(0f)
                pointerDown = (pointerDown - 1).coerceAtLeast(0)
                if (dragged) {
                    val target = dampedDragAnimation.value.fastRoundToInt()
                        .fastCoerceIn(0, count - 1)
                    dampedDragAnimation.animateToValue(target.toFloat())
                    currentIndex = target
                    onTabSelected(target)
                } else {
                    dampedDragAnimation.release()
                }
            }
        }

        // 指示器当前停的槽位：拖动越过半个 Tab 就换。图标强调色跟这个值走，
        // 而不是跟「已提交的页面」走 —— 拖离当前页面时原页面图标立即回到未选中态。
        val activeSlot by remember(tabsCount) {
            derivedStateOf {
                dampedDragAnimation.value.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
            }
        }

        // 胶囊尺寸：只有「按压放大」（56dp → 78dp）走 composition；
        // 位移与滑动拉伸全部放进 graphicsLayer 的 draw 阶段读状态 ——
        // 在 composition 里读 dampedDragAnimation.value / velocityFlex 会每帧重组 + 重新measure，
        // Dock 就跟不上手了。
        val pillScale = dampedDragAnimation.scaleX
        val pillHeightDp = BAR_INNER_HEIGHT * pillScale
        val basePillW = tabWidth * pillScale
        // 果冻拉伸按「真实宽度」做：改尺寸而不是缩放整层 ——
        // graphicsLayer 的 scaleX 会把胶囊里那层折射用的背景一起拉长，屏幕上就多出一个重影
        val flex = abs(velocityFlex.value).fastCoerceIn(0f, 1f)
        val stretchPx = basePillW * 0.30f * flex
        val pillWidthDp = with(density) { (basePillW + stretchPx).toDp() }
        val pillLeftPx = pillLeftFor(
            slot = dampedDragAnimation.value,
            tabWidth = tabWidth,
            baseW = basePillW,
            stretch = stretchPx,
            panelOffset = panelOffset,
            velocityFlex = velocityFlex.value,
            padPx = barInsetPx
        )
        val pillTopPx = (viewHeightPx - with(density) { pillHeightDp.toPx() }) / 2f
        // 指示器背景 = 真实背景 + 着色层 + 栏体边框。
        // 第三层是「坐标无关」的画布：在胶囊自己的 DrawScope 里，先反向平移回视图坐标，
        // 再按 1:1 画栏体的亮边与内侧暗线。玻璃采样到它之后 lens 直接把边掰弯，
        // 于是胶囊内部（以及胶囊边缘）都能看见被折射变形的栏体边框。
        val pillBackdrop = rememberCombinedBackdrop(
            backdrop,
            tabsBackdrop,
            rememberCanvasBackdrop {
                // 静止态不需要这层：栏体边框本来就画在视图里，胶囊没有折射也就没有「被掰弯」可言。
                // 省掉每帧一次全宽 drawImage + 两条圆角描边。
                if (dampedDragAnimation.pressProgress < 0.02f) return@rememberCanvasBackdrop
                val w = tabWidth * pillScale
                val stretch = w * 0.30f * abs(velocityFlex.value).fastCoerceIn(0f, 1f)
                // 与指示器本体用同一套夹取公式，保证折射出的栏体边框和胶囊严丝合缝
                val leftPx = pillLeftFor(
                    slot = dampedDragAnimation.value,
                    tabWidth = tabWidth,
                    baseW = w,
                    stretch = stretch,
                    panelOffset = panelOffset,
                    velocityFlex = velocityFlex.value,
                    padPx = barInsetPx
                )
                val topPx = (viewHeightPx - BAR_INNER_HEIGHT.toPx() * pillScale) / 2f
                translate(-leftPx, barTopPx - topPx) {
                    // 亮边：就是被 lens 掰弯的那条栏体边框
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (isLightTheme) 0.95f else 0.70f),
                        topLeft = Offset(2.5f, 2.5f),
                        size = Size(viewWidthPx - 5f, barHeightPx - 5f),
                        cornerRadius = CornerRadius((barHeightPx - 5f) / 2f),
                        style = Stroke(width = 5f)
                    )
                    // 内侧暗线：给边框一点厚度，折射后才有「玻璃把栏体压弯」的层次
                    drawRoundRect(
                        color = Color.Black.copy(alpha = if (isLightTheme) 0.10f else 0.30f),
                        topLeft = Offset(7f, 7f),
                        size = Size(viewWidthPx - 14f, barHeightPx - 14f),
                        cornerRadius = CornerRadius((barHeightPx - 14f) / 2f),
                        style = Stroke(width = 3f)
                    )
                }
            }
        )

        CompositionLocalProvider(LocalLiquidBottomTabActiveSlot provides activeSlot) {
        Box(
            Modifier
                .fillMaxSize()
                .then(dockGesture),
            contentAlignment = Alignment.CenterStart
        ) {
            // ① 容器玻璃
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            // 2.0dp：再降一档，背景画面与纹理更明显，仍留着玻璃的柔化
                            blur(2.0f.dp.toPx())
                            // 静止态 lens 半径乘 0，结果和「不做」一样，但要多跑一趟全屏 shader
                            val p = dampedDragAnimation.pressProgress
                            if (p > 0.01f) lens(24f.dp.toPx() * p, 24f.dp.toPx() * p)
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(BAR_HEIGHT)
                    .fillMaxWidth()
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )

            // ② 着色层：不可见，只为指示器提供 accent 染色后的背景
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0.004f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(2.0f.dp.toPx())
                                // lens 半径本来就要乘 pressProgress，静止时是 0，
                                // 与其跑一趟空 shader 不如直接不进管线
                                if (progress > 0.01f) {
                                    lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                                }
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .height(56f.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4f.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            // ③ 选中指示器：真实尺寸放大，上下越出栏体；玻璃里带着被掰弯的栏体边框
            Box(
                Modifier
                    // 父级是 64dp 栏体，必须 unbounded 才允许胶囊长到 78dp 并越出上下边框
                    .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) pillLeftPx
                            else viewWidthPx - pillLeftPx - (basePillW + stretchPx)
                    }
                    .drawBackdrop(
                        backdrop = pillBackdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            // 拖得越快折射越深：边缘光带被拉长、色散变宽，背景纹理跟着扭。
                            // 归一化到 0..1，否则手速一大 lens 半径翻四倍，背景直接被揉烂。
                            val flex = (abs(glowSpeed) / 3f).fastCoerceIn(0f, 1f)
                            if (progress > 0.01f) {
                                lens(
                                    16f.dp.toPx() * progress * (1f + 0.22f * flex),
                                    28f.dp.toPx() * progress * (1f + 0.16f * flex),
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            val flex = (abs(glowSpeed) / 3f).fastCoerceIn(0f, 1f)
                            Highlight(
                                width = (0.6f + 1.4f * progress).dp,
                                blurRadius = (0.3f + 0.9f * progress + 0.45f * flex).dp,
                                alpha = (progress * (0.80f + 0.20f * flex)).fastCoerceIn(0f, 1f),
                                style = HighlightStyle.Default(
                                    color = Color.White.copy(alpha = 0.5f),
                                    angle = glowAngle,
                                    falloff = 0.85f + 0.35f * flex
                                )
                            )
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress)
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8f.dp * progress,
                                alpha = progress
                            )
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.1f)
                                else Color.White.copy(0.1f),
                                alpha = 1f - progress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    )
                    .size(pillWidthDp, pillHeightDp)
            )
        }
        }
    }
}

/** 栏体高度：Dock 容器的 Capsule 高度，也是 `MainActivity` 里 ComposeView 的高度 */
private val BAR_HEIGHT = 64f.dp

/** 指示器静止高度：栏体内侧 4dp 内缩后剩 56dp */
private val BAR_INNER_HEIGHT = 56f.dp

/**
 * 栏体内缩量：容器玻璃的 `padding(4.dp)`，槽位从这里开始排。
 *
 * 指示器与槽位对齐时必须带上它 —— 漏掉这 4dp，首槽会贴住栏体左缘、
 * 末槽右侧却空出 8dp，两侧安全边距就不一样了。
 * 指示器本身不做边界夹取：按压放大 / 拖动拉伸时向两侧对称溢出栏体，
 * 溢出量两侧相同（首槽左溢 = 末槽右溢），视觉上仍是居中的。
 */
private val BAR_INSET = 4f.dp

/**
 * 指示器左缘（视图坐标）：槽位居中 + 果冻拉伸。
 *
 * @param velocityFlex 负值表示往左拖（指示器朝左伸长），正值朝右伸长
 * @param padPx 栏体内缩量，槽位从这里开始排
 */
private fun pillLeftFor(
    slot: Float,
    tabWidth: Float,
    baseW: Float,
    stretch: Float,
    panelOffset: Float,
    velocityFlex: Float,
    padPx: Float
): Float {
    // padPx 必须加进来：槽位是从栏体内缩 4dp 之后才开始排的
    val anchor = padPx + slot * tabWidth + (tabWidth - baseW) / 2f + panelOffset
    return if (velocityFlex < 0f) anchor - stretch else anchor
}

/** 按压时指示器的放大倍率：56dp → 78dp，上下各越出栏体 7dp */
private const val PRESSED_SCALE = 78f / 56f

/** 把角度收敛到 [0, 360)，避免长时间拖动后角度数值一直膨胀 */
private fun wrapDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
