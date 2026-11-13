package com.pindou.studio.ui.palette

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pindou.studio.PindouApp
import com.pindou.studio.core.Palette
import com.pindou.studio.core.PaletteColor
import com.pindou.studio.ui.components.ColorSwatch
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.util.FileUtils
import java.io.File

/** 色卡管理：浏览内置 / 导入自定义色卡。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PindouApp.from(context).paletteRepository }
    var palettes by remember { mutableStateOf(repo.all()) }
    var viewing by remember { mutableStateOf<Palette?>(null) }
    var pendingDelete by remember { mutableStateOf<Palette?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showTextImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    fun refresh() {
        palettes = repo.all()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val file = FileUtils.copyUriToCache(context, uri, "palette_" + System.currentTimeMillis() + ".json")
            if (file != null) {
                val parsed = repo.parsePaletteText(file.readText(), file.nameWithoutExtension)
                if (parsed.isEmpty()) {
                    message = "色卡解析失败，请检查文件格式"
                } else {
                    parsed.forEach { repo.saveUser(it) }
                    refresh()
                    message = "已导入 ${parsed.size} 套色卡"
                }
            } else {
                message = "读取文件失败"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Ds.s1, end = Ds.s2, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Back, size = 40, onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = Ds.s2)) {
                Text("色卡", style = Ds.h3, color = Ds.c.ink)
                MetaLabel("${palettes.size} 套 · 内置 ${palettes.count { it.builtin }}")
            }
            IconSquare(PIcon.Upload, size = 38) { importLauncher.launch("*/*") }
            IconSquare(PIcon.Plus, size = 38) { showTextImport = true }
        }
        Hairline()
        LazyColumn(Modifier.fillMaxSize()) {
            val builtin = palettes.filter { it.builtin }
            val user = palettes.filterNot { it.builtin }
            if (user.isNotEmpty()) {
                item { SectionHeader("我的色卡", "${user.size} 套") }
                items(user, key = { it.id }) { palette ->
                    PaletteRow(
                        palette = palette,
                        onClick = { viewing = palette },
                        onDelete = { pendingDelete = palette },
                    )
                }
            }
            item { SectionHeader("内置色卡", "${builtin.size} 套") }
            items(builtin, key = { it.id }) { palette ->
                PaletteRow(palette = palette, onClick = { viewing = palette })
            }
            item { Spacer(Modifier.height(Ds.s8)) }
        }
    }

    viewing?.let { palette ->
        PaletteDetailDialog(palette = palette, onDismiss = { viewing = null })
    }

    pendingDelete?.let { palette ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除色卡") },
            text = { Text("确定删除「${palette.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteUser(palette.id)
                    refresh()
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    if (showTextImport) {
        AlertDialog(
            onDismissRequest = { showTextImport = false },
            title = { Text("从文本导入色卡") },
            text = {
                Column {
                    Text(
                        "支持 JSON（{\"A1\":{\"color1\":\"rgb(250,245,205)\"}}）与 CSV（num,type,color1,color2）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        minLines = 6,
                        maxLines = 10,
                        label = { Text("粘贴色卡内容") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = repo.parsePaletteText(importText, "自定义色卡")
                    if (parsed.isEmpty()) {
                        message = "解析失败"
                    } else {
                        parsed.forEach { repo.saveUser(it) }
                        refresh()
                        message = "已导入 ${parsed.size} 套色卡"
                    }
                    showTextImport = false
                    importText = ""
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showTextImport = false }) { Text("取消") } },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } },
        )
    }
}

@Composable
private fun PaletteRow(palette: Palette, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(start = Ds.gutter, end = Ds.gutter, top = Ds.s3, bottom = Ds.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(palette.name, style = Ds.body, color = Ds.c.ink, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                MetaLabel("${palette.size} 色" + if (palette.builtin) " · 内置" else " · 我的")
            }
            Row {
                palette.colors.take(5).forEach { c ->
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(c.argb)),
                    )
                }
            }
            if (onDelete != null) {
                Spacer(Modifier.width(Ds.s3))
                Text(
                    "删除",
                    style = Ds.label,
                    color = Ds.c.accent,
                    modifier = Modifier.clickable { onDelete() },
                )
            }
        }
        Box(Modifier.padding(horizontal = Ds.gutter)) { Hairline() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteDetailDialog(palette: Palette, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, palette) {
        if (query.isBlank()) palette.colors
        else palette.colors.filter { it.code.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(palette.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("搜索色号") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    modifier = Modifier.height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.code }) { color ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ColorSwatch(color.argb, "", selected = false, size = 38) {}
                            Text(
                                color.code,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                            if (color.isSpecial) {
                                Text(
                                    PaletteColor.typeLabel(color.type),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
