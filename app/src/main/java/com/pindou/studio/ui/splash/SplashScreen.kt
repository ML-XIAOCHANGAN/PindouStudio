package com.pindou.studio.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开屏动画：黑色豆子淡入 → 逐字母上色 → 文字从字母右侧抹开 → 整体淡出。
 * 背景与窗口同色，点击任意处可跳过。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var skip by remember { mutableStateOf(false) }

    val appearAnim = remember { Animatable(0f) }
    val colorAnim = remember { Animatable(0f) }
    val wordAnim = remember { Animatable(0f) }
    val bloomAnim = remember { Animatable(0f) }
    val exitAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { appearAnim.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
        launch { bloomAnim.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
        launch {
            delay(820)
            if (!skip) colorAnim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            delay(1500)
            if (!skip) wordAnim.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        }
        delay(2900)
        if (skip) return@LaunchedEffect
        exitAnim.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        onFinished()
    }

    val appear = appearAnim.value
    val colorProgress = colorAnim.value
    val wordProgress = wordAnim.value
    val exit = exitAnim.value
    val bloom = bloomAnim.value

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF3EC))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                skip = true
                onFinished()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stops = listOf(
                floatArrayOf(0.18f, 0.14f, 0.46f) to (Color(0xFFBFE3C8) to 0.00f),
                floatArrayOf(0.84f, 0.10f, 0.40f) to (Color(0xFFFBD9B8) to 0.10f),
                floatArrayOf(0.10f, 0.86f, 0.44f) to (Color(0xFFC8E0F4) to 0.18f),
                floatArrayOf(0.86f, 0.90f, 0.38f) to (Color(0xFFD6EDD9) to 0.26f),
                floatArrayOf(0.52f, 0.50f, 0.34f) to (Color(0xFFF6E7D8) to 0.34f),
            )
            val base = size.minDimension
            stops.forEach { (pos, spec) ->
                val (color, delayFrac) = spec
                val p = ((bloom * 1.45f) - delayFrac * 1.45f).coerceIn(0f, 1f)
                if (p <= 0.001f) return@forEach
                val cx = size.width * pos[0]
                val cy = size.height * pos[1]
                val r = base * pos[2] * (0.55f + 0.45f * p) * (1f + exit * 0.15f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.62f * p * (1f - exit)),
                            color.copy(alpha = 0.28f * p * (1f - exit)),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
        }

        Box(
            Modifier
                .wrapContentSize()
                .graphicsLayer {
                    alpha = appear * (1f - exit)
                    val s = (0.94f + 0.06f * appear) * (1f + exit * 0.04f)
                    scaleX = s
                    scaleY = s
                    translationY = (1f - appear) * 14.dp.toPx()
                },
        ) {
            BrandLogo.Lockup(
                colorProgress = colorProgress,
                wordmarkProgress = wordProgress,
                lettersWidth = 196.dp,
            )
        }
    }
}
