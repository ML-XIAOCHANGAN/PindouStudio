package com.pindou.studio.ui.workshop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pindou.studio.core.ColorCombo
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.PatternTemplate
import com.pindou.studio.ui.components.ChipButton
import com.pindou.studio.ui.components.ColoredChip
import com.pindou.studio.ui.components.DsCard
import com.pindou.studio.ui.components.enterStagger
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.MetaLabel
import androidx.compose.foundation.border
import com.pindou.studio.ui.components.PatternPlate
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.PrimaryButton
import com.pindou.studio.ui.components.SecondaryButton
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.theme.Ds

/** 创意工坊：模板、配色灵感、小技巧。 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun WorkshopScreen(
    onOpenProject: (String) -> Unit,
    onOpenTemplate: (String) -> Unit = {},
) {
    val vm: WorkshopViewModel = viewModel()
    var tab by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    var previewCombo by remember { mutableStateOf<ColorCombo?>(null) }
    val categories = remember { listOf("全部") + com.pindou.studio.core.PatternTemplates.categories() }
    val visibleTemplates = remember(category, query) {
        val q = query.trim()
        vm.templates.filter { t ->
            (category == "全部" || t.category == category) &&
                (q.isEmpty() ||
                    t.name.contains(q, ignoreCase = true) ||
                    t.note.contains(q, ignoreCase = true) ||
                    t.category.contains(q, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = Ds.gutter, end = Ds.gutter, top = Ds.s6)) {
            Text("创意工坊", style = Ds.h1, color = Ds.c.ink)
            Spacer(Modifier.height(Ds.s3))
            Text(
                "没有灵感的时候，从一个模板、一组配色，或者一条小技巧开始。",
                style = Ds.body,
                color = Ds.c.inkSoft,
            )
            Spacer(Modifier.height(Ds.s6))
            Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                ChipButton("模板", tab == 0) { tab = 0 }
                ChipButton("配色", tab == 1) { tab = 1 }
                ChipButton("字体DIY", tab == 2) { tab = 2 }
            }
            Spacer(Modifier.height(Ds.s2))
        }

        if (tab == 2) {
            TextDiyContent(
                vm = vm,
                onOpenProject = onOpenProject,
                modifier = Modifier.weight(1f),
            )
        } else {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = Ds.gutter, end = Ds.gutter, bottom = Ds.s12),
        verticalArrangement = Arrangement.spacedBy(Ds.s2),
    ) {

        if (tab == 0) {
            item {
                Column {
                    SectionHeader("模板库", "${visibleTemplates.size} 个")
                    MetaLabel("点开任意一个先预览，确认后再切「编辑」")
                    Spacer(Modifier.height(Ds.s3))
                    TemplateSearchField(query = query, onQueryChange = { query = it })
                    Spacer(Modifier.height(Ds.s3))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Ds.s2),
                        verticalArrangement = Arrangement.spacedBy(Ds.s2),
                    ) {
                        categories.forEachIndexed { i, name ->
                            ColoredChip(
                                text = name,
                                tint = Ds.c.cyclic(i),
                                selected = category == name,
                            ) { category = name }
                        }
                    }
                    Spacer(Modifier.height(Ds.s3))
                }
            }
            if (visibleTemplates.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Ds.s8),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("没有找到匹配的模板", style = Ds.h3, color = Ds.c.ink)
                        Spacer(Modifier.height(Ds.s1))
                        Text(
                            "换个关键词，或者切回「全部」分类看看。",
                            style = Ds.small,
                            color = Ds.c.inkSoft,
                        )
                    }
                }
            }
            itemsIndexed(visibleTemplates, key = { _, t -> t.id }) { index, template ->
                val tint = Ds.c.cyclicSoft(categories.indexOf(template.category) + 1)
                TemplateCard(
                    template = template,
                    preview = vm.templatePreview(template),
                    tint = tint,
                    accent = Ds.c.cyclic(categories.indexOf(template.category) + 1),
                    modifier = Modifier.enterStagger(index),
                ) { onOpenTemplate(template.id) }
            }
            item { Spacer(Modifier.height(Ds.s4)) }
        }

        if (tab == 1) {
            item {
                Column {
                    SectionHeader("配色灵感", "${vm.combos.size} 组")
                    MetaLabel("每组 4 个颜色，存成限定色卡后更好买豆")
                    Spacer(Modifier.height(Ds.s3))
                }
            }
            items(vm.combos, key = { it.id }) { combo ->
                ComboCard(combo = combo, onClick = { previewCombo = combo })
                Spacer(Modifier.height(Ds.s3))
            }
            item { Spacer(Modifier.height(Ds.s4)) }
        }

        }
    }
    }

    previewCombo?.let { combo ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { previewCombo = null },
            sheetState = sheetState,
            containerColor = Ds.c.surface.copy(alpha = 0.88f),
            scrimColor = Ds.c.ink.copy(alpha = 0.14f),
        ) {
            ComboSheet(
                vm = vm,
                combo = combo,
                onUse = {
                    val id = vm.createWithCombo(combo)
                    previewCombo = null
                    onOpenProject(id)
                },
                onDismiss = { previewCombo = null },
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: PatternTemplate,
    preview: androidx.compose.ui.graphics.ImageBitmap?,
    tint: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    DsCard(modifier = modifier.fillMaxWidth(), padding = 0.dp, onClick = onClick) {
        // 一行一条：左侧画板，右侧信息铺满整行
        Row(
            Modifier.fillMaxWidth().padding(Ds.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PatternPlate(
                bitmap = preview,
                tint = tint,
                modifier = Modifier.size(92.dp),
                radius = Ds.rCard - Ds.s2,
                inset = Ds.s2,
                artPadding = Ds.s2,
            ) {
                PIconView(PIcon.Image, Ds.c.inkFaint, Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Ds.s3))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(template.name, style = Ds.h3, color = Ds.c.ink, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                MetaLabel(
                    "${template.width}×${template.height} · ${template.beadCount} 颗 · ${template.colorCount} 色",
                )
                Spacer(Modifier.height(2.dp))
                Text(template.note, style = Ds.label, color = Ds.c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PIconView(
                PIcon.ChevronRight,
                Ds.c.inkFaint,
                Modifier.size(16.dp),
            )
        }
    }
}

/** 模板搜索框。 */
@Composable
private fun TemplateSearchField(query: String, onQueryChange: (String) -> Unit) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(Ds.rControl))
            .background(Ds.c.surface)
            .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rControl))
            .padding(horizontal = Ds.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIconView(PIcon.Search, Ds.c.inkFaint, Modifier.size(18.dp))
        Spacer(Modifier.width(Ds.s2))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("搜索模板，比如「喵」「彩虹」「文字」", style = Ds.small, color = Ds.c.inkFaint)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = Ds.small.copy(color = Ds.c.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ds.c.clay),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(Ds.s2))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Ds.c.surfaceAlt)
                    .clickable {
                        onQueryChange("")
                        keyboard?.hide()
                    },
                contentAlignment = Alignment.Center,
            ) {
                PIconView(PIcon.Close, Ds.c.inkSoft, Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun ComboCard(combo: ColorCombo, onClick: () -> Unit) {
    DsCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row {
                combo.hexes.forEach { hex ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(ColorMath.parseHex(hex))),
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.width(Ds.s2))
            Column(Modifier.weight(1f)) {
                Text(combo.name, style = Ds.h3, color = Ds.c.ink, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(combo.note, style = Ds.small, color = Ds.c.inkSoft, maxLines = 2)
            }
            PIconView(PIcon.ChevronRight, Ds.c.inkFaint, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ComboSheet(
    vm: WorkshopViewModel,
    combo: ColorCombo,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val codes = remember(combo.id) { vm.comboColorCodes(combo) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ds.gutter)
            .padding(bottom = Ds.s8),
    ) {
        Text(combo.name, style = Ds.h2, color = Ds.c.ink)
        Spacer(Modifier.height(Ds.s1))
        Text(combo.note, style = Ds.small, color = Ds.c.inkSoft)
        Spacer(Modifier.height(Ds.s4))
        codes.forEach { (color, hex) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Ds.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(color.argb)),
                )
                Spacer(Modifier.width(Ds.s3))
                Column(Modifier.weight(1f)) {
                    Text(color.code, style = Ds.h3, color = Ds.c.ink)
                    MetaLabel(hex.uppercase())
                }
            }
        }
        Spacer(Modifier.height(Ds.s4))
        Text(
            "颜色会按 OKLab 就近匹配到当前色卡（${vm.workingPalette().name}），存成限定色卡后导入图片只会用这几种颜色。",
            style = Ds.small,
            color = Ds.c.inkSoft,
        )
        Spacer(Modifier.height(Ds.s6))
        PrimaryButton("用它新建作品", Modifier.fillMaxWidth(), onClick = onUse)
        Spacer(Modifier.height(Ds.s2))
        SecondaryButton(
            "只存为限定色卡",
            Modifier.fillMaxWidth(),
            onClick = {
                vm.saveComboAsPalette(combo)
                onDismiss()
            },
        )
    }
}
