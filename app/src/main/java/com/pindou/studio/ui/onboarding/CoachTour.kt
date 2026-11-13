package com.pindou.studio.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.hapticClick
import com.pindou.studio.ui.components.pressScale
import com.pindou.studio.ui.components.rememberTapHaptics
import com.pindou.studio.ui.theme.Ds
import kotlin.math.roundToInt

/** 一步一步的气泡引导。 */
data class TourStep(
    val anchor: String,
    val title: String,
    val body: String,
)

/**
 * 引导锚点：各处控件把自己的屏幕位置登记进来，引导层按 key 找目标。
 */
class TourAnchors {
    val map = mutableStateMapOf<String, Rect>()

    fun Modifier.tourAnchor(key: String): Modifier = this.onGloballyPositioned { coords ->
        val rect = coords.boundsInWindow()
        if (map[key] != rect) map[key] = rect
    }
}

val LocalTourAnchors = compositionLocalOf<TourAnchors?> { null }

/** 把控件登记成引导锚点；没有引导层时原样返回。 */
@Composable
fun Modifier.tourAnchorKey(key: String): Modifier {
    val anchors = LocalTourAnchors.current ?: return this
    return anchors.run { this@tourAnchorKey.tourAnchor(key) }
}

/** 首页的新手引导步骤：每一屏都锚在一个真实控件上。 */
val HomeTourSteps = listOf(
    TourStep(
        anchor = "start",
        title = "从这两种方式开始",
        body = "「新建作品」给你一张空白画布；「从图片生成」把照片自动转成能拼的图纸。",
    ),
    TourStep(
        anchor = "works",
        title = "作品都在这里",
        body = "点开继续编辑，长按看大图，向左滑可以复制、导出图纸或删除。",
    ),
    TourStep(
        anchor = "topbar",
        title = "右上角三件套",
        body = "导入工程文件、管理色卡、打开设置，都在这一排。",
    ),
    TourStep(
        anchor = "nav",
        title = "底部三个页面",
        body = "作品、创意工坊、豆仓，随时切换；豆仓里记着你手上有多少颗豆子。",
    ),
)

/**
 * 覆盖在真实界面上的新手引导：在目标位置挖一个圆角洞、其余压暗，
 * 洞会在步骤之间平滑移动与缩放，气泡跟着走。
 */
@Composable
fun CoachTour(
    steps: List<TourStep>,
    onFinish: () -> Unit,
) {
    if (steps.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val anchors = LocalTourAnchors.current

    var origin by remember { mutableStateOf(Offset.Zero) }
    var rawHole by remember { mutableStateOf<Rect?>(null) }

    // 每次开始引导都清空锚点缓存：否则会拿上一个页面残留的矩形去框
    LaunchedEffect(Unit) { anchors?.map?.clear() }
    var hasTarget by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // 目标位置换到本层坐标系；找不到锚点时沿用上一次的洞，避免闪一下
    val anchorRect = anchors?.map?.get(step.anchor)

    // 该步骤的锚点始终没出现（比如控件不在当前页面）就跳过，不留一块空白遮罩
    LaunchedEffect(index, anchorRect) {
        if (anchorRect == null) {
            delay(800)
            if (anchors?.map?.get(steps[index.coerceIn(0, steps.lastIndex)].anchor) == null) {
                if (index < steps.lastIndex) index += 1 else onFinish()
            }
        }
    }
    LaunchedEffect(anchorRect, origin) {
        val r = anchorRect ?: return@LaunchedEffect
        val pad = with(density) { 8.dp.toPx() }
        rawHole = Rect(
            r.left - origin.x - pad,
            r.top - origin.y - pad,
            r.right - origin.x + pad,
            r.bottom - origin.y + pad,
        )
        hasTarget = true
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.localToWindow(Offset.Zero) },
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        val gut = with(density) { Ds.gutter.toPx() }
        val tooltipEstimate = with(density) { 210.dp.toPx() }
        val ringColor = Ds.c.amber
        val ringWidth = with(density) { 2.dp.toPx() }
        val maxCorner = with(density) { 24.dp.toPx() }

        // 自适应动画：洞的四个边各自缓动到新目标（首帧直接就位）
        val spec: AnimationSpec<Float> =
            if (hasTarget) tween(480, easing = FastOutSlowInEasing) else snap()
        val hole = rawHole
        val targetLeft = hole?.left ?: 0f
        val targetTop = hole?.top ?: 0f
        val targetRight = hole?.right ?: 0f
        val targetBottom = hole?.bottom ?: 0f

        val left by animateFloatAsState(targetLeft, spec, label = "holeL")
        val top by animateFloatAsState(targetTop, spec, label = "holeT")
        val right by animateFloatAsState(targetRight, spec, label = "holeR")
        val bottom by animateFloatAsState(targetBottom, spec, label = "holeB")
        val appear by animateFloatAsState(if (hole != null) 1f else 0f, tween(320), label = "appear")

        // 呼吸高亮：强调色在透明度与线宽上缓慢起伏，指认「现在讲的就是这块」
        val breathe = rememberInfiniteTransition(label = "breathe")
        val ringPulse by breathe.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ringPulse",
        )

        val animHole = Rect(left, top, right, bottom)
        // 圆角跟着洞的高度自适应，小目标用小圆角，大目标最多 24dp
        val radius = (animHole.height * 0.28f).coerceIn(8.dp.value * density.density, maxCorner)

        // 遮罩：整屏减去一个圆角洞
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { /* 吃掉点击，避免误触底层 */ } },
        ) {
            val scrim = Color.Black.copy(alpha = 0.55f * appear)
            if (hole == null || animHole.width <= 1f || animHole.height <= 1f) {
                drawRect(scrim)
            } else {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(animHole, CornerRadius(radius, radius)))
                }
                drawPath(path, scrim)
                drawRoundRect(
                    color = ringColor.copy(alpha = (0.55f + 0.45f * ringPulse) * appear),
                    topLeft = Offset(animHole.left, animHole.top),
                    size = Size(animHole.width, animHole.height),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = ringWidth * (0.85f + 0.3f * ringPulse)),
                )
            }
        }

        // 气泡位置：目标偏上就放下面，反之放上面
        val below = animHole.center.y < heightPx * 0.55f
        val targetY = when {
            hole == null -> heightPx * 0.35f
            below -> (animHole.bottom + with(density) { 12.dp.toPx() }).coerceAtMost(heightPx - tooltipEstimate)
            else -> (animHole.top - with(density) { 12.dp.toPx() } - tooltipEstimate)
                .coerceAtLeast(with(density) { 24.dp.toPx() })
        }
        val tooltipY by animateFloatAsState(
            targetValue = targetY,
            animationSpec = if (hasTarget) tween(480, easing = FastOutSlowInEasing) else snap(),
            label = "tooltipY",
        )

        Column(
            Modifier
                .offset { IntOffset(gut.roundToInt(), tooltipY.roundToInt()) }
                .width(with(density) { (constraints.maxWidth - gut * 2).toDp() })
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Ds.c.surface)
                .padding(Ds.s4),
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 })
                        .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 8 })
                },
                label = "tourContent",
            ) { i ->
                val s = steps[i.coerceIn(0, steps.lastIndex)]
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = Ds.h3, color = Ds.c.ink)
                            Spacer(Modifier.height(Ds.s1))
                            Text(s.body, style = Ds.small, color = Ds.c.inkSoft)
                        }
                        Spacer(Modifier.width(Ds.s2))
                        SkipButton(onFinish)
                    }
                    Spacer(Modifier.height(Ds.s4))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}/${steps.size}",
                            style = Ds.label,
                            color = Ds.c.inkFaint,
                        )
                        Spacer(Modifier.weight(1f))
                        if (i > 0) {
                            SmallAction("上一步", Ds.c.inkSoft) {
                                index = (i - 1).coerceAtLeast(0)
                            }
                            Spacer(Modifier.width(Ds.s2))
                        }
                        SmallAction(
                            text = if (i == steps.lastIndex) "完成" else "下一步",
                            color = Ds.c.onAccent,
                            filled = true,
                        ) {
                            if (i == steps.lastIndex) onFinish() else index = i + 1
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    val haptics = rememberTapHaptics()
    Row(
        Modifier
            .clip(RoundedCornerShape(Ds.rControl))
            .background(Ds.c.surfaceAlt)
            .heightIn(min = 36.dp)
            .hapticClick(haptics, onClick = onClick)
            .padding(horizontal = Ds.s2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("跳过", style = Ds.label, color = Ds.c.inkSoft)
        Spacer(Modifier.width(4.dp))
        PIconView(PIcon.ChevronRight, Ds.c.inkFaint, Modifier.size(12.dp))
    }
}

@Composable
private fun SmallAction(
    text: String,
    color: Color,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptics = rememberTapHaptics()
    Box(
        Modifier
            .clip(RoundedCornerShape(Ds.rControl))
            .background(if (filled) Ds.c.accent else Ds.c.surfaceAlt)
            .pressScale(interaction, 0.95f)
            .heightIn(min = 36.dp)
            .hapticClick(haptics, onClick = onClick)
            .padding(horizontal = Ds.s3, vertical = Ds.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ds.label, color = color)
    }
}


