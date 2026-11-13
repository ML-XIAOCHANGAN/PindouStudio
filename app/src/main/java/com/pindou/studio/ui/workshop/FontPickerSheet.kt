package com.pindou.studio.ui.workshop

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pindou.studio.ui.components.ColoredChip
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 字体选择器。
 *
 * 字体一多，"一排 chip 横向滑动"就废了，所以这里按主流字体库的做法重做：
 * **搜索 + 分类 + 所见即所得预览 + 最近使用**，并且列表是懒加载的
 * （只有滚到屏幕里的那一行才解析字体，解析结果 LRU 只留 6 个）。
 */
@Composable
fun FontPickerSheet(
    fonts: List<FontCatalog.Entry>,
    selectedKey: String,
    sampleText: String,
    onPick: (FontCatalog.Entry) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val categories = remember(fonts) { FontCatalog.categories(fonts) }
    val recentKeys = remember { FontCatalog.recentKeys(context) }

    val filtered = remember(fonts, query, category) {
        FontCatalog.filter(fonts, query, category)
    }
    val recents = remember(fonts, recentKeys, query, category) {
        if (query.isNotBlank() || category != null) emptyList()
        else recentKeys.mapNotNull { k -> fonts.firstOrNull { it.key == k } }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
        // ---------- 标题 ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Ds.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("选择字体", style = Ds.h2, color = Ds.c.ink)
                MetaLabel("共 ${fonts.size} 款 · 只有中文款能打出中文")
            }
            com.pindou.studio.ui.components.TextLink("导入字体", color = Ds.c.clay) { onImport() }
        }
        Spacer(Modifier.height(Ds.s3))

        // ---------- 搜索 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Ds.gutter)
                .height(44.dp)
                .clip(RoundedCornerShape(Ds.rControl))
                .background(Ds.c.surface)
                .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rControl))
                .padding(horizontal = Ds.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PIconView(PIcon.Search, Ds.c.inkFaint, Modifier.size(16.dp))
            Spacer(Modifier.width(Ds.s2))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("搜字体名 / 分类，比如「手写」「像素」", style = Ds.small, color = Ds.c.inkFaint)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Ds.small.copy(color = Ds.c.ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Ds.c.clay),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Ds.c.surfaceAlt)
                        .clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { PIconView(PIcon.Close, Ds.c.inkSoft, Modifier.size(12.dp)) }
            }
        }
        Spacer(Modifier.height(Ds.s2))

        // ---------- 分类 ----------
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = Ds.gutter),
            horizontalArrangement = Arrangement.spacedBy(Ds.s2),
        ) {
            item {
                ColoredChip("全部", Ds.c.clay, category == null) { category = null }
            }
            items(categories) { name ->
                ColoredChip(name, Ds.c.cyclic(categories.indexOf(name)), category == name) {
                    category = if (category == name) null else name
                }
            }
        }

        // ---------- 最近使用 ----------
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(Ds.s3))
            MetaLabel("最近用过", Modifier.padding(horizontal = Ds.gutter))
            Spacer(Modifier.height(Ds.s1))
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = Ds.gutter),
                horizontalArrangement = Arrangement.spacedBy(Ds.s2),
            ) {
                items(recents, key = { "recent_" + it.key }) { entry ->
                    RecentFontChip(entry = entry, selected = entry.key == selectedKey) { onPick(entry) }
                }
            }
        }

        Spacer(Modifier.height(Ds.s3))

        // ---------- 列表（懒加载） ----------
        if (filtered.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(Ds.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("没有匹配的字体", style = Ds.h3, color = Ds.c.ink)
                Spacer(Modifier.height(Ds.s1))
                Text("换个关键词，或者点「全部」看看。", style = Ds.small, color = Ds.c.inkSoft)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(filtered, key = { it.key }) { entry ->
                    FontRow(
                        entry = entry,
                        selected = entry.key == selectedKey,
                        sample = sampleText,
                        onClick = { onPick(entry) },
                    )
                }
                item {
                    Text(
                        "字体越多越难挑，所以这里按分类分好、可以搜；「仅英文」的字体打不出中文，选它只适合做英文图纸。",
                        style = Ds.label,
                        color = Ds.c.inkFaint,
                        modifier = Modifier.padding(horizontal = Ds.gutter, vertical = Ds.s4),
                    )
                }
            }
        }

        // ---------- 底部 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Ds.gutter, vertical = Ds.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.pindou.studio.ui.components.SecondaryButton("导入手机里的字体", Modifier.weight(1f)) {
                onImport()
            }
            Spacer(Modifier.width(Ds.s3))
            com.pindou.studio.ui.components.SecondaryButton("关闭", Modifier.weight(1f)) { onDismiss() }
        }
    }
}

/** 一行字体：名字 + 规格 + 用字体本身渲染的预览。 */
@Composable
private fun FontRow(
    entry: FontCatalog.Entry,
    selected: Boolean,
    sample: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var typeface by remember(entry.key) { mutableStateOf<Typeface?>(null) }

    // 只有滚到可见位置才会走到这里：加载一个字体，LRU 只留 6 个
    LaunchedEffect(entry.key) {
        typeface = withContext(Dispatchers.IO) {
            runCatching { FontCatalog.loadCached(context, entry) }.getOrNull()
        }
    }

    val previewStyle = Ds.h2.copy(
        fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Default,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ds.gutter, vertical = Ds.s1)
            .clip(RoundedCornerShape(Ds.rControl))
            .background(if (selected) Ds.c.claySoft else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = Ds.s3, vertical = Ds.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.name,
                style = Ds.body,
                color = Ds.c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!entry.hasChinese) {
                Text(
                    "仅英文",
                    style = Ds.label,
                    color = Ds.c.rose,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Ds.c.roseSoft)
                        .padding(horizontal = Ds.s2, vertical = 1.dp),
                )
                Spacer(Modifier.width(Ds.s2))
            }
            Text(
                entry.category,
                style = Ds.label,
                color = Ds.c.inkFaint,
            )
            if (selected) {
                Spacer(Modifier.width(Ds.s2))
                PIconView(PIcon.Check, Ds.c.clay, Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (entry.hasChinese) {
                sample.ifBlank { "拼豆工坊" }.take(10)
            } else {
                "Bead Craft 123"
            },
            style = previewStyle,
            color = Ds.c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MetaLabel(entry.subtitle)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = Ds.gutter).height(1.dp).background(Ds.c.hairline))
}

@Composable
private fun RecentFontChip(entry: FontCatalog.Entry, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var typeface by remember(entry.key) { mutableStateOf<Typeface?>(null) }
    LaunchedEffect(entry.key) {
        typeface = withContext(Dispatchers.IO) {
            runCatching { FontCatalog.loadCached(context, entry) }.getOrNull()
        }
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Ds.c.claySoft else Ds.c.surface)
            .border(Ds.stroke, if (selected) Ds.c.clay else Ds.c.hairline, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = Ds.s3, vertical = Ds.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.name,
            style = Ds.label.copy(fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Default),
            color = if (selected) Ds.c.clay else Ds.c.ink,
            maxLines = 1,
        )
    }
}
