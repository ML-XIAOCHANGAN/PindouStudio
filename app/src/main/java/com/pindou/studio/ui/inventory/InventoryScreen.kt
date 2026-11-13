package com.pindou.studio.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pindou.studio.core.PaletteColor
import com.pindou.studio.ui.components.ChipButton
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.PrimaryButton
import com.pindou.studio.ui.components.SecondaryButton
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.theme.Ds

/** 豆仓：录入手头色号与库存。 */
@Composable
fun InventoryScreen() {
    val vm: InventoryViewModel = viewModel()
    var editing by remember { mutableStateOf<PaletteColor?>(null) }
    var editText by remember { mutableStateOf("") }
    var showClear by remember { mutableStateOf(false) }
    var palettePicker by remember { mutableStateOf(false) }

    val palette = vm.palette()
    val stockTick = vm.stockTick
    val query = vm.query
    val onlyOwned = vm.onlyOwned

    val rows = remember(stockTick, query, onlyOwned, palette.id) {
        palette.colors.filter { color ->
            (query.isBlank() || color.code.contains(query, ignoreCase = true)) &&
                (!onlyOwned || vm.countOf(color.code) > 0)
        }
    }
    val (kinds, beads) = remember(stockTick) { vm.summary() }

    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Ds.gutter, end = Ds.s2, top = Ds.s3, bottom = Ds.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("豆仓", style = Ds.h1, color = Ds.c.sage)
                Spacer(Modifier.height(Ds.s1))
                MetaLabel("$kinds 种色号 · 共 $beads 颗")
            }
            IconSquare(PIcon.Trash, size = 38) { showClear = true }
        }

        Column(Modifier.padding(horizontal = Ds.gutter)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Ds.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable { palettePicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(palette.name, style = Ds.body, color = Ds.c.ink, maxLines = 1)
                    Spacer(Modifier.width(Ds.s1))
                    PIconView(PIcon.ChevronRight, Ds.c.inkFaint, Modifier.size(14.dp))
                }
                ChipButton("只看已有", onlyOwned) { vm.onlyOwned = !onlyOwned }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query = it },
                singleLine = true,
                label = { Text("搜索色号", style = Ds.label) },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader("色号库存", "${rows.size} 个")
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Ds.gutter, end = Ds.gutter, bottom = Ds.s8),
        ) {
            items(rows, key = { it.code }) { color ->
                val count = vm.countOf(color.code)
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = color
                                editText = if (count > 0) count.toString() else ""
                            }
                            .padding(vertical = Ds.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(color.argb)),
                        )
                        Spacer(Modifier.width(Ds.s3))
                        Text(color.code, style = Ds.body, color = Ds.c.ink, modifier = Modifier.width(64.dp), maxLines = 1)
                        Text(
                            if (count > 0) "$count 颗" else "未录入",
                            style = Ds.label,
                            color = if (count > 0) Ds.c.sage else Ds.c.inkFaint,
                            modifier = Modifier.weight(1f),
                        )
                        StepButton("－") { vm.addCount(color.code, -10) }
                        Spacer(Modifier.width(Ds.s2))
                        StepButton("＋") { vm.addCount(color.code, 10) }
                    }
                    Hairline()
                }
            }
        }
    }

    if (palettePicker) {
        AlertDialog(
            onDismissRequest = { palettePicker = false },
            containerColor = Ds.c.surface,
            title = { Text("选择色卡", style = Ds.h2, color = Ds.c.ink) },
            text = {
                Column(Modifier.height(420.dp)) {
                    LazyColumn {
                        items(vm.palettes, key = { it.id }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.selectPalette(p.id)
                                        palettePicker = false
                                    }
                                    .padding(vertical = Ds.s3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    p.name,
                                    style = Ds.body,
                                    color = if (p.id == vm.paletteId) Ds.c.accent else Ds.c.ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                MetaLabel("${p.size} 色")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { palettePicker = false }) {
                    Text("关闭", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    editing?.let { color ->
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Ds.c.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(Color(color.argb)),
                    )
                    Spacer(Modifier.width(Ds.s2))
                    Text("${color.code} 库存", style = Ds.h2, color = Ds.c.ink)
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it.filter { ch -> ch.isDigit() }.take(6) },
                        singleLine = true,
                        label = { Text("手头有多少颗", style = Ds.label) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Ds.s3))
                    Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                        listOf(50, 100, 500).forEach { preset ->
                            ChipButton("+$preset", false) {
                                val base = editText.toIntOrNull() ?: 0
                                editText = (base + preset).toString()
                            }
                        }
                    }
                    Spacer(Modifier.height(Ds.s3))
                    Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                        ChipButton("清空", false) { editText = "0" }
                        ChipButton("取消", false) { editing = null }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setCount(color.code, editText.toIntOrNull() ?: 0)
                    editing = null
                }) { Text("保存", style = Ds.label, color = Ds.c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text("取消", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            containerColor = Ds.c.surface,
            title = { Text("清空豆仓", style = Ds.h2, color = Ds.c.ink) },
            text = { Text("所有色号的库存记录都会被删除。", style = Ds.small, color = Ds.c.inkSoft) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    showClear = false
                }) { Text("清空", style = Ds.label, color = Ds.c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) {
                    Text("取消", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Ds.c.surfaceAlt)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Ds.label, color = Ds.c.ink)
    }
}
