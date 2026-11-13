package com.pindou.studio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pindou.studio.ui.theme.Ds

/**
 * 全应用统一的一处动效参数。
 *
 * 手感目标：按下有回弹、切换不飘、展开不拖沓。
 */
object Motion {
    /** 通用弹性：略有回弹，适合选中态、尺寸变化 */
    fun <T> springy() = spring<T>(dampingRatio = 0.72f, stiffness = 520f)

    /** 强回弹：按下 / 弹入 */
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f)

    /** 柔和：页面切换、面板展开 */
    fun <T> soft() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f)

    const val SCREEN_FADE_MS = 260
}

/** 按下时轻微缩小，松手弹回。挂在任何可点击元素上即可。 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.bouncy(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 毛玻璃面板。
 *
 * 半透明表面 + 顶部一道高光 + 1px 描边，配合 [blurBackdrop] 把后面的内容虚化，
 * 就是一套不依赖 API 31 也有质感的玻璃层（31 以上额外获得真实模糊）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Ds.rPanel),
    alpha: Float = 0.82f,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = Ds.c.surface
    val ink = Ds.c.ink
    Box(
        modifier
            .clip(shape)
            .background(surface.copy(alpha = alpha), shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isLight(surface)) 0.44f else 0.06f),
                        Color.Transparent,
                    ),
                ),
                shape,
            )
            .border(Ds.stroke, ink.copy(alpha = 0.10f), shape),
        content = content,
    )
}

private fun isLight(color: Color): Boolean = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.6f

/**
 * 毛玻璃背后的模糊强度：面板展开时把底层内容虚化（API 31+ 生效，以下自动忽略）。
 */
val glassBlurRadius = 18.dp
