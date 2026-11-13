package com.pindou.studio.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pindou.studio.core.ShoppingList
import com.pindou.studio.ui.components.ColoredChip
import com.pindou.studio.ui.components.FigureBlock
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.components.TextLink
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.util.FileUtils
import java.io.File

/** 用量统计 + 缺色检查。 */
@Composable
fun StatsSheetContent(vm: EditorViewModel) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }

    val rows = remember(vm.renderTick) { vm.usageRows() }
    val total = remember(vm.renderTick) { rows.sumOf { it.count } }
    val bounds = remember(vm.renderTick) { vm.project.grid.bounds() }
    val paintedW = if (bounds != null) bounds[2] - bounds[0] + 1 else 0
    val paintedH = if (bounds != null) bounds[3] - bounds[1] + 1 else 0

    val needs = remember(vm.renderTick, vm.progressTick) { vm.shortageNeeds() }
    val missing = remember(needs) { ShoppingList.missing(needs) }
    val inventoryKinds = remember(vm.progressTick) { vm.inventorySummary().first }

    Column(Modifier.fillMaxWidth().padding(horizontal = Ds.gutter)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ds.s3)) {
            FigureBlock("${vm.project.width}×${vm.project.height}", "画布", Modifier.weight(1f), Ds.c.sky)
            FigureBlock("$paintedW×$paintedH", "图案", Modifier.weight(1f), Ds.c.sage)
            FigureBlock("$total", "豆子", Modifier.weight(1f), Ds.c.clay)
            FigureBlock("${rows.size}", "颜色", Modifier.weight(1f), Ds.c.amber)
        }

        Spacer(Modifier.height(Ds.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
            ColoredChip("色号用量", Ds.c.sky, tab == 0) { tab = 0 }
            ColoredChip(
                if (inventoryKinds == 0) "缺色检查" else "缺色 ${missing.size}",
                Ds.c.rose,
                tab == 1,
            ) { tab = 1 }
        }
        Spacer(Modifier.height(Ds.s2))

        if (tab == 0) {
            SectionHeader("色号用量")
            if (rows.isEmpty()) {
                Text(
                    "画布还没有内容",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                    modifier = Modifier.padding(vertical = Ds.s6),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(rows, key = { it.index }) { row ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Ds.s2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(Color.parseColorSafe(row.hex))),
                                )
                                Spacer(Modifier.width(Ds.s3))
                                Text(
                                    row.code,
                                    style = Ds.label,
                                    color = Ds.c.ink,
                                    modifier = Modifier.width(54.dp),
                                    maxLines = 1,
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Ds.c.hairline),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((row.count.toFloat() / total).coerceIn(0.02f, 1f))
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Ds.c.ink),
                                    )
                                }
                                Spacer(Modifier.width(Ds.s3))
                                Text(
                                    "${row.count}",
                                    style = Ds.label,
                                    color = Ds.c.inkSoft,
                                    modifier = Modifier.width(52.dp),
                                )
                            }
                            Hairline()
                        }
                    }
                }
            }
        } else {
            SectionHeader(
                "缺色检查",
                if (inventoryKinds == 0) null else if (missing.isEmpty()) "库存充足" else "缺 ${missing.size} 色",
            )
            if (inventoryKinds == 0) {
                Text(
                    "豆仓还没有录入。去底部「豆仓」填上你手头的色号和颗数，这里会自动比对并算出需要补多少。",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                )
                Spacer(Modifier.height(Ds.s4))
            } else if (missing.isEmpty()) {
                Text(
                    "这张图纸要用的 ${needs.size} 个色号都够，可以开拼了。",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                )
                Spacer(Modifier.height(Ds.s4))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(missing, key = { it.code }) { need ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = Ds.s2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(Color.parseColorSafe(need.hex))),
                                )
                                Spacer(Modifier.width(Ds.s3))
                                Text(need.code, style = Ds.label, color = Ds.c.ink, modifier = Modifier.width(54.dp))
                                MetaLabel("需要 ${need.required} · 手头 ${need.owned}", Modifier.weight(1f))
                                Text("缺 ${need.missing}", style = Ds.label, color = Ds.c.rose)
                            }
                            Hairline()
                        }
                    }
                }
                Spacer(Modifier.height(Ds.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s4)) {
                    TextLink("复制采购清单", color = Ds.c.accent) {
                        val text = ShoppingList.asText(needs, vm.project.name)
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("拼豆采购清单", text))
                        vm.message = "采购清单已复制"
                    }
                    TextLink("导出 CSV", color = Ds.c.ink) {
                        val dir = File(context.cacheDir, "export").apply { mkdirs() }
                        val file = File(dir, FileUtils.timestampName(vm.project.name + "_采购清单", "csv"))
                        file.writeText("\uFEFF" + ShoppingList.asCsv(needs))
                        if (FileUtils.saveFileToDownloads(context, file, file.name, "text/csv") != null) {
                            vm.message = "采购清单已保存到下载目录"
                        } else {
                            vm.message = "导出失败"
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Ds.s6))
    }
}

internal fun Color.Companion.parseColorSafe(hex: String): Int = runCatching {
    android.graphics.Color.parseColor(hex)
}.getOrDefault(0xFF000000.toInt())
