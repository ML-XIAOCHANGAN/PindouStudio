package com.pindou.studio.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.Palette
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.util.findActivity
import com.pindou.studio.util.hideSystemBars
import com.pindou.studio.util.showSystemBars
import kotlin.math.min

/**
 * 全屏看图：模板预览和作品预览共用同一套。
 *
 * - 沉浸式：进入时隐藏系统栏，退出（含返回键）自动恢复
 * - 屏幕方向在进入全屏时**钉住**，不会跟着重力自己翻转
 * - 想换个角度看，点右上角「翻转」手动把图纸转 90°（0° → 90° → 180° → 270°）
 * - 双指缩放 1×–8×、单指拖动，越界自动夹住
 */
@Composable
fun FullScreenPatternViewer(
    grid: BeadGrid,
    palette: Palette,
    showCodes: Boolean,
    showGridLines: Boolean,
    onExit: () -> Unit,
    footerHint: String? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    BackHandler(enabled = true) { onExit() }

    // 方向钉在进入那一刻：手机稍微一歪不会把画面甩成横屏
    LaunchedEffect(Unit) {
        activity?.hideSystemBars()
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.showSystemBars()
        }
    }

    val board = Ds.c.surface
    val gridLine = Ds.c.hairline
    val backdrop = Color(0xFF15120F)

    /** 翻转后「可用画布尺寸」的宽高会互换，缩放和平移的夹取都要跟着换。 */
    fun availableSize(): Pair<Float, Float> {
        val w = viewport.width.toFloat()
        val h = viewport.height.toFloat()
        return if (rotation % 180 == 0) w to h else h to w
    }

    Box(Modifier.fillMaxSize().background(backdrop)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val next = (scale * zoomChange).coerceIn(1f, 8f)
                        val ratio = next / scale
                        scale = next
                        val (availW, availH) = availableSize()
                        if (availW > 0f && availH > 0f) {
                            val fit = min(availW / grid.width, availH / grid.height)
                            val boardW = grid.width * fit * next
                            val boardH = grid.height * fit * next
                            val limitX = maxOf(0f, (boardW - availW) / 2f) + 48f
                            val limitY = maxOf(0f, (boardH - availH) / 2f) + 48f
                            pan = Offset(
                                (pan.x * ratio + panChange.x).coerceIn(-limitX, limitX),
                                (pan.y * ratio + panChange.y).coerceIn(-limitY, limitY),
                            )
                        }
                    }
                },
        ) {
            drawPatternFullScreen(
                grid = grid,
                palette = palette,
                board = board,
                gridLine = gridLine,
                scale = scale,
                pan = pan,
                rotation = rotation,
                showCodes = showCodes,
                showGridLines = showGridLines,
            )
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = Ds.s4, vertical = Ds.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerPill(icon = PIcon.Close, text = "退出全屏", active = false, onClick = onExit)
            // 手动翻转：点一次转 90°，不需要锁定功能
            ViewerPill(
                icon = PIcon.RotateCW,
                text = "翻转 ${rotation}°",
                active = rotation != 0,
                onClick = {
                    rotation = (rotation + 90) % 360
                    scale = 1f
                    pan = Offset.Zero
                },
            )
        }

        Text(
            footerHint ?: "双指缩放 · 单指拖动 · 右上角「翻转」可把图纸转 90°",
            style = Ds.label,
            color = Color(0xCCFFFFFF),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Ds.s6, vertical = Ds.s6),
        )
    }
}

@Composable
private fun ViewerPill(icon: PIcon, text: String, active: Boolean, onClick: () -> Unit) {
    val haptics = rememberTapHaptics()
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) Ds.c.clay else Color(0xFF2A2521))
            .border(Ds.stroke, Color(0x33FFFFFF), CircleShape)
            .hapticClick(haptics, onClick = onClick)
            .padding(horizontal = Ds.s3, vertical = Ds.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIconView(icon, if (active) Color.White else Color(0xFFEDE6DC), Modifier.size(16.dp))
        Spacer(Modifier.width(Ds.s2))
        Text(text, style = Ds.label, color = if (active) Color.White else Color(0xFFEDE6DC))
    }
}

private fun DrawScope.drawPatternFullScreen(
    grid: BeadGrid,
    palette: Palette,
    board: Color,
    gridLine: Color,
    scale: Float,
    pan: Offset,
    rotation: Int,
    showCodes: Boolean,
    showGridLines: Boolean,
) {
    val rotated = rotation % 180 != 0
    val availW = if (rotated) size.height else size.width
    val availH = if (rotated) size.width else size.height
    val cell = min(availW / grid.width, availH / grid.height) * scale
    val boardW = grid.width * cell
    val boardH = grid.height * cell
    val cx = size.width / 2f
    val cy = size.height / 2f

    rotate(degrees = rotation.toFloat(), pivot = Offset(cx, cy)) {
        val ox = cx - boardW / 2f + pan.x
        val oy = cy - boardH / 2f + pan.y

        drawRect(board, topLeft = Offset(ox, oy), size = Size(boardW, boardH))
        for (r in 0 until grid.height) {
            for (c in 0 until grid.width) {
                val idx = grid.get(c, r)
                if (idx == 0) continue
                val color = palette.colorAt(idx) ?: continue
                drawRect(
                    color = Color(color.argb),
                    topLeft = Offset(ox + c * cell, oy + r * cell),
                    size = Size(cell + 0.4f, cell + 0.4f),
                )
            }
        }
        if (showGridLines && cell >= 7f) {
            for (c in 0..grid.width) {
                val x = ox + c * cell
                drawLine(gridLine, Offset(x, oy), Offset(x, oy + boardH), strokeWidth = 1f)
            }
            for (r in 0..grid.height) {
                val y = oy + r * cell
                drawLine(gridLine, Offset(ox, y), Offset(ox + boardW, y), strokeWidth = 1f)
            }
        }
        if (showCodes && cell >= 18f) {
            val canvas = drawContext.canvas.nativeCanvas
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = cell * 0.42f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val metrics = paint.fontMetrics
            val vo = -(metrics.ascent + metrics.descent) / 2f
            for (r in 0 until grid.height) {
                for (c in 0 until grid.width) {
                    val idx = grid.get(c, r)
                    if (idx == 0) continue
                    val color = palette.colorAt(idx) ?: continue
                    if (color.code.length > 5) continue
                    paint.color = if (ColorMath.luminance(color.argb) > 0.45f) {
                        0xDD111111.toInt()
                    } else {
                        0xEEFFFFFF.toInt()
                    }
                    canvas.drawText(
                        color.code,
                        ox + c * cell + cell / 2f,
                        oy + r * cell + cell / 2f + vo,
                        paint,
                    )
                }
            }
        }
    }
}
