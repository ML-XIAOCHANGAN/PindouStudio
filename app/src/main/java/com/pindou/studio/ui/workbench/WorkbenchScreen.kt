package com.pindou.studio.ui.workbench

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pindou.studio.core.DisplayResolver
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.editor.EditorViewModel
import com.pindou.studio.ui.theme.Ds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 拼制工作台：把图纸变成「边拼边打点」的辅助界面。
 *
 * - 点格子标记已拼（再点取消），已拼格子淡出，剩下的更醒目
 * - 单色隔离：只高亮当前要拼的颜色
 * - 行列辅助：高亮最近一次点到的行与列
 * - 屏幕常亮、双指缩放、单指拖动
 */
@Composable
fun WorkbenchScreen(vm: EditorViewModel, onBack: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var zoom by remember { mutableStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(0 to 0) }
    var focusCell by remember { mutableStateOf<IntArray?>(null) }
    var showClear by remember { mutableStateOf(false) }
    var colorPicker by remember { mutableStateOf(false) }

    val grid = vm.project.grid
    val palette = vm.palette
    // 订阅进度变化，让顶部计数 / 进度条跟着刷新
    val progressTick = vm.progressTick
    val doneCount = remember(progressTick) { vm.doneBeads() }
    val totalCount = remember(progressTick) { vm.totalBeads() }
    val fraction = remember(progressTick) { vm.project.progressFraction() }
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f, stiffness = 380f,
        ),
        label = "progress",
    )

    fun fit(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        val s = min((w - 24f) / grid.width, (h - 24f) / grid.height)
        zoom = s.coerceIn(2f, 96f)
        offset = Offset((w - grid.width * zoom) / 2f, (h - grid.height * zoom) / 2f)
    }

    LaunchedEffect(grid.width, grid.height) { fit(viewport.first, viewport.second) }

    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    val ground = Ds.c.canvasGround
    val board = Ds.c.surface
    val ink = Ds.c.ink
    val accent = Ds.c.clay
    val hair = Ds.c.hairline
    val sageArgb = (Ds.c.sage.toArgb() and 0x00FFFFFF) or (0x66 shl 24)
    // 单色隔离时的「压暗」底色：中性底板色。
    // 旧实现把其它颜色按 18% 透明度叠在底色上，低饱和色（米白、浅灰）几乎看不出来，
    // 现在直接铺中性底板色、只画目标色号，对比就够强了。
    val mutedArgb = Ds.c.surfaceAlt.toArgb()

    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        // ---------- 顶部 ----------
        Row(
            Modifier.fillMaxWidth().padding(start = Ds.s1, end = Ds.s2, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Back, size = 40, onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = Ds.s2)) {
                Text(vm.project.name, style = Ds.h3, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "拼制工作台 · $doneCount/$totalCount 颗",
                    style = Ds.label,
                    color = Ds.c.inkSoft,
                    maxLines = 1,
                )
            }
            IconSquare(PIcon.Fit, size = 38) { fit(viewport.first, viewport.second) }
            IconSquare(PIcon.Trash, size = 38) { showClear = true }
        }
        Hairline()

        // ---------- 画布 ----------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    viewport = it.width to it.height
                    if (zoom == 0f) fit(it.width, it.height)
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (zoom <= 0f) return@detectTapGestures
                        val c = floor((pos.x - offset.x) / zoom).toInt()
                        val r = floor((pos.y - offset.y) / zoom).toInt()
                        if (c in 0 until grid.width && r in 0 until grid.height) {
                            focusCell = intArrayOf(c, r)
                            vm.toggleProgress(c, r)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        if (zoom <= 0f) return@detectTransformGestures
                        val newZoom = (zoom * gestureZoom).coerceIn(2f, 96f)
                        val ratio = newZoom / zoom
                        offset = Offset(
                            centroid.x - (centroid.x - offset.x) * ratio + pan.x,
                            centroid.y - (centroid.y - offset.y) * ratio + pan.y,
                        )
                        zoom = newZoom
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val tick = vm.progressTick
                val isolated = vm.isolateColor
                val dm = vm.displayMode
                @Suppress("UNUSED_EXPRESSION") tick
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.clipRect(0f, 0f, size.width, size.height)
                    nc.drawColor(ground.toArgb())

                    paint.reset()
                    paint.isAntiAlias = false
                    paint.style = Paint.Style.FILL
                    paint.color = board.toArgb()
                    nc.drawRect(
                        offset.x, offset.y,
                        offset.x + grid.width * zoom, offset.y + grid.height * zoom, paint,
                    )

                    val c0 = max(0, floor(-offset.x / zoom).toInt())
                    val c1 = min(grid.width - 1, ceil((size.width - offset.x) / zoom).toInt())
                    val r0 = max(0, floor(-offset.y / zoom).toInt())
                    val r1 = min(grid.height - 1, ceil((size.height - offset.y) / zoom).toInt())

                    // 行列辅助线
                    focusCell?.let { focus ->
                        paint.color = 0x149E5330
                        paint.style = Paint.Style.FILL
                        val fx = offset.x + focus[0] * zoom
                        val fy = offset.y + focus[1] * zoom
                        nc.drawRect(fx, offset.y, fx + zoom, offset.y + grid.height * zoom, paint)
                        nc.drawRect(offset.x, fy, offset.x + grid.width * zoom, fy + zoom, paint)
                    }

                    // 单色隔离：整块画布先铺成中性底板色，只保留当前色号
                    if (isolated != 0) {
                        paint.style = Paint.Style.FILL
                        paint.color = mutedArgb
                        nc.drawRect(
                            offset.x, offset.y,
                            offset.x + grid.width * zoom, offset.y + grid.height * zoom, paint,
                        )
                    }

                    for (r in r0..r1) {
                        for (c in c0..c1) {
                            val idx = grid.get(c, r)
                            if (idx == 0) continue
                            if (isolated != 0 && idx != isolated) continue
                            val color = palette.colorAt(idx) ?: continue
                            var argb = DisplayResolver.resolve(color, dm)
                            val done = vm.project.progress.contains(r * grid.width + c)
                            if (done) {
                                argb = (argb and 0x00FFFFFF) or (0x2E shl 24)
                            }
                            paint.style = Paint.Style.FILL
                            paint.color = argb
                            paint.isAntiAlias = false
                            val left = offset.x + c * zoom
                            val top = offset.y + r * zoom
                            nc.drawRect(left, top, left + zoom + 0.4f, top + zoom + 0.4f, paint)
                        }
                    }

                    // 隔离时给目标色号描一圈细边，浅色豆子压在中性底板上也能一眼看出边界
                    if (isolated != 0 && zoom >= 6f) {
                        paint.style = Paint.Style.STROKE
                        paint.isAntiAlias = false
                        paint.strokeWidth = (zoom * 0.08f).coerceAtLeast(1f)
                        paint.color = hair.toArgb()
                        for (r in r0..r1) {
                            for (c in c0..c1) {
                                if (grid.get(c, r) != isolated) continue
                                val left = offset.x + c * zoom
                                val top = offset.y + r * zoom
                                nc.drawRect(left, top, left + zoom, top + zoom, paint)
                            }
                        }
                    }

                    // 网格线（放大后才画）
                    if (zoom >= 8f) {
                        paint.style = Paint.Style.STROKE
                        paint.color = 0x1A000000
                        paint.strokeWidth = 0.8f
                        for (c in c0..c1 + 1) {
                            val x = offset.x + c * zoom
                            nc.drawLine(x, offset.y, x, offset.y + grid.height * zoom, paint)
                        }
                        for (r in r0..r1 + 1) {
                            val y = offset.y + r * zoom
                            nc.drawLine(offset.x, y, offset.x + grid.width * zoom, y, paint)
                        }
                    }

                    // 坐标尺
                    if (zoom >= 16f) {
                        textPaint.textAlign = Paint.Align.CENTER
                        textPaint.textSize = min(zoom * 0.42f, 26f)
                        textPaint.color = 0x996B635B.toInt()
                        val step = when {
                            zoom >= 34f -> 5
                            zoom >= 22f -> 10
                            else -> 20
                        }
                        var c = (c0 / step) * step
                        while (c <= c1) {
                            if (c >= 0) {
                                nc.drawText(
                                    (c + 1).toString(),
                                    offset.x + c * zoom + zoom / 2f,
                                    offset.y - 4f,
                                    textPaint,
                                )
                            }
                            c += step
                        }
                        textPaint.textAlign = Paint.Align.RIGHT
                        var r = (r0 / step) * step
                        while (r <= r1) {
                            if (r >= 0) {
                                nc.drawText(
                                    (r + 1).toString(),
                                    offset.x - 4f,
                                    offset.y + r * zoom + zoom / 2f + zoom * 0.15f,
                                    textPaint,
                                )
                            }
                            r += step
                        }
                    }

                    // 已拼格子的勾选标记（放大后）
                    if (zoom >= 14f) {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = zoom * 0.09f
                        paint.color = sageArgb
                        for (r in r0..r1) {
                            for (c in c0..c1) {
                                if (!vm.project.progress.contains(r * grid.width + c)) continue
                                val l = offset.x + c * zoom
                                val t = offset.y + r * zoom
                                val pad = zoom * 0.3f
                                nc.drawLine(l + pad, t + zoom * 0.52f, l + zoom * 0.45f, t + zoom - pad, paint)
                                nc.drawLine(l + zoom * 0.45f, t + zoom - pad, l + zoom - pad, t + pad, paint)
                            }
                        }
                    }

                    nc.restore()
                }
            }
        }

        Hairline()

        // ---------- 底部：进度 + 当前色 ----------
        Column(Modifier.fillMaxWidth().background(Ds.c.ground).padding(horizontal = Ds.gutter, vertical = Ds.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = Ds.figure,
                    color = ink,
                )
                Spacer(Modifier.width(Ds.s3))
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(hair),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent),
                    )
                }
                Spacer(Modifier.width(Ds.s3))
                Text(
                    "$doneCount/$totalCount",
                    style = Ds.label,
                    color = Ds.c.inkSoft,
                )
            }
            Spacer(Modifier.height(Ds.s2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val current = palette.colorAt(vm.colorIndex)
                Row(
                    Modifier
                        .clip(CircleShape)
                        .clickable { colorPicker = true }
                        .padding(end = Ds.s3, top = Ds.s1, bottom = Ds.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(current?.argb ?: 0xFF000000.toInt())),
                    )
                    Spacer(Modifier.width(Ds.s2))
                    Text(
                        if (vm.isolateColor == 0) "单色隔离：关" else "只看 ${palette.colorAt(vm.isolateColor)?.code ?: ""}",
                        style = Ds.label,
                        color = if (vm.isolateColor == 0) Ds.c.inkSoft else Ds.c.amber,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "点格子打点 · 双指缩放",
                    style = Ds.label,
                    color = Ds.c.inkFaint,
                    maxLines = 1,
                )
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            containerColor = Ds.c.surface,
            title = { Text("清空拼制进度", style = Ds.h2, color = ink) },
            text = { Text("已打点的 $doneCount 颗会被清除，图纸本身不受影响。", style = Ds.small, color = Ds.c.inkSoft) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearProgress()
                    showClear = false
                }) { Text("清空", style = Ds.label, color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) {
                    Text("取消", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    if (colorPicker) {
        AlertDialog(
            onDismissRequest = { colorPicker = false },
            containerColor = Ds.c.surface,
            title = { Text("选择要拼的颜色", style = Ds.h2, color = ink) },
            text = {
                Column {
                    MetaLabel("点一下只看这一种颜色，方便从豆仓里挑豆")
                    Spacer(Modifier.height(Ds.s3))
                    val rows = vm.usageRows()
                    androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxWidth().height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(Ds.s2),
                    ) {
                        items(rows.size) { i ->
                            val row = rows[i]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.chooseColor(row.index)
                                        vm.toggleIsolate(row.index)
                                        colorPicker = false
                                    }
                                    .padding(vertical = Ds.s1),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(row.hex))),
                                )
                                Spacer(Modifier.width(Ds.s3))
                                Text(row.code, style = Ds.body, color = ink)
                                Spacer(Modifier.weight(1f))
                                MetaLabel("${row.count} 颗")
                            }
                        }
                    }
                    Spacer(Modifier.height(Ds.s2))
                    TextButton(onClick = {
                        vm.clearIsolate()
                        colorPicker = false
                    }) { Text("关闭隔离", style = Ds.label, color = Ds.c.inkSoft) }
                }
            },
            confirmButton = {
                TextButton(onClick = { colorPicker = false }) {
                    Text("完成", style = Ds.label, color = accent)
                }
            },
        )
    }
}
