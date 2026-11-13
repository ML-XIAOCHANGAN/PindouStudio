package com.pindou.studio.ui.mix

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.Palette
import com.pindou.studio.ui.components.BeadSlider
import com.pindou.studio.ui.components.ColoredChip
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PrimaryButton
import com.pindou.studio.ui.components.SecondaryButton
import com.pindou.studio.ui.components.SwitchRow
import com.pindou.studio.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MIX 叠加编辑器。
 *
 * 从「我的作品」把一张作品长按拖到另一张上就会进到这里：
 * 上下层可换、上层可加蒙版（画笔擦/恢复、反相、清空）、整体透明度可调，
 * 「生成作品」后就是一张新的图纸。
 */
@Composable
fun MixScreen(
    idA: String,
    idB: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: MixViewModel = viewModel(
        key = "mix_${idA}_${idB}",
        factory = MixViewModel.Factory(app, idA, idB),
    )
    LaunchedEffect(Unit) { vm.load() }

    var composite by remember { mutableStateOf<BeadGrid?>(null) }
    var maskSnap by remember { mutableStateOf<FloatArray?>(null) }
    var saving by remember { mutableStateOf(false) }

    // 参数变一次只重算一次合成（不是每帧都算）
    LaunchedEffect(vm.version, vm.loading) {
        if (vm.loading) return@LaunchedEffect
        val result = withContext(Dispatchers.Default) {
            vm.compose() to vm.maskSnapshot()
        }
        composite = result.first
        maskSnap = result.second
    }

    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Ds.s1, end = Ds.s2, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Back, size = 40, onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = Ds.s2)) {
                Text("MIX 叠加", style = Ds.h3, color = Ds.c.ink)
                MetaLabel(
                    "${vm.outWidth}×${vm.outHeight} 格 · ${if (vm.secondOnTop) "${vm.nameB} 在上" else "${vm.nameA} 在上"}",
                )
            }
        }
        Hairline()

        if (vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ds.c.clay, modifier = Modifier.size(28.dp))
            }
            return@Column
        }
        vm.error?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(msg, style = Ds.body, color = Ds.c.rose)
            }
            return@Column
        }

        // ---------- 画布 ----------
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
                        // 固定高度：按宽度撑正方形会溢出到下面的控制区，
                        // 导致"看到的和点到的不一致"
                        .height(300.dp)
                        .clip(RoundedCornerShape(Ds.rPanel))
                        .background(Ds.c.canvasGround)
                        .padding(Ds.s2),
                ) {
                    MixCanvas(
                        grid = composite,
                        mask = maskSnap,
                        palette = vm.palette,
                        width = vm.outWidth,
                        height = vm.outHeight,
                        showMask = vm.showMask,
                        painting = vm.showMask,
                        onPaint = { c, r -> vm.paintMask(c, r) },
                    )
                }
            }
        }

        // ---------- 控制区 ----------
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ds.gutter),
        ) {
            MetaLabel("上下层")
            Spacer(Modifier.height(Ds.s2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LayerChip(
                    name = vm.nameA,
                    onTop = !vm.secondOnTop,
                    tint = Ds.c.sky,
                    modifier = Modifier.weight(1f),
                ) {
                    // 点 A 想让它当上层：当前是 B 在上就换过来
                    if (vm.secondOnTop) vm.swapLayers()
                }
                IconSquare(PIcon.FlipV, size = 40) { vm.swapLayers() }
                LayerChip(
                    name = vm.nameB,
                    onTop = vm.secondOnTop,
                    tint = Ds.c.clay,
                    modifier = Modifier.weight(1f),
                ) {
                    // 点 B 同理
                    if (!vm.secondOnTop) vm.swapLayers()
                }
            }
            Spacer(Modifier.height(Ds.s1))
            MetaLabel("点其中一张把它设为上层，中间按钮可直接交换。上层有空位的地方会露出下层。")

            Spacer(Modifier.height(Ds.s4))
            BeadSlider(
                label = "上层透明度",
                value = vm.opacity,
                valueRange = 0f..1f,
                valueText = "${(vm.opacity * 100).toInt()}%",
                tint = Ds.c.clay,
            ) { vm.updateOpacity(it) }
            MetaLabel("拼豆没有半透明豆子，这里用交错打点来表达：50% 就是隔一颗露一颗下层。")

            Spacer(Modifier.height(Ds.s4))
            SwitchRow("显示并编辑蒙版", "在画布上涂，露出/遮住上层", checked = vm.showMask) {
                vm.toggleShowMask()
            }
            if (vm.showMask) {
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    ColoredChip("擦除", Ds.c.rose, vm.maskErase) { vm.updateMaskErase(true) }
                    ColoredChip("恢复", Ds.c.sage, !vm.maskErase) { vm.updateMaskErase(false) }
                }
                Spacer(Modifier.height(Ds.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    ColoredChip("反相", Ds.c.amber, false) { vm.invertMask() }
                    ColoredChip("清空蒙版", Ds.c.inkSoft, false) { vm.resetMask() }
                }
                Spacer(Modifier.height(Ds.s1))
                MetaLabel("擦除 = 这块只留下面那张；恢复 = 这块显示上层。手指直接在上面画就行。")
            }
            Spacer(Modifier.height(Ds.s6))
        }

        // ---------- 底部 ----------
        Column(Modifier.fillMaxWidth().background(Ds.c.ground).padding(horizontal = Ds.gutter, vertical = Ds.s3)) {
            Hairline()
            Spacer(Modifier.height(Ds.s3))
            PrimaryButton(
                if (saving) "生成中…" else "生成作品",
                Modifier.fillMaxWidth(),
                icon = PIcon.Save,
                enabled = !saving,
            ) {
                saving = true
                vm.saveAsProject("${vm.nameA} × ${vm.nameB}") { id ->
                    saving = false
                    if (id != null) onCreated(id)
                }
            }
            Spacer(Modifier.height(Ds.s2))
            SecondaryButton("取消", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun LayerChip(
    name: String,
    onTop: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Ds.rPanel))
            .background(if (onTop) tint.copy(alpha = 0.18f) else Ds.c.surface)
            .border(
                if (onTop) 2.dp else Ds.stroke,
                if (onTop) tint else Ds.c.hairline,
                RoundedCornerShape(Ds.rPanel),
            )
            .padding(horizontal = Ds.s3, vertical = Ds.s2)
            .clickableCompat(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = Ds.body,
                color = Ds.c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (onTop) "上层" else "下层",
                style = Ds.label,
                color = if (onTop) tint else Ds.c.inkFaint,
            )
        }
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** 合成结果 / 蒙版预览画布。 */
@Composable
private fun MixCanvas(
    grid: BeadGrid?,
    mask: FloatArray?,
    palette: Palette,
    width: Int,
    height: Int,
    showMask: Boolean,
    painting: Boolean,
    onPaint: (Int, Int) -> Unit,
) {
    val board = Ds.c.surface
    val ink = Ds.c.ink

    Canvas(
        Modifier
            .fillMaxSize()
            // 用 pointerInput 的 key 控制"是否可涂"，避免在 Modifier 链里做条件拼接
            .pointerInput(painting, width, height) {
                if (!painting) return@pointerInput
                // 画面按"短边"等比缩放并居中，所以命中测试也要用同一套换算
                fun cellOf(w: Int, h: Int): Float = minOf(
                    w.toFloat() / width,
                    h.toFloat() / height,
                )
                fun originOf(w: Int, h: Int, cell: Float): Pair<Float, Float> =
                    (w - width * cell) / 2f to (h - height * cell) / 2f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cell = cellOf(size.width, size.height)
                    val (ox, oy) = originOf(size.width, size.height, cell)
                    fun emit(p: androidx.compose.ui.geometry.Offset) {
                        val c = ((p.x - ox) / cell).toInt()
                        val r = ((p.y - oy) / cell).toInt()
                        if (c in 0 until width && r in 0 until height) onPaint(c, r)
                    }
                    emit(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        emit(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawRect(board, size = size)
        if (grid == null) return@Canvas
        val cellPx = minOf(size.width / grid.width, size.height / grid.height)
        val originX = (size.width - grid.width * cellPx) / 2f
        val originY = (size.height - grid.height * cellPx) / 2f
        val cellW = cellPx
        val cellH = cellPx
        for (r in 0 until grid.height) {
            for (c in 0 until grid.width) {
                val idx = grid.get(c, r)
                if (idx == 0) continue
                val color = palette.colorAt(idx) ?: continue
                var argb = color.argb
                if (showMask && mask != null) {
                    // 蒙版视图：被擦掉的格子在画面上淡出，方便看清涂了哪儿
                    val m = mask.getOrElse(r * width + c) { 1f }
                    val alpha = (0x22 + (0xFF - 0x22) * m).toInt()
                    argb = (argb and 0x00FFFFFF) or (alpha shl 24)
                }
                drawRect(
                    color = Color(argb),
                    topLeft = Offset(originX + c * cellW, originY + r * cellH),
                    size = Size(cellW + 0.4f, cellH + 0.4f),
                )
            }
        }
        drawRect(
            color = ink.copy(alpha = 0.10f),
            size = size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
    }
}
