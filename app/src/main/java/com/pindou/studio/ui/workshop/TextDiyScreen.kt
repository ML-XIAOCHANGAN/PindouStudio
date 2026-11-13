package com.pindou.studio.ui.workshop

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.Palette
import com.pindou.studio.core.TextBeadEngine
import com.pindou.studio.core.TextSpec
import com.pindou.studio.ui.components.BeadSlider
import com.pindou.studio.ui.components.ChipButton
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.ParamGroup
import com.pindou.studio.ui.components.PrimaryButton
import com.pindou.studio.ui.components.SwitchRow
import com.pindou.studio.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * 字体 DIY：文字 → 拼豆图纸。
 *
 * 布局分三段：顶部固定预览、中间可滚动参数、底部固定生成。
 * 参数按「字形 → 尺寸 → 精度 → 描边 → 底板 → 配色」分组，符合用户的决策顺序。
 */
/** 一套完整的字体 DIY 参数（既用于触发重算，也用于预设存取）。 */
@kotlinx.serialization.Serializable
data class TextPresetParams(
    val fontKey: String,
    val bold: Boolean,
    val italic: Boolean,
    val widthScale: Float,
    val cellHeight: Int,
    val letterSpacing: Int,
    val lineSpacing: Int,
    val supersample: Int,
    val threshold: Float,
    val outlineWidth: Int,
    val repair: Boolean,
    val boardSize: Int,
    val bodyArgb: Int,
    val outlineArgb: Int,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TextDiyContent(
    vm: WorkshopViewModel,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val json = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    val palette = remember { vm.workingPalette() }
    var fonts by remember { mutableStateOf(FontCatalog.all(context)) }
    var pickerOpen by remember { mutableStateOf(false) }

    // 上次用过的参数：进来就沿用，省得每次重配字高 / 字距 / 阈值
    val last = remember {
        com.pindou.studio.data.ParamPresets.lastPayload(context, "text")?.let { raw ->
            runCatching { json.decodeFromString(TextPresetParams.serializer(), raw) }.getOrNull()
        }
    }

    var text by remember { mutableStateOf("拼豆工坊") }
    var fontKey by remember {
        mutableStateOf(last?.fontKey?.takeIf { key -> fonts.any { it.key == key } } ?: fonts.firstOrNull()?.key ?: "")
    }
    // 导入手机里的字体文件（SAF 选择 → 复制到 files/fonts → 出现在列表里）
    val fontImporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val copied = runCatching {
                val name = "import_" + System.currentTimeMillis() + ".ttf"
                val tmp = com.pindou.studio.util.FileUtils.copyUriToCache(context, uri, name)
                if (tmp == null) null else FontCatalog.importFont(context, tmp)
            }.getOrNull()
            fonts = FontCatalog.all(context)
            if (copied != null) {
                fontKey = copied.key
                pickerOpen = false
            }
        }
    }

    var bold by remember { mutableStateOf(last?.bold ?: false) }
    var italic by remember { mutableStateOf(last?.italic ?: false) }
    var widthScale by remember { mutableStateOf(last?.widthScale ?: 1f) }
    var cellHeight by remember { mutableStateOf((last?.cellHeight ?: 14).toFloat()) }
    var letterSpacing by remember { mutableStateOf((last?.letterSpacing ?: 1).toFloat()) }
    var lineSpacing by remember { mutableStateOf((last?.lineSpacing ?: 2).toFloat()) }
    var supersample by remember { mutableStateOf(last?.supersample ?: 4) }
    var threshold by remember { mutableStateOf(last?.threshold ?: 0.45f) }
    var outlineWidth by remember { mutableStateOf((last?.outlineWidth ?: 1).toFloat()) }
    var repair by remember { mutableStateOf(last?.repair ?: true) }
    var boardSize by remember { mutableStateOf(last?.boardSize ?: 52) }

    val defaults = remember(palette) { defaultColors(palette) }
    var bodyArgb by remember { mutableStateOf(last?.bodyArgb ?: defaults.first) }
    var outlineArgb by remember { mutableStateOf(last?.outlineArgb ?: defaults.second) }
    var colorTarget by remember { mutableStateOf(0) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf("") }
    var computing by remember { mutableStateOf(false) }
    var masks by remember { mutableStateOf<com.pindou.studio.core.TextMasks?>(null) }
    var missingChars by remember { mutableStateOf<List<String>>(emptyList()) }

    // 参数一改就记下来（400ms 防抖），下次打开自动沿用
    LaunchedEffect(
        fontKey, bold, italic, widthScale, cellHeight, letterSpacing, lineSpacing,
        supersample, threshold, outlineWidth, repair, boardSize, bodyArgb, outlineArgb,
    ) {
        kotlinx.coroutines.delay(400)
        runCatching {
            com.pindou.studio.data.ParamPresets.saveLastPayload(
                context,
                "text",
                json.encodeToString(
                    TextPresetParams.serializer(),
                    TextPresetParams(
                        fontKey = fontKey, bold = bold, italic = italic, widthScale = widthScale,
                        cellHeight = cellHeight.toInt(), letterSpacing = letterSpacing.toInt(),
                        lineSpacing = lineSpacing.toInt(), supersample = supersample,
                        threshold = threshold, outlineWidth = outlineWidth.toInt(), repair = repair,
                        boardSize = boardSize, bodyArgb = bodyArgb, outlineArgb = outlineArgb,
                    ),
                ),
            )
        }
    }

    // 参数预设
    var presets by remember { mutableStateOf(com.pindou.studio.data.ParamPresets.list(context, "text")) }
    var showSavePreset by remember { mutableStateOf(false) }

    val font = fonts.firstOrNull { it.key == fontKey } ?: fonts.firstOrNull()
    LaunchedEffect(fontKey) {
        if (fontKey.isNotBlank()) FontCatalog.markUsed(context, fontKey)
    }
    var typeface by remember { mutableStateOf<android.graphics.Typeface?>(null) }
    LaunchedEffect(fontKey) {
        val target = font ?: return@LaunchedEffect
        typeface = withContext(Dispatchers.IO) { FontCatalog.loadCached(context, target) }
    }

    // 实时预览：snapshotFlow + conflate —— 拖滑块时只渲染最新一次，
    // 先立刻出一张低倍速写图，再补一张精细图，手感是"跟手"而不是"等算完"
    LaunchedEffect(Unit) {
        snapshotFlow {
            TextPresetParams(
                fontKey = fontKey, bold = bold, italic = italic, widthScale = widthScale,
                cellHeight = cellHeight.toInt(), letterSpacing = letterSpacing.toInt(),
                lineSpacing = lineSpacing.toInt(), supersample = supersample,
                threshold = threshold, outlineWidth = outlineWidth.toInt(),
                repair = repair, boardSize = boardSize,
                bodyArgb = bodyArgb, outlineArgb = outlineArgb,
            ) to text
        }.conflate().collect { (p, currentText) ->
            computing = true
            val tf = typeface
            fun specOf(ss: Int) = TextSpec(
                text = currentText,
                typeface = tf,
                cellHeight = p.cellHeight,
                letterSpacing = p.letterSpacing,
                lineSpacing = p.lineSpacing,
                threshold = p.threshold,
                outlineWidth = p.outlineWidth,
                repair = p.repair,
                bold = p.bold,
                italic = p.italic,
                widthScale = p.widthScale,
                supersample = ss,
            )
            // 快图：2× 超采样 + 小像素预览
            val quick = withContext(Dispatchers.Default) {
                runCatching { TextBeadEngine.layout(specOf(2)) }.getOrNull()
            }
            if (quick != null) {
                preview = withContext(Dispatchers.Default) {
                    TextBeadEngine.preview(quick, p.bodyArgb, p.outlineArgb, 4, p.boardSize)
                }
                masks = quick
                info = infoOf(quick, p)
            }
            // 精细图：按用户设定的倍率重算
            val full = withContext(Dispatchers.Default) {
                runCatching { TextBeadEngine.layout(specOf(p.supersample)) }.getOrNull()
            }
            if (full != null) {
                preview = withContext(Dispatchers.Default) {
                    TextBeadEngine.preview(full, p.bodyArgb, p.outlineArgb, 6, p.boardSize)
                }
                masks = full
                info = infoOf(full, p)
            } else if (quick == null) {
                preview = null
                // 必须把上一次的结果清掉，否则「生成图纸」会用旧掩膜生成一张过时的图纸
                masks = null
                info = if (currentText.isBlank()) {
                    "输入文字后才能生成图纸"
                } else {
                    "内容超出一张画布（1024 格）：把字高调小一点，或者在输入框里多敲几个回车分行"
                }
            }
            missingChars = withContext(Dispatchers.Default) {
                val probe = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    setTypeface(tf ?: android.graphics.Typeface.DEFAULT)
                }
                currentText.replace("\n", "").toSet()
                    .filter { !it.isWhitespace() && !probe.hasGlyph(it.toString()) }
                    .map { it.toString() }
            }
            computing = false
        }
    }

    if (showSavePreset) {
        com.pindou.studio.ui.components.PresetNameDialog(
            onDismiss = { showSavePreset = false },
            onConfirm = { name ->
                val payload = json.encodeToString(
                    TextPresetParams.serializer(),
                    TextPresetParams(
                        fontKey = fontKey, bold = bold, italic = italic, widthScale = widthScale,
                        cellHeight = cellHeight.toInt(), letterSpacing = letterSpacing.toInt(),
                        lineSpacing = lineSpacing.toInt(), supersample = supersample,
                        threshold = threshold, outlineWidth = outlineWidth.toInt(),
                        repair = repair, boardSize = boardSize,
                        bodyArgb = bodyArgb, outlineArgb = outlineArgb,
                    ),
                )
                presets = com.pindou.studio.data.ParamPresets.save(context, "text", name, payload)
                showSavePreset = false
            },
        )
    }

    Column(modifier.fillMaxWidth().padding(horizontal = Ds.gutter)) {
        // ---- 固定预览 ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(Ds.rPanel))
                .background(Ds.c.surface)
                .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rPanel))
                .padding(Ds.s2),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = preview
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    if (computing) "生成中…" else "输入文字后这里会显示预览",
                    style = Ds.small,
                    color = Ds.c.inkFaint,
                )
            }
        }
        Spacer(Modifier.height(Ds.s1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaLabel(info)
            if (missingChars.isNotEmpty()) {
                Spacer(Modifier.width(Ds.s2))
                Text("缺字 ${missingChars.size} 个", style = Ds.label, color = Ds.c.amber)
            }
        }

        // ---- 参数区 ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Ds.s2))
            // 预设：点=套用，长按=删除
            com.pindou.studio.ui.components.PresetRow(
                presets = presets,
                onApply = { stored ->
                    runCatching {
                        json.decodeFromString(TextPresetParams.serializer(), stored.payload)
                    }.getOrNull()?.let { p ->
                        fontKey = p.fontKey
                        bold = p.bold
                        italic = p.italic
                        widthScale = p.widthScale
                        cellHeight = p.cellHeight.toFloat()
                        letterSpacing = p.letterSpacing.toFloat()
                        lineSpacing = p.lineSpacing.toFloat()
                        supersample = p.supersample
                        threshold = p.threshold
                        outlineWidth = p.outlineWidth.toFloat()
                        repair = p.repair
                        boardSize = p.boardSize
                        bodyArgb = p.bodyArgb
                        outlineArgb = p.outlineArgb
                    }
                },
                onSave = { showSavePreset = true },
                onDelete = { stored ->
                    presets = com.pindou.studio.data.ParamPresets.delete(context, "text", stored.id)
                },
            )
            Spacer(Modifier.height(Ds.s2))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                label = { Text("文字内容", style = Ds.label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
            )
            Spacer(Modifier.height(Ds.s1))
            MetaLabel("不会自动折行：要分行就在输入框里回车；一行排多长就是多长")

            // 字形
            ParamGroup("字形", Ds.c.clay) {
                // 字体可能几十上百款，不再铺一排 chip，改成"当前字体 + 更换"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Ds.rControl))
                        .background(Ds.c.surface)
                        .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rControl))
                        .clickable { pickerOpen = true }
                        .padding(horizontal = Ds.s3, vertical = Ds.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        MetaLabel("当前字体")
                        Text(
                            font?.name ?: "选择字体",
                            style = Ds.h3.copy(
                                fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Default,
                            ),
                            color = Ds.c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (font != null && !font.hasChinese) {
                            Text("这款只含英文字形", style = Ds.label, color = Ds.c.rose)
                        }
                    }
                    Text("更换", style = Ds.button, color = Ds.c.clay)
                    Spacer(Modifier.width(Ds.s1))
                    PIconView(PIcon.ChevronRight, Ds.c.clay, Modifier.size(14.dp))
                }
                Spacer(Modifier.height(Ds.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    ChipButton("常规", !bold) { bold = false }
                    ChipButton("加粗", bold) { bold = true }
                    Spacer(Modifier.width(Ds.s1))
                    ChipButton("不倾斜", !italic) { italic = false }
                    ChipButton("倾斜", italic) { italic = true }
                }
                BeadSlider(
                    label = "字宽",
                    value = widthScale,
                    valueRange = 0.70f..1.30f,
                    valueText = String.format("%.0f%%", widthScale * 100),
                    tint = Ds.c.clay,
                ) { widthScale = (it * 20f).toInt() / 20f }
            }

            // 尺寸
            ParamGroup("尺寸", Ds.c.sage) {
                BeadSlider(
                    label = "字高（格）",
                    value = cellHeight,
                    valueRange = 6f..40f,
                    valueText = "${cellHeight.toInt()} 格",
                    tint = Ds.c.sage,
                    steps = 35,
                ) { cellHeight = it }
                BeadSlider(
                    label = "字间距（格）",
                    value = letterSpacing,
                    valueRange = 0f..6f,
                    valueText = "${letterSpacing.toInt()} 格",
                    tint = Ds.c.sage,
                    steps = 7,
                ) { letterSpacing = it }
                BeadSlider(
                    label = "行间距（格）",
                    value = lineSpacing,
                    valueRange = 0f..8f,
                    valueText = "${lineSpacing.toInt()} 格",
                    tint = Ds.c.sage,
                    steps = 9,
                ) { lineSpacing = it }
            }

            // 精度
            ParamGroup("精度", Ds.c.sky) {
                Text("超采样倍率", style = Ds.body, color = Ds.c.ink)
                Spacer(Modifier.height(Ds.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    listOf(2 to "2× 快", 4 to "4× 标准", 6 to "6× 细腻", 8 to "8× 极致").forEach { (v, label) ->
                        ChipButton(label, supersample == v) { supersample = v }
                    }
                }
                Spacer(Modifier.height(Ds.s2))
                MetaLabel("倍率越高笔画边缘越准，代价是生成更慢")
                BeadSlider(
                    label = "覆盖率阈值",
                    value = threshold,
                    valueRange = 0.30f..0.70f,
                    valueText = String.format("%.2f", threshold),
                    tint = Ds.c.sky,
                ) { threshold = (it * 100f).toInt() / 100f }
            }

            // 描边与修复
            ParamGroup("描边与修复", Ds.c.amber) {
                SwitchRow(
                    title = "描边",
                    subtitle = "给笔画加一圈轮廓，浅色底板上更清楚",
                    checked = outlineWidth > 0f,
                ) { on -> outlineWidth = if (on) 1f else 0f }
                if (outlineWidth > 0f) {
                    BeadSlider(
                        label = "描边厚度（格）",
                        value = outlineWidth,
                        valueRange = 1f..3f,
                        valueText = "${outlineWidth.toInt()} 格",
                        tint = Ds.c.amber,
                        steps = 3,
                    ) { outlineWidth = it }
                }
                SwitchRow(
                    title = "断笔修复",
                    subtitle = "自动补齐细笔画断开的地方（只补不改，预览可见）",
                    checked = repair,
                ) { repair = it }
            }

            // 底板
            ParamGroup("底板", Ds.c.rose) {
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    listOf(0 to "不分板", 14 to "14×14", 29 to "29×29", 50 to "50×50").forEach { (size, label) ->
                        ChipButton(label, boardSize == size) { boardSize = size }
                    }
                }
                Spacer(Modifier.height(Ds.s2))
                MetaLabel("超过一块底板的部分在预览里用深色线标出")
            }

            // 配色
            ParamGroup("配色", Ds.c.clay) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorTargetChip("主体", bodyArgb, colorTarget == 0) { colorTarget = 0 }
                    Spacer(Modifier.width(Ds.s2))
                    if (outlineWidth > 0f) {
                        ColorTargetChip("描边", outlineArgb, colorTarget == 1) { colorTarget = 1 }
                    }
                }
                Spacer(Modifier.height(Ds.s2))
                // 热门配色：不想自己搭的话点一下就成套换好（主体 + 描边）
                if (outlineWidth > 0f) {
                    MetaLabel("热门配色")
                    Spacer(Modifier.height(Ds.s1))
                    Row(
                        Modifier.fillMaxWidth().horizontalScrollCompatRow(),
                        horizontalArrangement = Arrangement.spacedBy(Ds.s2),
                    ) {
                        TextColorPresets.forEach { preset ->
                            val active = preset.body == bodyArgb && preset.outline == outlineArgb
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(Ds.rControl))
                                    .background(if (active) Ds.c.surfaceAlt else Ds.c.surface)
                                    .border(
                                        if (active) 2.dp else Ds.stroke,
                                        if (active) Ds.c.clay else Ds.c.hairline,
                                        RoundedCornerShape(Ds.rControl),
                                    )
                                    .clickable {
                                        bodyArgb = preset.body
                                        outlineArgb = preset.outline
                                    }
                                    .padding(horizontal = Ds.s2, vertical = Ds.s1),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(preset.body)),
                                )
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(preset.outline)),
                                )
                                Spacer(Modifier.width(Ds.s2))
                                Text(preset.name, style = Ds.label, color = Ds.c.ink)
                            }
                        }
                    }
                    Spacer(Modifier.height(Ds.s2))
                }
                PaletteStrip(
                    palette = palette,
                    selected = if (colorTarget == 0) bodyArgb else outlineArgb,
                ) { argb -> if (colorTarget == 0) bodyArgb = argb else outlineArgb = argb }
            }
            Spacer(Modifier.height(Ds.s3))
        }

        // ---- 固定底部 ----
        PrimaryButton(
            text = if (computing) "生成中…" else "生成图纸",
            modifier = Modifier.fillMaxWidth(),
            enabled = !computing && masks != null,
        ) {
            val m = masks ?: return@PrimaryButton
            val grid = TextBeadEngine.toGrid(m, palette, bodyArgb, outlineArgb)
            val name = text.lineSequence().firstOrNull()?.take(12)?.ifBlank { "文字图纸" } ?: "文字图纸"
            val id = vm.createFromGrid(name, grid, palette)
            onOpenProject(id)
        }
        Spacer(Modifier.height(Ds.s1))
        MetaLabel("生成后会变成一个普通工程，可以继续在编辑器里改色、加描边、导出")
        Spacer(Modifier.height(Ds.s2))
    }

    // 字体选择器：搜索 + 分类 + 所见即所得预览 + 最近使用
    if (pickerOpen) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            ),
            containerColor = Ds.c.surface,
            scrimColor = Ds.c.ink.copy(alpha = 0.14f),
        ) {
            FontPickerSheet(
                fonts = fonts,
                selectedKey = fontKey,
                sampleText = text,
                onPick = {
                    fontKey = it.key
                    pickerOpen = false
                },
                onImport = { fontImporter.launch("font/*") },
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

private fun infoOf(
    m: com.pindou.studio.core.TextMasks,
    p: TextPresetParams,
): String {
    var beads = 0
    m.eachCell { _, _, _ -> beads++ }
    val colors = if (p.outlineWidth > 0) 2 else 1
    val boards = TextBeadEngine.boardCount(m.width, m.height, p.boardSize)
    return "${m.width}×${m.height} 格 · $beads 颗 · $colors 色" +
        (if (p.boardSize > 0) " · $boards 块板" else "") +
        if (m.repairedCells > 0) " · 断笔修复 ${m.repairedCells} 格" else ""
}

@Composable
private fun ColorTargetChip(label: String, argb: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Ds.c.ink.copy(alpha = 0.10f) else Ds.c.surfaceAlt)
            .clickable { onClick() }
            .padding(horizontal = Ds.s3, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, Ds.c.hairline, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = Ds.label, color = if (selected) Ds.c.ink else Ds.c.inkSoft)
    }
}

@Composable
private fun PaletteStrip(palette: Palette, selected: Int, onPick: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Ds.s2),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(palette.colors, key = { it.code }) { color ->
            val argb = remember(color.hex) { ColorMath.parseHex(color.hex) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (argb == selected) 2.dp else 1.dp,
                            color = if (argb == selected) Ds.c.ink else Ds.c.hairline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(argb) },
                )
                Spacer(Modifier.height(2.dp))
                Text(color.code, fontSize = 9.sp, color = Ds.c.inkFaint, fontWeight = FontWeight.Normal)
            }
        }
    }
}

/** 一组热门配色（主体 + 描边），直接点一下就成套换好。 */
private data class TextColorPreset(val name: String, val body: Int, val outline: Int)

private val TextColorPresets = listOf(
    TextColorPreset("墨黑 · 米白", 0xFF2B2622.toInt(), 0xFFF3E8D2.toInt()),
    TextColorPreset("玫红 · 奶白", 0xFFC4517A.toInt(), 0xFFFFF1F3.toInt()),
    TextColorPreset("松绿 · 奶油", 0xFF2F6B4F.toInt(), 0xFFF7F0DC.toInt()),
    TextColorPreset("焦糖 · 奶昔", 0xFF9A5B2C.toInt(), 0xFFFFE9C9.toInt()),
    TextColorPreset("海蓝 · 白云", 0xFF2C5D82.toInt(), 0xFFEAF2F7.toInt()),
)

@Composable
private fun Modifier.horizontalScrollCompatRow(): Modifier =
    this.horizontalScroll(rememberScrollState())

/** 默认配色：主体取有彩度的深色，描边取同色相更亮一档。 */
private fun defaultColors(palette: Palette): Pair<Int, Int> {
    if (palette.colors.isEmpty()) return 0xFF222222.toInt() to 0xFFBBBBBB.toInt()
    val entries = palette.colors.map { it to it.lab }
    val chromatic = entries.filter { it.second[1] * it.second[1] + it.second[2] * it.second[2] > 0.0025f }
    val body = (if (chromatic.isNotEmpty()) chromatic else entries).minByOrNull { it.second[0] }!!
    val bodyL = body.second[0]
    val bodyHue = hue(body.second)
    val outline = entries
        .filter { it.first.code != body.first.code && it.second[0] > bodyL + 0.22f }
        .minByOrNull {
            val l = it.second[0]
            val hueGap = hueDistance(hue(it.second), bodyHue)
            kotlin.math.abs(l - (bodyL + 0.34f)) + hueGap * 0.6f
        }
    return ColorMath.parseHex(body.first.hex) to
        ColorMath.parseHex((outline ?: body).first.hex)
}

private fun hue(lab: FloatArray): Float = kotlin.math.atan2(lab[2], lab[1])

private fun hueDistance(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % (2f * Math.PI.toFloat())
    return if (d > Math.PI) 2f * Math.PI.toFloat() - d else d
}
