package com.pindou.studio.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pindou.studio.core.DisplayMode
import com.pindou.studio.core.SymmetryMode
import com.pindou.studio.core.Tool
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.blur
import com.pindou.studio.ui.components.GlassSurface
import com.pindou.studio.ui.components.hapticClick
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.Motion
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.util.FileUtils
import kotlinx.coroutines.launch

/** 编辑器主界面。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    autoOpenImport: Boolean,
    autoOpenExport: Boolean = false,
    /** 新建 / 从图片生成 / 模板保存后进入 → 直接编辑；从作品列表打开 → 先只读预览 */
    startEditing: Boolean = false,
    onBack: () -> Unit,
    onOpenWorkbench: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val vm: EditorViewModel = viewModel(
        key = projectId,
        factory = EditorViewModel.Factory(application, projectId),
    )
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(projectId) { vm.load() }

    // 只有「刚创建 / 刚生成」的入口才直接进编辑态；从列表打开一律先预览
    LaunchedEffect(startEditing) { if (startEditing) vm.enterEditing() }

    var fullscreen by remember { mutableStateOf(false) }
    // 有未保存改动时，返回要先问一声（和模板页同一套规则）
    var showExitPrompt by remember { mutableStateOf(false) }

    fun requestExit() {
        if (vm.dirty) showExitPrompt = true else onBack()
    }
    BackHandler(enabled = vm.dirty) { requestExit() }

    if (fullscreen) {
        com.pindou.studio.ui.components.FullScreenPatternViewer(
            grid = vm.project.grid,
            palette = vm.palette,
            showCodes = vm.showCodes,
            showGridLines = vm.showGrid,
            onExit = { fullscreen = false },
        )
        return
    }

    var activeSheet by remember { mutableStateOf(EditorSheet.None) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Bitmap?>(null) }

    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(autoOpenImport, autoOpenExport, vm.loaded) {
        if (vm.loaded && !autoOpened) {
            if (autoOpenImport) {
                autoOpened = true
                activeSheet = EditorSheet.Import
            } else if (autoOpenExport) {
                autoOpened = true
                activeSheet = EditorSheet.Export
            }
        }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bmp = FileUtils.decodeSampledBitmap(context, uri, 2048)
            if (bmp != null) {
                pendingImage = bmp
                activeSheet = EditorSheet.Import
            } else {
                vm.message = "图片读取失败"
            }
        }
    }

    val baseLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bmp = FileUtils.decodeSampledBitmap(context, uri, 1600)
            if (bmp != null) {
                vm.setBaseImage(bmp)
                vm.baseVisible = true
                vm.message = "底图已导入"
            } else {
                vm.message = "图片读取失败"
            }
        }
    }

    val sheetOpen = activeSheet != EditorSheet.None
    val backdropBlur by animateDpAsState(
        targetValue = if (sheetOpen) 16.dp else 0.dp,
        animationSpec = Motion.soft(),
        label = "backdropBlur",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Ds.c.ground,
    ) { padding ->
        if (!vm.loaded) {
            // 大工程的 JSON 解析 + 网格解压都在 IO 线程，这里给一个明确的等待态，
            // 避免先闪一下占位画布再跳到真实内容
            Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Ds.c.clay,
                    strokeWidth = 2.5.dp,
                )
            }
        } else {
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .blur(backdropBlur),
        ) {
            EditorTopBar(
                title = vm.project.name,
                subtitle = "${vm.project.width}×${vm.project.height} · ${vm.palette.name}",
                saving = vm.saving,
                canUndo = vm.canUndo,
                canRedo = vm.canRedo,
                preview = !vm.editing,
                onBack = { requestExit() },
                dirty = vm.dirty,
                onSave = { vm.persistNow() },
                onTitleClick = {
                    renameText = vm.project.name
                    showRename = true
                },
                onUndo = { vm.undo() },
                onRedo = { vm.redo() },
                onMore = { showMoreMenu = true },
                menu = {
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        // 常用五件事，其余收进「更多编辑操作」，菜单不再一长串
                        MenuItem("开始拼制（工作台）") { showMoreMenu = false; onOpenWorkbench() }
                        MenuItem("导出图纸") { showMoreMenu = false; activeSheet = EditorSheet.Export }
                        MenuItem("用量统计") { showMoreMenu = false; activeSheet = EditorSheet.Stats }
                        MenuItem("显示设置") { showMoreMenu = false; activeSheet = EditorSheet.Display }
                        MenuItem("全屏查看") { showMoreMenu = false; fullscreen = true }
                        if (vm.editing) {
                            MenuItem("更多编辑操作…") { showMoreMenu = false; activeSheet = EditorSheet.More }
                        }
                        MenuItem("放弃本次作品（移入回收站）") {
                            showMoreMenu = false
                            showDiscard = true
                        }
                    }
                },
            )

            if (vm.editing) {
                ToolBar(vm = vm)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                BeadCanvas(vm, editable = vm.editing, modifier = Modifier.fillMaxSize())
                if (vm.editing && vm.selection != null) {
                    SelectionBar(
                        vm = vm,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
                ZoomControls(
                    vm = vm,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
                if (vm.baseImage != null && vm.baseVisible) {
                    BaseImageBadge(
                        vm = vm,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
                }
            }

            if (vm.editing) {
                ColorBar(
                    vm = vm,
                    onOpenPalette = { activeSheet = EditorSheet.Palette },
                )
            } else {
                PreviewActions(
                    onEnterEdit = { vm.enterEditing() },
                    onFullscreen = { fullscreen = true },
                )
            }
        }
        }
    }

    if (showExitPrompt) {
        AlertDialog(
            onDismissRequest = { showExitPrompt = false },
            containerColor = Ds.c.surface,
            title = { Text("还有没保存的改动", style = Ds.h2, color = Ds.c.ink) },
            text = {
                Text(
                    "「${vm.project.name}」的改动还没保存。离开后这些改动就没了。",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitPrompt = false
                    vm.persistNow()
                    onBack()
                }) { Text("保存并退出", style = Ds.label, color = Ds.c.clay) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitPrompt = false
                        onBack()
                    }) { Text("放弃修改", style = Ds.label, color = Ds.c.rose) }
                    TextButton(onClick = { showExitPrompt = false }) {
                        Text("继续编辑", style = Ds.label, color = Ds.c.inkSoft)
                    }
                }
            },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor = Ds.c.surface,
            title = { Text("放弃这个作品？", style = Ds.h2, color = Ds.c.ink) },
            text = {
                Text(
                    "「${vm.project.name}」会移入回收站，之后可以在首页的「回收站」里找回来。" +
                        "如果只是刚才那一步画错了，用左上角的撤销更省事。",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    vm.discardProject()
                    onBack()
                }) { Text("移入回收站", style = Ds.label, color = Ds.c.rose) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text("继续编辑", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = Ds.c.surface,
            title = { Text("重命名", style = Ds.h2, color = Ds.c.ink) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("作品名称", style = Ds.label) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(renameText.trim())
                    showRename = false
                }) { Text("确定", style = Ds.label, color = Ds.c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("取消", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    if (activeSheet != EditorSheet.None) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activeSheet = EditorSheet.None; pendingImage = null },
            sheetState = sheetState,
            containerColor = Ds.c.surface.copy(alpha = 0.88f),
            scrimColor = Ds.c.ink.copy(alpha = 0.14f),
        ) {
            when (activeSheet) {
                EditorSheet.Palette -> PaletteSheetContent(vm = vm)
                EditorSheet.Stats -> StatsSheetContent(vm = vm)
                EditorSheet.Import -> ImportSheetContent(
                    vm = vm,
                    source = pendingImage,
                    onPickImage = { importLauncher.launch("image/*") },
                    onDone = { activeSheet = EditorSheet.None; pendingImage = null },
                )
                EditorSheet.Export -> ExportSheetContent(vm = vm, onDone = { activeSheet = EditorSheet.None })
                EditorSheet.CanvasSize -> CanvasSizeSheetContent(vm = vm, onDone = { activeSheet = EditorSheet.None })
                EditorSheet.Display -> DisplaySheetContent(vm = vm)
                EditorSheet.ImageOps -> ImageOpsSheetContent(vm = vm, onDone = { activeSheet = EditorSheet.None })
                EditorSheet.More -> MoreSheetContent(
                    vm = vm,
                    onPickImage = { importLauncher.launch("image/*") },
                    onPickBaseImage = { baseLauncher.launch("image/*") },
                    onOpen = { sheet -> activeSheet = sheet },
                )
                EditorSheet.None -> Unit
            }
        }
    }
}

/** 底部面板类型。 */
enum class EditorSheet {
    None, Palette, Stats, Import, Export, CanvasSize, Display, ImageOps, More,
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = Ds.body, color = Ds.c.ink) },
        onClick = onClick,
    )
}

@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String,
    saving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    /** true = 只读预览：隐藏撤销/重做，右侧显示「预览 · 只读」角标 */
    preview: Boolean,
    /** 有未保存改动（显示「未保存」并点亮保存按钮） */
    dirty: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMore: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Ds.c.ground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Ds.s1, end = Ds.s2, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Back, size = 40, onClick = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onTitleClick() }
                    .padding(horizontal = Ds.s2),
            ) {
                Text(
                    title,
                    style = Ds.h3,
                    color = Ds.c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        saving -> "保存中…"
                        dirty -> "未保存 · " + subtitle
                        else -> subtitle
                    },
                    style = Ds.label,
                    color = if (dirty && !saving) Ds.c.clay else Ds.c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview) {
                Text(
                    "预览 · 只读",
                    style = Ds.label,
                    color = Ds.c.inkSoft,
                    modifier = Modifier
                        .padding(end = Ds.s2)
                        .clip(CircleShape)
                        .background(Ds.c.surfaceAlt)
                        .padding(horizontal = Ds.s3, vertical = Ds.s1),
                )
            } else {
                // 「保存」是唯一的确认口；没改动时显示灰色的「已保存」
                Text(
                    if (saving) "保存中" else if (dirty) "保存" else "已保存",
                    style = Ds.label,
                    color = if (dirty && !saving) Ds.c.clay else Ds.c.inkFaint,
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (dirty && !saving) {
                                Modifier.background(Ds.c.claySoft).clickable { onSave() }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = Ds.s3, vertical = Ds.s1),
                )
                IconSquare(PIcon.Undo, enabled = canUndo, size = 34, onClick = onUndo)
                IconSquare(PIcon.Redo, enabled = canRedo, size = 34, onClick = onRedo)
            }
            Box {
                IconSquare(PIcon.More, size = 38, onClick = onMore)
                menu()
            }
        }
        Hairline()
    }
}

@Composable
private fun ToolBar(vm: EditorViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ds.c.ground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Ds.s1, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquare(PIcon.Pen, "画笔", vm.tool == Tool.PEN) { vm.selectTool(Tool.PEN) }
        IconSquare(PIcon.Eraser, "橡皮", vm.tool == Tool.ERASER) { vm.selectTool(Tool.ERASER) }
        IconSquare(PIcon.Bucket, "填充", vm.tool == Tool.FILL) { vm.selectTool(Tool.FILL) }
        IconSquare(PIcon.Dropper, "吸管", vm.tool == Tool.EYEDROPPER) { vm.selectTool(Tool.EYEDROPPER) }
        IconSquare(PIcon.Select, "选区", vm.tool == Tool.SELECT) { vm.selectTool(Tool.SELECT) }
        IconSquare(PIcon.Move, "移动", vm.tool == Tool.PAN) { vm.selectTool(Tool.PAN) }
        VerticalDivider()
        // 对称按钮每点一次换一种模式，同时用 snackbar 说明这次的含义
        // 网格 / 色号 / 光效 都在「显示设置」里，「用量统计」「色卡」在 ⋮ 菜单与底部色条上，
        // 工具栏只留真正边画边用的东西
        IconSquare(PIcon.Symmetry, vm.symmetry.label, vm.symmetry != SymmetryMode.NONE) {
            vm.cycleSymmetry()
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .padding(horizontal = Ds.s2)
            .width(Ds.stroke)
            .height(22.dp)
            .background(Ds.c.hairline),
    )
}

/**
 * 「更多编辑操作」面板：把不常用、但确实要用到的编辑动作收在一处，
 * 主菜单只留五件常做的事，工具栏只留边画边用的工具。
 */
@Composable
private fun MoreSheetContent(
    vm: EditorViewModel,
    onPickImage: () -> Unit,
    onPickBaseImage: () -> Unit,
    onOpen: (EditorSheet) -> Unit,
) {
    val haptics = com.pindou.studio.ui.components.rememberTapHaptics()
    Column(Modifier.fillMaxWidth().padding(horizontal = Ds.gutter).padding(bottom = Ds.s8)) {
        Text("更多编辑操作", style = Ds.h2, color = Ds.c.ink)
        Spacer(Modifier.height(Ds.s3))

        MoreRow(PIcon.Image, "以图生草图", "从相册选一张图，转成拼豆图纸", Ds.c.sky) { onPickImage() }
        MoreRow(
            PIcon.Upload,
            if (vm.baseImage == null) "导入底图" else "更换底图",
            "把参考照片垫在豆子下面，对着描",
            Ds.c.sage,
        ) { onPickBaseImage() }
        if (vm.baseImage != null) {
            MoreRow(PIcon.Trash, "移除底图", null, Ds.c.rose) { vm.setBaseImage(null) }
        }
        MoreRow(PIcon.Resize, "画布尺寸", "扩裁画布，内容可左上或居中", Ds.c.amber) {
            onOpen(EditorSheet.CanvasSize)
        }
        MoreRow(PIcon.FlipH, "图像操作", "翻转 / 旋转 / 扩裁整张图纸", Ds.c.clay) {
            onOpen(EditorSheet.ImageOps)
        }
        MoreRow(PIcon.Close, "清空画布", "可以撤销", Ds.c.rose) { vm.clearCanvas() }
        MoreRow(PIcon.Eye, "返回预览（只读）", "改完先看整体效果", Ds.c.inkSoft) { vm.exitEditing() }
    }
}

@Composable
private fun MoreRow(
    icon: PIcon,
    title: String,
    subtitle: String?,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val haptics = com.pindou.studio.ui.components.rememberTapHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ds.rControl))
            .hapticClick(haptics, onClick = onClick)
            .padding(vertical = Ds.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(Ds.rControl))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PIconView(icon, tint, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Ds.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ds.body, color = Ds.c.ink)
            if (subtitle != null) {
                Text(subtitle, style = Ds.label, color = Ds.c.inkFaint)
            }
        }
        PIconView(PIcon.ChevronRight, Ds.c.inkFaint, Modifier.size(14.dp))
    }
}

/** 预览态底部动作：进入编辑 / 全屏查看（和模板页保持同一套语言）。 */
@Composable
private fun PreviewActions(onEnterEdit: () -> Unit, onFullscreen: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ds.c.ground).padding(horizontal = Ds.gutter, vertical = Ds.s3)) {
        Hairline()
        Spacer(Modifier.height(Ds.s3))
        com.pindou.studio.ui.components.PrimaryButton(
            "进入编辑",
            Modifier.fillMaxWidth(),
            icon = PIcon.Pen,
        ) { onEnterEdit() }
        Spacer(Modifier.height(Ds.s2))
        com.pindou.studio.ui.components.SecondaryButton("全屏查看", Modifier.fillMaxWidth()) {
            onFullscreen()
        }
        Spacer(Modifier.height(Ds.s2))
        Text(
            "预览只能看不能改：单指拖动移动画面、双指缩放；想改点「进入编辑」。",
            style = Ds.label,
            color = Ds.c.inkFaint,
        )
    }
}

@Composable
private fun ColorBar(vm: EditorViewModel, onOpenPalette: () -> Unit) {
    val color = vm.palette.colorAt(vm.colorIndex)
    Column(Modifier.fillMaxWidth().background(Ds.c.ground)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpenPalette() }
                .padding(horizontal = Ds.gutter, vertical = Ds.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(color?.argb ?: 0xFF000000.toInt()), CircleShape)
                    .border(Ds.stroke, Ds.c.hairline, CircleShape),
            )
            Spacer(Modifier.width(Ds.s3))
            Column(Modifier.weight(1f)) {
                Text(
                    (color?.code ?: "-") + "   " + (color?.hex ?: ""),
                    style = Ds.h3,
                    color = Ds.c.ink,
                    maxLines = 1,
                )
                Text(
                    vm.palette.name,
                    style = Ds.label,
                    color = Ds.c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("调色板", style = Ds.label, color = Ds.c.amber)
        }
    }
}

@Composable
private fun SelectionBar(vm: EditorViewModel, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(Ds.rPanel),
        alpha = 0.86f,
    ) {
        Row(
            Modifier.padding(horizontal = Ds.s1, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Bucket, "填充", size = 40) { vm.fillSelection() }
            IconSquare(PIcon.Trash, "删除", size = 40) { vm.deleteSelection() }
            IconSquare(PIcon.Copy, "复制", size = 40) { vm.copySelection() }
            IconSquare(PIcon.Cut, "剪切", size = 40) { vm.cutSelection() }
            IconSquare(PIcon.Paste, "粘贴", size = 40) { vm.pasteSelection() }
            IconSquare(PIcon.Close, "取消", size = 40) { vm.clearSelection() }
        }
    }
}

@Composable
private fun ZoomControls(vm: EditorViewModel, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(Ds.rPanel),
        alpha = 0.86f,
    ) {
        Column {
            IconSquare(PIcon.ZoomIn, size = 36) { vm.zoomIn() }
            IconSquare(PIcon.ZoomOut, size = 36) { vm.zoomOut() }
            IconSquare(PIcon.Fit, size = 36) { vm.fitToScreen() }
        }
    }
}

@Composable
private fun BaseImageBadge(vm: EditorViewModel, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.clickable { vm.baseVisible = false },
        shape = RoundedCornerShape(Ds.rControl),
        alpha = 0.86f,
    ) {
        Text(
            "底图显示中 · 点击隐藏",
            style = Ds.label,
            color = Ds.c.inkSoft,
            modifier = Modifier.padding(horizontal = Ds.s3, vertical = Ds.s2),
        )
    }
}
