package com.pindou.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.DitherMode
import com.pindou.studio.core.PaletteColor
import com.pindou.studio.core.QuantizeMode
import com.pindou.studio.core.QuantizeOptions
import com.pindou.studio.core.Quantizer
import com.pindou.studio.ui.components.BeadSlider
import com.pindou.studio.ui.components.ChipButton
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.ParamGroup
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.SectionTitle
import com.pindou.studio.ui.components.SwitchRow
import com.pindou.studio.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** 一套完整的以图生图参数（触发重算 + 预设存取共用）。 */
@kotlinx.serialization.Serializable
data class ImagePresetParams(
    val targetWidth: Int,
    val mode: String,
    val dither: String,
    val merge: Float,
    val removeBg: Boolean,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val gamma: Float,
    val maxColors: Int,
    val despeckle: Boolean,
)

/** 以图生草图面板。 */
@Composable
fun ImportSheetContent(
    vm: EditorViewModel,
    source: Bitmap?,
    onPickImage: () -> Unit,
    onDone: () -> Unit,
) {
    if (source == null) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("选择一张图片，自动转成拼豆图纸", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "支持照片与插画，转换在本地完成，不会上传",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPickImage) { Text("选择图片") }
            Spacer(Modifier.height(24.dp))
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val json = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }

    // 上次用过的参数：进门就沿用，不用每次都重新配（反馈里说的「历史参数保存」）
    val last = remember(source) {
        com.pindou.studio.data.ParamPresets.lastPayload(context, "image")?.let { raw ->
            runCatching { json.decodeFromString(ImagePresetParams.serializer(), raw) }.getOrNull()
        }
    }

    var targetWidth by remember(source) { mutableStateOf(last?.targetWidth ?: 64) }
    var mode by remember(source) {
        mutableStateOf(
            last?.mode?.let { runCatching { QuantizeMode.valueOf(it) }.getOrNull() } ?: QuantizeMode.AVERAGE,
        )
    }
    var dither by remember(source) {
        mutableStateOf(
            last?.dither?.let { runCatching { DitherMode.valueOf(it) }.getOrNull() } ?: DitherMode.NONE,
        )
    }
    var merge by remember(source) { mutableStateOf(last?.merge ?: 0f) }
    var removeBg by remember(source) { mutableStateOf(last?.removeBg ?: false) }
    var brightness by remember(source) { mutableStateOf(last?.brightness ?: 0f) }
    var contrast by remember(source) { mutableStateOf(last?.contrast ?: 0f) }
    var saturation by remember(source) { mutableStateOf(last?.saturation ?: 0f) }
    var gamma by remember(source) { mutableStateOf(last?.gamma ?: 1f) }
    var maxColors by remember(source) { mutableStateOf(last?.maxColors ?: 0) }
    var despeckle by remember(source) { mutableStateOf(last?.despeckle ?: false) }
    var excluded by remember(source) { mutableStateOf<Set<String>>(emptySet()) }
    var showExclude by remember { mutableStateOf(false) }

    // 参数一改就记下来（400ms 防抖，拖动滑块不会写几百次）
    LaunchedEffect(source, targetWidth, mode, dither, merge, removeBg, brightness, contrast, saturation, gamma, maxColors, despeckle) {
        if (source == null) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        runCatching {
            com.pindou.studio.data.ParamPresets.saveLastPayload(
                context,
                "image",
                json.encodeToString(
                    ImagePresetParams.serializer(),
                    ImagePresetParams(
                        targetWidth = targetWidth, mode = mode.name, dither = dither.name,
                        merge = merge, removeBg = removeBg, brightness = brightness,
                        contrast = contrast, saturation = saturation, gamma = gamma,
                        maxColors = maxColors, despeckle = despeckle,
                    ),
                ),
            )
        }
    }
    var preview by remember(source) { mutableStateOf<Bitmap?>(null) }
    var result by remember(source) { mutableStateOf<BeadGrid?>(null) }
    var computing by remember(source) { mutableStateOf(true) }

    // 实时预览：conflate 只保留最新一次参数，拖滑块时不会因为"每次改动都重来"而卡住
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow {
            ImagePresetParams(
                targetWidth = targetWidth, mode = mode.name, dither = dither.name,
                merge = merge, removeBg = removeBg, brightness = brightness,
                contrast = contrast, saturation = saturation, gamma = gamma,
                maxColors = maxColors, despeckle = despeckle,
            ) to excluded
        }.conflate().collect { (p, excludedNow) ->
        computing = true
        val options = QuantizeOptions(
            targetWidth = p.targetWidth,
            mode = runCatching { QuantizeMode.valueOf(p.mode) }.getOrDefault(QuantizeMode.AVERAGE),
            dither = runCatching { DitherMode.valueOf(p.dither) }.getOrDefault(DitherMode.NONE),
            mergeSimilarity = p.merge,
            removeBackground = p.removeBg,
            brightness = p.brightness,
            contrast = p.contrast,
            saturation = p.saturation,
            excludedCodes = excludedNow,
            maxColors = p.maxColors,
            gamma = p.gamma,
            despeckle = p.despeckle,
        )
        val grid = withContext(Dispatchers.Default) {
            Quantizer.quantize(src, vm.palette, options)
        }
        val bmp = withContext(Dispatchers.Default) {
            Quantizer.renderPreview(grid, vm.palette, 3)
        }
        result = grid
        preview = bmp
        computing = false
        }
    }

    // 参数预设
    var presets by remember { mutableStateOf(com.pindou.studio.data.ParamPresets.list(context, "image")) }
    var showSavePreset by remember { mutableStateOf(false) }

    if (showSavePreset) {
        com.pindou.studio.ui.components.PresetNameDialog(
            onDismiss = { showSavePreset = false },
            onConfirm = { name ->
                val payload = json.encodeToString(
                    ImagePresetParams.serializer(),
                    ImagePresetParams(
                        targetWidth = targetWidth, mode = mode.name, dither = dither.name,
                        merge = merge, removeBg = removeBg, brightness = brightness,
                        contrast = contrast, saturation = saturation, gamma = gamma,
                        maxColors = maxColors, despeckle = despeckle,
                    ),
                )
                presets = com.pindou.studio.data.ParamPresets.save(context, "image", name, payload)
                showSavePreset = false
            },
        )
    }

    // 三段式：预览固定在顶部、参数区独立滚动、应用按钮固定在底部
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = Ds.gutter),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(Ds.rPanel))
                .background(Ds.c.surface)
                .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rPanel)),
            contentAlignment = Alignment.Center,
        ) {
            val shown = preview
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            if (computing) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                result?.let { "输出 ${it.width}×${it.height} 格 · ${it.paintedCount()} 颗豆子" } ?: "计算中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPickImage) { Text("换一张") }
        }

        // ---- 参数区 ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        Spacer(Modifier.height(Ds.s2))
        com.pindou.studio.ui.components.PresetRow(
            presets = presets,
            onApply = { stored ->
                runCatching {
                    json.decodeFromString(ImagePresetParams.serializer(), stored.payload)
                }.getOrNull()?.let { p ->
                    targetWidth = p.targetWidth
                    mode = runCatching { QuantizeMode.valueOf(p.mode) }.getOrDefault(QuantizeMode.AVERAGE)
                    dither = runCatching { DitherMode.valueOf(p.dither) }.getOrDefault(DitherMode.NONE)
                    merge = p.merge
                    removeBg = p.removeBg
                    brightness = p.brightness
                    contrast = p.contrast
                    saturation = p.saturation
                    gamma = p.gamma
                    maxColors = p.maxColors
                    despeckle = p.despeckle
                }
            },
            onSave = { showSavePreset = true },
            onDelete = { stored ->
                presets = com.pindou.studio.data.ParamPresets.delete(context, "image", stored.id)
            },
        )

        ParamGroup("尺寸", Ds.c.sage) {
            BeadSlider(
                label = "目标宽度",
                value = targetWidth.toFloat(),
                valueRange = 8f..256f,
                valueText = "${targetWidth} 格",
                tint = Ds.c.sage,
                steps = 0,
            ) { targetWidth = it.toInt() }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 48, 64, 96, 128).forEach { preset ->
                    ChipButton("$preset", targetWidth == preset) { targetWidth = preset }
                }
            }
        }

        ParamGroup("取色模式", Ds.c.sky) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton("照片", mode == QuantizeMode.AVERAGE) { mode = QuantizeMode.AVERAGE }
                ChipButton("插画", mode == QuantizeMode.DOMINANT) { mode = QuantizeMode.DOMINANT }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (mode == QuantizeMode.AVERAGE) "平均：相邻像素混合，过渡更自然，适合照片"
                else "主色：取区域内出现最多的颜色，色块更锐利，适合插画与像素图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text("抖动", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DitherMode.entries.forEach { d ->
                    ChipButton(d.label, dither == d) { dither = d }
                }
            }
        }

        ParamGroup("配色", Ds.c.clay) {
            BeadSlider(
                label = "色数上限",
                value = maxColors.toFloat(),
                valueRange = 0f..24f,
                valueText = if (maxColors == 0) "不限" else "${maxColors} 色",
                tint = Ds.c.clay,
                steps = 25,
            ) { maxColors = it.toInt() }
            BeadSlider(
                label = "合并相近色",
                value = merge,
                valueRange = 0f..0.14f,
                valueText = if (merge <= 0f) "关闭" else String.format("%.3f", merge),
                tint = Ds.c.clay,
            ) { merge = it }
        }

        ParamGroup("优化", Ds.c.amber) {
            SwitchRow("移除背景", "从边缘清除主要背景色", removeBg) { removeBg = it }
            SwitchRow("去杂点", "把孤立的一格并进周围主色，成品更干净", despeckle) { despeckle = it }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("排除色号", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { showExclude = true }) {
                    Text(if (excluded.isEmpty()) "未排除" else "已排除 ${excluded.size} 个")
                }
            }
        }

        ParamGroup("影调", Ds.c.rose) {
            BeadSlider(label = "亮度", value = brightness, valueRange = -100f..100f, valueText = "${brightness.toInt()}", tint = Ds.c.rose) { brightness = it }
            BeadSlider(label = "对比度", value = contrast, valueRange = -100f..100f, valueText = "${contrast.toInt()}", tint = Ds.c.rose) { contrast = it }
            BeadSlider(label = "饱和度", value = saturation, valueRange = -100f..100f, valueText = "${saturation.toInt()}", tint = Ds.c.rose) { saturation = it }
            BeadSlider(
                label = "伽马",
                value = gamma,
                valueRange = 0.5f..2.0f,
                valueText = String.format("%.2f", gamma),
                tint = Ds.c.rose,
            ) { gamma = (it * 20f).toInt() / 20f }
        }

        Spacer(Modifier.height(Ds.s3))
        }

        // ---- 固定在底部的操作条 ----
        Spacer(Modifier.height(Ds.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val grid = result ?: return@Button
                    vm.applyImportedGrid(grid)
                    vm.message = "已生成拼豆图纸"
                    onDone()
                },
                enabled = result != null && !computing,
                modifier = Modifier.weight(1f),
            ) { Text("应用到画布") }
            ChipButton("取消", false) { onDone() }
        }
        Spacer(Modifier.height(Ds.s4))
    }

    if (showExclude) {
        ExcludeCodesDialog(
            vm = vm,
            excluded = excluded,
            onDismiss = { showExclude = false },
            onChange = { excluded = it },
        )
    }
}

@Composable
private fun ExcludeCodesDialog(
    vm: EditorViewModel,
    excluded: Set<String>,
    onDismiss: () -> Unit,
    onChange: (Set<String>) -> Unit,
) {
    var local by remember { mutableStateOf(excluded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排除色号") },
        text = {
            Column {
                Text(
                    "被排除的色号不会用于图片转换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(60.dp),
                    modifier = Modifier.heightIn(max = 380.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(vm.palette.colors) { color ->
                        val isExcluded = color.code.uppercase() in local
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    val code = color.code.uppercase()
                                    local = if (isExcluded) local - code else local + code
                                }
                                .padding(vertical = 3.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(color.argb)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isExcluded) {
                                    IconSquare(PIcon.Close, size = 26) {}
                                }
                            }
                            Text(
                                color.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isExcluded) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onChange(local)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = {
                local = emptySet()
                onChange(emptySet())
                onDismiss()
            }) { Text("全部启用") }
        },
    )
}
