package com.pindou.studio.ui.workshop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pindou.studio.PindouApp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.GridHistory
import com.pindou.studio.core.Palette
import com.pindou.studio.core.PatternTemplate
import com.pindou.studio.ui.components.FullScreenPatternViewer
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.PrimaryButton
import com.pindou.studio.ui.components.SecondaryButton
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.components.SwitchRow
import com.pindou.studio.ui.components.hapticClick
import com.pindou.studio.ui.components.rememberTapHaptics
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.util.findActivity
import com.pindou.studio.util.hideSystemBars
import com.pindou.studio.util.showSystemBars
import kotlin.math.abs
import kotlin.math.min

/** 模板编辑里的四个工具。 */
private enum class TemplateTool(val label: String, val icon: PIcon) {
    PEN("画笔", PIcon.Pen),
    ERASER("橡皮", PIcon.Eraser),
    FILL("填充", PIcon.Bucket),
    DROPPER("吸管", PIcon.Dropper),
}

/**
 * 模板详情：**默认预览模式，只能看不能改**。
 *
 * 三个层次的入口，避免"进来就能改"的误会：
 * 1. 预览（默认）：画布完全不响应触摸，没有任何编辑控件；底部只有「进入编辑」和「全屏查看」
 * 2. 编辑：底部出现「返回预览」和「保存为我的作品」，画布才可落笔
 * 3. 全屏：沉浸式看图纸，可缩放平移，顶部有「退出全屏」和「锁定」（锁住后不跟手，适合照着拼）
 */
@Composable
fun TemplateDetailScreen(
    template: PatternTemplate,
    onBack: () -> Unit,
    onSaveAsProject: (BeadGrid) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { PindouApp.from(context) }
    val palette = remember(template.id) {
        app.paletteRepository.byId(app.settings.lastPaletteId) ?: app.paletteRepository.default()
    }
    val baseGrid = remember(template.id, palette.id) { template.toGrid(palette) }

    // 必须拿一份**拷贝**来编辑：否则画笔改的是同一个 baseGrid，
    // 「有没有改动」永远判不出来，退出时也就不会提示保存
    var grid by remember(template.id, palette.id) { mutableStateOf(baseGrid.copy()) }
    val history = remember(template.id, palette.id) { GridHistory(limit = 40) }
    var tick by remember { mutableIntStateOf(0) }

    // 默认预览：只能看
    var editing by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    // 有未保存修改时，返回/退出要先问一声，绝不默默保存
    var pendingExit by remember { mutableStateOf(false) }

    var tool by remember { mutableStateOf(TemplateTool.PEN) }
    var showCodes by remember { mutableStateOf(false) }
    var showGridLines by remember { mutableStateOf(true) }
    var colorIndex by remember { mutableIntStateOf(0) }

    val usage = remember(tick) { grid.colorUsage() }

    /** 画布是否已经被改过（和模板原图不一致）。 */
    val dirty = remember(tick) { !grid.cells.contentEquals(baseGrid.cells) }

    /** 丢弃改动，回到模板原样。 */
    fun discardEdits() {
        grid = baseGrid.copy()
        history.clear()
        tick++
    }

    /**
     * 退出模板页。
     *
     * 编辑中的改动**不会自动保存**：只有点「保存为我的作品」才算数。
     * 有未保存改动时先弹窗让用户选，避免辛苦改完又悄悄丢掉。
     */
    fun requestExit() {
        if (editing && dirty) pendingExit = true else onBack()
    }

    BackHandler(enabled = true) { requestExit() }
    LaunchedEffect(usage) {
        if (colorIndex == 0) colorIndex = usage.firstOrNull()?.first ?: 1
    }

    // ---------- 编辑动作 ----------
    var recorder: GridHistory.StrokeRecorder? = null

    fun beginStroke() {
        recorder = GridHistory.StrokeRecorder(grid)
    }

    fun paintCell(c: Int, r: Int) {
        if (!grid.inBounds(c, r)) return
        val value = if (tool == TemplateTool.ERASER) 0 else colorIndex
        recorder?.record(c, r, value)
    }

    fun paintLine(c0: Int, r0: Int, c1: Int, r1: Int) {
        var x0 = c0
        var y0 = r0
        val dx = abs(c1 - x0)
        val dy = abs(r1 - y0)
        val sx = if (x0 < c1) 1 else -1
        val sy = if (y0 < r1) 1 else -1
        var err = dx - dy
        var guard = 0
        while (guard++ < 512) {
            paintCell(x0, y0)
            if (x0 == c1 && y0 == r1) break
            val e2 = err * 2
            if (e2 > -dy) { err -= dy; x0 += sx }
            if (e2 < dx) { err += dx; y0 += sy }
        }
    }

    fun endStroke() {
        val patch = recorder?.build()
        recorder = null
        if (patch != null) {
            history.push(patch)
            tick++
        }
    }

    fun editOnce(block: () -> Boolean) {
        val snap = GridHistory.snapshotOf(grid)
        if (block()) {
            history.push(snap)
            tick++
        }
    }

    fun onCellDown(c: Int, r: Int) {
        if (!editing) return
        when (tool) {
            TemplateTool.PEN, TemplateTool.ERASER -> {
                beginStroke()
                paintCell(c, r)
                tick++
            }
            TemplateTool.FILL -> editOnce { grid.floodFill(c, r, colorIndex) > 0 }
            TemplateTool.DROPPER -> {
                val idx = grid.get(c, r)
                if (idx > 0) {
                    colorIndex = idx
                    tool = TemplateTool.PEN
                }
            }
        }
    }

    // ---------- 全屏 ----------
    if (fullscreen) {
        FullScreenPatternViewer(
            grid = grid,
            palette = palette,
            showCodes = showCodes,
            showGridLines = showGridLines,
            onExit = { fullscreen = false },
        )
        return
    }

    if (pendingExit) {
        AlertDialog(
            onDismissRequest = { pendingExit = false },
            containerColor = Ds.c.surface,
            title = { Text("还有没保存的修改", style = Ds.h2, color = Ds.c.ink) },
            text = {
                Text(
                    "模板的改动不会自动保存，现在退出这些改动就没了。要保存成作品吗？",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExit = false
                    onSaveAsProject(grid.copy())
                }) { Text("保存为作品", style = Ds.label, color = Ds.c.clay) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingExit = false
                        discardEdits()
                        onBack()
                    }) { Text("放弃修改", style = Ds.label, color = Ds.c.rose) }
                    TextButton(onClick = { pendingExit = false }) {
                        Text("继续编辑", style = Ds.label, color = Ds.c.inkSoft)
                    }
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Ds.s1, end = Ds.s2, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Back, size = 40, onClick = { requestExit() })
            Column(Modifier.weight(1f).padding(horizontal = Ds.s2)) {
                Text(
                    template.name,
                    style = Ds.h3,
                    color = Ds.c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLabel("${template.category} · ${grid.width}×${grid.height} 格")
            }
            // 模式标签：一眼能看出现在是不是只读
            Text(
                if (editing) "编辑中" else "预览 · 只读",
                style = Ds.label,
                color = if (editing) Ds.c.clay else Ds.c.inkSoft,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (editing) Ds.c.claySoft else Ds.c.surfaceAlt)
                    .padding(horizontal = Ds.s3, vertical = Ds.s1),
            )
        }
        Hairline()

        // ---------- 画布（固定，不参与滚动） ----------
        Column(Modifier.fillMaxWidth().padding(horizontal = Ds.gutter, vertical = Ds.s3)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Ds.rCard))
                    .background(Ds.c.surface)
                    .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rCard))
                    .padding(Ds.s3),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(Ds.rPanel))
                        .background(Ds.c.canvasGround)
                        .padding(Ds.s2),
                ) {
                    TemplateCanvas(
                        grid = grid,
                        palette = palette,
                        tick = tick,
                        showCodes = showCodes,
                        showGridLines = showGridLines,
                        editable = editing,
                        onDown = { c, r -> onCellDown(c, r) },
                        onMove = { c, r, pc, pr ->
                            if (tool == TemplateTool.PEN || tool == TemplateTool.ERASER) {
                                paintLine(pc, pr, c, r)
                                tick++
                            }
                        },
                        onUp = { endStroke() },
                    )
                }
            }

            // ---------- 编辑工具栏（只在编辑模式出现） ----------
            if (editing) {
                Spacer(Modifier.height(Ds.s3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TemplateTool.entries.forEach { t ->
                        IconSquare(
                            icon = t.icon,
                            label = t.label,
                            selected = tool == t,
                            size = 42,
                        ) { tool = t }
                    }
                    Spacer(Modifier.weight(1f))
                    IconSquare(PIcon.Undo, size = 38, enabled = history.canUndo) {
                        history.undo(grid)?.let { grid = it }
                        tick++
                    }
                    IconSquare(PIcon.Redo, size = 38, enabled = history.canRedo) {
                        history.redo(grid)?.let { grid = it }
                        tick++
                    }
                }
                Spacer(Modifier.height(Ds.s2))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Ds.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("颜色", style = Ds.label, color = Ds.c.inkSoft)
                    Row(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Ds.s2),
                    ) {
                        usage.forEach { (idx, _) ->
                            val color = palette.colorAt(idx) ?: return@forEach
                            TemplateSwatch(
                                argb = color.argb,
                                code = color.code,
                                selected = colorIndex == idx,
                            ) { colorIndex = idx; tool = TemplateTool.PEN }
                        }
                    }
                }
                Spacer(Modifier.height(Ds.s2))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Ds.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "当前色号 ${palette.colorAt(colorIndex)?.code ?: "-"}",
                        style = Ds.label,
                        color = Ds.c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    com.pindou.studio.ui.components.TextLink("还原模板", color = Ds.c.rose) {
                        grid = template.toGrid(palette)
                        history.clear()
                        tick++
                    }
                }
            }
        }

        // ---------- 信息区（可滚动） ----------
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ds.gutter),
        ) {
            if (!editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatBlock("${grid.paintedCount()}", "颗豆子")
                    Spacer(Modifier.width(Ds.s6))
                    StatBlock("${usage.size}", "种颜色")
                    Spacer(Modifier.width(Ds.s6))
                    StatBlock("${grid.width}×${grid.height}", "格数")
                }
            }
            SectionHeader("配色", "${usage.size} 色")
            usage.forEach { (idx, count) ->
                val color = palette.colorAt(idx) ?: return@forEach
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Ds.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(color.argb))
                            .border(Ds.stroke, Ds.c.hairline, CircleShape),
                    )
                    Spacer(Modifier.width(Ds.s3))
                    Text(color.code, style = Ds.h3, color = Ds.c.ink)
                    Spacer(Modifier.width(Ds.s2))
                    MetaLabel(color.hex.uppercase())
                    Spacer(Modifier.weight(1f))
                    Text("$count 颗", style = Ds.label, color = Ds.c.inkSoft)
                }
            }

            SectionHeader("显示")
            SwitchRow("显示网格线", checked = showGridLines) { showGridLines = it }
            SwitchRow("显示色号", "全屏放大后更清楚", checked = showCodes) { showCodes = it }

            Spacer(Modifier.height(Ds.s3))
            Text(template.note, style = Ds.small, color = Ds.c.inkSoft)
            Spacer(Modifier.height(Ds.s6))
        }

        // ---------- 底部动作 ----------
        Column(Modifier.fillMaxWidth().background(Ds.c.ground).padding(horizontal = Ds.gutter, vertical = Ds.s3)) {
            Hairline()
            Spacer(Modifier.height(Ds.s3))
            if (!editing) {
                // 预览是只读的，改图必须显式点这里
                PrimaryButton("进入编辑", Modifier.fillMaxWidth(), icon = PIcon.Pen) {
                    editing = true
                }
                Spacer(Modifier.height(Ds.s2))
                SecondaryButton("全屏查看", Modifier.fillMaxWidth()) {
                    fullscreen = true
                }
                Spacer(Modifier.height(Ds.s2))
                Text(
                    "预览只能看不能改；想动手改点「进入编辑」，全屏适合照着拼。",
                    style = Ds.label,
                    color = Ds.c.inkFaint,
                )
            } else {
                // 唯一的"确认保存"入口
                PrimaryButton(
                    "保存为我的作品",
                    Modifier.fillMaxWidth(),
                    icon = PIcon.Save,
                ) {
                    onSaveAsProject(grid.copy())
                }
                Spacer(Modifier.height(Ds.s2))
                if (dirty) {
                    SecondaryButton("放弃修改，返回预览", Modifier.fillMaxWidth()) {
                        discardEdits()
                        editing = false
                    }
                } else {
                    SecondaryButton("返回预览", Modifier.fillMaxWidth()) {
                        editing = false
                    }
                }
                Spacer(Modifier.height(Ds.s2))
                Text(
                    if (dirty) {
                        "改动还没保存：点上面「保存为我的作品」才算保存；直接返回或退出会丢掉这些改动。"
                    } else {
                        "编辑不会自动保存，改完点「保存为我的作品」才会生成作品。"
                    },
                    style = Ds.label,
                    color = if (dirty) Ds.c.clay else Ds.c.inkFaint,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 画布

/** 一行小统计。 */
@Composable
private fun StatBlock(value: String, label: String) {
    Column {
        Text(value, style = Ds.figure, color = Ds.c.ink)
        MetaLabel(label)
    }
}

/** 颜色圆点。 */
@Composable
private fun TemplateSwatch(argb: Int, code: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberTapHaptics()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(if (selected) 34.dp else 30.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(
                    if (selected) 2.dp else Ds.stroke,
                    if (selected) Ds.c.ink else Ds.c.hairline,
                    CircleShape,
                )
                .hapticClick(haptics, onClick = onClick),
        )
        Spacer(Modifier.height(2.dp))
        Text(code, style = Ds.label, color = if (selected) Ds.c.ink else Ds.c.inkFaint)
    }
}

/**
 * 模板画布：预览时**完全不接手势**（只读），编辑时才落笔。
 *
 * 模板尺寸很小（十几格），按「整块铺满、不缩放」绘制即可，
 * 单指落笔改一格，拖动按直线插值，不会断笔。
 */
@Composable
private fun TemplateCanvas(
    grid: BeadGrid,
    palette: Palette,
    tick: Int,
    showCodes: Boolean,
    showGridLines: Boolean,
    editable: Boolean,
    onDown: (Int, Int) -> Unit,
    onMove: (Int, Int, Int, Int) -> Unit,
    onUp: () -> Unit,
) {
    val board = Ds.c.surface
    val gridLine = Ds.c.hairline
    val ink = Ds.c.ink

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(grid.width.toFloat() / grid.height.toFloat().coerceAtLeast(1f))
            .then(
                if (!editable) Modifier else Modifier.pointerInput(grid.width, grid.height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cellW = size.width.toFloat() / grid.width
                        val cellH = size.height.toFloat() / grid.height
                        fun cellAt(pos: Offset): Pair<Int, Int> =
                            Pair((pos.x / cellW).toInt(), (pos.y / cellH).toInt())

                        var prev = cellAt(down.position)
                        onDown(prev.first, prev.second)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val cell = cellAt(change.position)
                            if (cell != prev) {
                                onMove(cell.first, cell.second, prev.first, prev.second)
                                prev = cell
                            }
                            change.consume()
                        }
                        onUp()
                    }
                },
            ),
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        val cellW = size.width / grid.width
        val cellH = size.height / grid.height
        drawRect(board, size = size)
        for (r in 0 until grid.height) {
            for (c in 0 until grid.width) {
                val idx = grid.get(c, r)
                if (idx == 0) continue
                val color = palette.colorAt(idx) ?: continue
                drawRect(
                    color = Color(color.argb),
                    topLeft = Offset(c * cellW, r * cellH),
                    size = Size(cellW + 0.4f, cellH + 0.4f),
                )
            }
        }
        if (showGridLines) {
            for (c in 0..grid.width) {
                val x = c * cellW
                drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (r in 0..grid.height) {
                val y = r * cellH
                drawLine(gridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }
        if (showCodes && min(cellW, cellH) >= 18f) {
            drawCodes(grid, palette, min(cellW, cellH), 0f, 0f, cellW, cellH)
        }
        drawRect(
            color = ink.copy(alpha = 0.10f),
            size = size,
            style = Stroke(width = 2f),
        )
    }
}

/** 画色号（Compose Canvas 没有画文本的 API，走 nativeCanvas）。 */
private fun DrawScope.drawCodes(
    grid: BeadGrid,
    palette: Palette,
    cell: Float,
    originX: Float,
    originY: Float,
    cellW: Float = cell,
    cellH: Float = cell,
) {
    val canvas = drawContext.canvas.nativeCanvas
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = min(cellW, cellH) * 0.42f
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
            paint.color =
                if (ColorMath.luminance(color.argb) > 0.45f) 0xDD111111.toInt() else 0xEEFFFFFF.toInt()
            canvas.drawText(
                color.code,
                originX + c * cellW + cellW / 2f,
                originY + r * cellH + cellH / 2f + vo,
                paint,
            )
        }
    }
}
