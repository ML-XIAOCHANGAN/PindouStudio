package com.pindou.studio.ui.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.pindou.studio.core.Palette
import com.pindou.studio.core.ProjectSummary
import com.pindou.studio.ui.components.ChipButton
import com.pindou.studio.ui.components.DsCard
import com.pindou.studio.ui.components.Motion
import com.pindou.studio.ui.components.hapticClick
import com.pindou.studio.ui.onboarding.tourAnchorKey
import com.pindou.studio.ui.components.PatternPlate
import com.pindou.studio.ui.components.enterStagger
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.PrimaryButton
import com.pindou.studio.ui.components.SecondaryButton
import com.pindou.studio.ui.components.TintedButton
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.util.FileUtils
import java.io.File

/** 首页：我的作品。 */
@Composable
fun HomeScreen(
    onOpenProject: (projectId: String, openImport: Boolean, openExport: Boolean) -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenPalettes: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 长按一张作品拖到另一张上 → 打开 MIX 叠加 */
    onOpenMix: (String, String) -> Unit = { _, _ -> },
) {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var menuTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var previewTarget by remember { mutableStateOf<ProjectSummary?>(null) }

    var heroIndex by remember { mutableIntStateOf(kotlin.random.Random.nextInt(HeroCopy.all.size)) }

    // ---- 长按拖拽叠加（MIX） ----
    val cardBounds = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }
    var dragMoved by remember { mutableStateOf(false) }
    // 长按开始拖拽后，松手时卡片自身的 click 也会触发，这里把它吞掉
    var suppressClickAt by remember { mutableStateOf(0L) }
    val dragHaptics = com.pindou.studio.ui.components.rememberTapHaptics()

    // 菜单里的"叠加到另一个作品"：选中源作品后，列表进入选择目标状态
    var mixSource by remember { mutableStateOf<ProjectSummary?>(null) }
    // 作品列表筛选：全部 / 收藏 / 未完成 / 已导出
    var listFilter by remember { mutableIntStateOf(0) }
    // 收藏 / 未完成 / 已导出 都是现成的数据推出来的，不需要额外的分类字段
    val shownProjects = remember(vm.projects, vm.favorites, vm.exportedIds, listFilter) {
        when (listFilter) {
            1 -> vm.projects.filter { it.id in vm.favorites }
            2 -> vm.projects.filter { it.doneBeads in 1 until it.beadCount }
            3 -> vm.projects.filter { it.id in vm.exportedIds }
            else -> vm.projects
        }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    // 每 10 秒随机换一条文案（不连续重复）
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            var next = kotlin.random.Random.nextInt(HeroCopy.all.size)
            if (HeroCopy.all.size > 1 && next == heroIndex) {
                next = (next + 1) % HeroCopy.all.size
            }
            heroIndex = next
        }
    }

    // 首页之前只往 vm.message 里写、却没有地方显示，删除/导出/收藏的提示全丢了
    val snackbar = remember { SnackbarHostState() }
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
            // 复制 + 解析 + 写盘都在 ViewModel 的 IO 线程里完成
            vm.importProjectFromUri(uri) { id ->
                if (id != null) onOpenProject(id, false, false)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        AppHeader(
            onImport = {
                // 反馈里提到「导入工程文件时找了半天它在哪」，开选择器前先提示一下位置
                vm.message = "工程文件一般在「文件管理 → 下载 → 拼豆工坊」，聊天软件收到的在 WeChat/Download"
                importLauncher.launch("*/*")
            },
            onPalettes = onOpenPalettes,
            onAbout = onOpenSettings,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Ds.gutter, end = Ds.gutter, bottom = Ds.s8),
            verticalArrangement = Arrangement.spacedBy(Ds.s2),
        ) {
            item {
                Column(Modifier.padding(top = Ds.s6)) {
                    // 宽字距双语小标
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PINDOU STUDIO", style = Ds.kicker, color = Ds.c.clay)
                        Spacer(Modifier.width(Ds.s2))
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(1.dp)
                                .background(Ds.c.hairline),
                        )
                        Spacer(Modifier.width(Ds.s2))
                        Text(
                            "拼豆工坊",
                            style = Ds.kicker,
                            color = Ds.c.inkFaint,
                            letterSpacing = 3.2.sp,
                        )
                    }
                    Spacer(Modifier.height(Ds.s4))

                    // 中文主标题 + 英文副标：每 10 秒换一条
                    AnimatedContent(
                        targetState = heroIndex,
                        transitionSpec = {
                            (fadeIn(tween(420)) + slideInVertically(Motion.soft()) { it / 3 })
                                .togetherWith(
                                    fadeOut(tween(240)) + slideOutVertically(tween(240)) { -it / 3 },
                                )
                        },
                        label = "heroCopy",
                    ) { index ->
                        val copy = HeroCopy.all[index]
                        Column {
                            Text(
                                styledHeadline(copy.cn, Ds.c.ink, Ds.c.clay),
                                style = Ds.displayCn,
                            )
                            Spacer(Modifier.height(Ds.s2))
                            Text(
                                copy.en,
                                style = Ds.displayEn,
                                color = Ds.c.inkSoft,
                            )
                        }
                    }

                    Spacer(Modifier.height(Ds.s4))
                    Text(
                        "一张照片，也能变成拼得出来的图纸。",
                        style = Ds.small,
                        color = Ds.c.inkSoft,
                    )
                    Spacer(Modifier.height(Ds.s6))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tourAnchorKey("start"),
                        horizontalArrangement = Arrangement.spacedBy(Ds.s3),
                    ) {
                        // 「图片转图纸」是绝大多数人真正的起点，所以放在主按钮位；
                        // 「新建作品」退到次按钮，但两个都保持一级可见
                        PrimaryButton(
                            text = "图片转图纸",
                            icon = PIcon.Image,
                            modifier = Modifier.weight(1f),
                        ) {
                            val id = vm.createProject(
                                "图片转图纸 " + (vm.projects.size + 1),
                                64, 64,
                                vm.defaultPaletteId(),
                            )
                            onOpenProject(id, true, false)
                        }
                        TintedButton(
                            text = "空白新建",
                            tint = Ds.c.sky,
                            icon = PIcon.Plus,
                            modifier = Modifier.weight(1f),
                        ) { showNewDialog = true }
                    }
                }
            }

            mixSource?.let { source ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Ds.rCard))
                            .background(Ds.c.claySoft)
                            .border(Ds.stroke, Ds.c.clay, RoundedCornerShape(Ds.rCard))
                            .padding(horizontal = Ds.s3, vertical = Ds.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("选择要叠加到的作品", style = Ds.body, color = Ds.c.ink)
                            MetaLabel("「${source.name}」在上层 / 下层的顺序进去再调")
                        }
                        com.pindou.studio.ui.components.TextLink("取消", color = Ds.c.rose) {
                            mixSource = null
                        }
                    }
                }
            }

            item {
                Column(Modifier.tourAnchorKey("works")) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Ds.sectionTop, bottom = Ds.s3),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text("我的作品", style = Ds.h2, color = Ds.c.ink)
                        Spacer(Modifier.width(Ds.s2))
                        Text(
                            if (vm.projects.isEmpty()) "" else "${shownProjects.size} 个",
                            style = Ds.label,
                            color = Ds.c.inkFaint,
                        )
                        Spacer(Modifier.weight(1f))
                        // 回收站常驻入口：以前只有回收站非空才显示，删完就找不着了
                        com.pindou.studio.ui.components.TextLink(
                            "回收站${if (vm.trashCount > 0) "（${vm.trashCount}）" else ""}",
                            color = if (vm.trashCount > 0) Ds.c.sage else Ds.c.inkFaint,
                        ) {
                            if (vm.trashCount > 0) {
                                vm.restoreTrash()
                            } else {
                                vm.message = "回收站是空的。删除的作品会先放进回收站，随时可以在这里恢复。"
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Ds.s2),
                        horizontalArrangement = Arrangement.spacedBy(Ds.s2),
                    ) {
                        listOf("全部", "收藏", "未完成", "已导出").forEachIndexed { i, label ->
                            com.pindou.studio.ui.components.ColoredChip(
                                text = label,
                                tint = Ds.c.cyclic(i),
                                selected = listFilter == i,
                            ) { listFilter = i }
                        }
                    }
                }
            }

            if (!vm.loaded) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Ds.s8),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Ds.c.clay,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            } else if (vm.projects.isEmpty()) {
                item {
                    // 空作品页：垫板插画 + 一句引导 + 两个居中的主按钮
                    Column(
                        Modifier.fillMaxWidth().padding(top = Ds.s2, bottom = Ds.s4),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(Ds.rCard))
                                .background(
                                    androidx.compose.ui.graphics.lerp(Ds.c.surface, Ds.c.amber, 0.10f),
                                )
                                .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rCard)),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.pindou.studio.ui.components.BeadHeart(Modifier.size(104.dp))
                            com.pindou.studio.ui.components.Sparkle(
                                com.pindou.studio.ui.components.BeadArt.RAINBOW[1],
                                Modifier.size(13.dp).align(Alignment.TopStart)
                                    .padding(start = 22.dp, top = 18.dp),
                            )
                            com.pindou.studio.ui.components.Sparkle(
                                com.pindou.studio.ui.components.BeadArt.RAINBOW[6],
                                Modifier.size(9.dp).align(Alignment.BottomEnd)
                                    .padding(end = 24.dp, bottom = 22.dp),
                            )
                        }
                        Spacer(Modifier.height(Ds.s4))
                        Text("还没有作品", style = Ds.h2, color = Ds.c.ink)
                        Spacer(Modifier.height(Ds.s2))
                        Text(
                            "从一张空白画布开始，或者挑个现成模板。",
                            style = Ds.small,
                            color = Ds.c.inkSoft,
                        )
                        Spacer(Modifier.height(Ds.s4))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Ds.s3),
                        ) {
                            PrimaryButton(
                                text = "新建作品",
                                icon = PIcon.Plus,
                                modifier = Modifier.weight(1f),
                            ) { showNewDialog = true }
                            TintedButton(
                                text = "去创意工坊",
                                tint = Ds.c.amber,
                                icon = PIcon.Star,
                                modifier = Modifier.weight(1f),
                            ) { onOpenWorkshop() }
                        }
                        Spacer(Modifier.height(Ds.s2))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.pindou.studio.ui.components.TextLink("导入工程（.pdp / .pd）") {
                                importLauncher.launch("*/*")
                            }
                            Spacer(Modifier.width(Ds.s6))
                            com.pindou.studio.ui.components.TextLink(
                                "回收站${if (vm.trashCount > 0) "（${vm.trashCount}）" else "（空）"}",
                                color = if (vm.trashCount > 0) Ds.c.sage else Ds.c.inkFaint,
                            ) {
                                if (vm.trashCount > 0) vm.restoreTrash()
                                else vm.message = "回收站是空的。删除的作品会先放进回收站，随时可以恢复。"
                            }
                        }
                        Spacer(Modifier.height(Ds.s2))
                        Text(
                            "找工程文件：手机「文件管理 → 下载 → 拼豆工坊」里就是我们导出的 .pdp / .pd；" +
                                "从聊天软件收到的文件一般在 WeChat/Download 目录。",
                            style = Ds.label,
                            color = Ds.c.inkFaint,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (shownProjects.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Ds.s6),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("这个筛选下没有作品", style = Ds.h3, color = Ds.c.ink)
                        Spacer(Modifier.height(Ds.s1))
                        Text(
                            when (listFilter) {
                                1 -> "点作品右边的星标就能把它收藏起来，收藏的作品会出现在这里。"
                                2 -> "画布上已经有豆子、但还没开始打点的作品会出现在这里。"
                                3 -> "导出过 PNG / PDF / SVG 图纸的作品会出现在这里。"
                                else -> "换个筛选看看。"
                            },
                            style = Ds.small,
                            color = Ds.c.inkSoft,
                        )
                    }
                }
            } else {
                itemsIndexed(shownProjects, key = { _, p -> p.id }) { index, project ->
                    ProjectRow(
                        project = project,
                        tint = Ds.c.cyclicSoft(index + 1),
                        exported = project.id in vm.exportedIds,
                        favorite = project.id in vm.favorites,
                        dropTarget = dropTargetId != null && dropTargetId == project.id,
                        dragging = dragId == project.id,
                        modifier = Modifier
                            .enterStagger(index)
                            .onGloballyPositioned { coords ->
                                cardBounds[project.id] = coords.boundsInRoot()
                            }
                            .pointerInput(project.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        suppressClickAt = System.currentTimeMillis()
                                        val origin = cardBounds[project.id]?.topLeft
                                            ?: androidx.compose.ui.geometry.Offset.Zero
                                        dragId = project.id
                                        dragPos = origin + offset
                                        dragMoved = false
                                        dropTargetId = null
                                        dragHaptics.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                        )
                                    },
                                    onDrag = { change, delta ->
                                        dragPos += delta
                                        dragMoved = true
                                        dropTargetId = cardBounds.entries
                                            .firstOrNull { it.key != project.id && it.value.contains(dragPos) }
                                            ?.key
                                    },
                                    onDragEnd = {
                                        val target = dropTargetId
                                        val from = dragId
                                        val moved = dragMoved
                                        dragId = null
                                        dropTargetId = null
                                        dragMoved = false
                                        when {
                                            from != null && target != null && target != from ->
                                                onOpenMix(from, target)
                                            // 只是长按没拖动：给一句用法提示，不弹窗
                                            !moved -> vm.message =
                                                "长按后直接拖动，把它拖到另一张作品上就能 MIX 叠加"
                                        }
                                    },
                                    onDragCancel = {
                                        dragId = null
                                        dropTargetId = null
                                        dragMoved = false
                                    },
                                )
                            },
                        onClick = {
                            // 刚结束一次长按拖拽：这次 click 不算数
                            if (System.currentTimeMillis() - suppressClickAt < 600) return@ProjectRow
                            val source = mixSource
                            if (source != null && source.id != project.id) {
                                mixSource = null
                                onOpenMix(source.id, project.id)
                            } else {
                                onOpenProject(project.id, false, false)
                            }
                        },
                        onMenu = { menuTarget = project },
                        // 长按已经让给 MIX 拖拽，不再弹大图（大图改到 ⋯ 菜单）
                        onLongPress = null,
                        onDuplicate = { vm.duplicate(project.id) },
                        onExport = { onOpenProject(project.id, false, true) },
                        onDelete = { deleteTarget = project },
                        onToggleFavorite = { vm.toggleFavorite(project.id) },
                    )
                }
            }
        }
    }
        // 拖拽浮层：跟着手指走，提示可以放到哪张上面
        dragId?.let { id ->
            val dragging = vm.projects.firstOrNull { it.id == id }
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (dragPos.x - 60.dp.toPx()).toInt(),
                            (dragPos.y - 40.dp.toPx()).toInt(),
                        )
                    }
                    .clip(RoundedCornerShape(Ds.rPanel))
                    .background(Ds.c.surface.copy(alpha = 0.96f))
                    .border(2.dp, Ds.c.clay, RoundedCornerShape(Ds.rPanel))
                    .padding(horizontal = Ds.s3, vertical = Ds.s2),
            ) {
                Column {
                    Text(
                        dragging?.name ?: "作品",
                        style = Ds.label,
                        color = Ds.c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (dropTargetId != null) "松手开始 MIX 叠加" else "拖到另一张作品上",
                        style = Ds.label,
                        color = if (dropTargetId != null) Ds.c.clay else Ds.c.inkFaint,
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    previewTarget?.let { target ->
        PreviewDialog(
            project = target,
            onOpen = { onOpenProject(target.id, false, false) },
            onDismiss = { previewTarget = null },
        )
    }

    if (showNewDialog) {
        NewProjectDialog(
            palettes = vm.palettes,
            defaultPaletteId = vm.defaultPaletteId(),
            onDismiss = { showNewDialog = false },
            onCreate = { name, w, h, paletteId ->
                val id = vm.createProject(name, w, h, paletteId)
                showNewDialog = false
                onOpenProject(id, false, false)
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Ds.c.surface,
            title = { Text("重命名作品", style = Ds.h2, color = Ds.c.ink) },
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
                    vm.rename(target.id, renameText.trim())
                    renameTarget = null
                }) { Text("确定", style = Ds.label, color = Ds.c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Ds.c.surface,
            title = { Text("删除作品", style = Ds.h2, color = Ds.c.ink) },
            text = {
                Text(
                    "「${target.name}」会移入回收站——首页「我的作品」右边的「回收站」就是入口，随时能恢复。",
                    style = Ds.small,
                    color = Ds.c.inkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target.id)
                    deleteTarget = null
                }) { Text("删除", style = Ds.label, color = Ds.c.accent) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }

    menuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor = Ds.c.surface,
            title = { Text(target.name, style = Ds.h2, color = Ds.c.ink) },
            text = {
                Column {
                    MenuRow("重命名") {
                        renameText = target.name
                        renameTarget = target
                        menuTarget = null
                    }
                    MenuRow(
                        if (target.id in vm.favorites) "取消收藏" else "收藏这个作品",
                    ) {
                        vm.toggleFavorite(target.id)
                        menuTarget = null
                    }
                    MenuRow("看大图") {
                        previewTarget = target
                        menuTarget = null
                    }
                    MenuRow("叠加到另一个作品（MIX）") {
                        mixSource = target
                        menuTarget = null
                    }
                    MenuRow("创建副本") {
                        vm.duplicate(target.id)
                        menuTarget = null
                    }
                    MenuRow("导出工程 .pdp") {
                        val dir = File(context.cacheDir, "export").apply { mkdirs() }
                        val file = File(dir, target.name + ".pdp")
                        menuTarget = null
                        vm.exportProject(target.id, file, false) { ok ->
                            if (ok) {
                                FileUtils.saveFileToDownloads(context, file, target.name + ".pdp", "application/json")
                                vm.message = "已导出到下载目录"
                            } else {
                                vm.message = "导出失败"
                            }
                        }
                    }
                    MenuRow("导出 pindou-draw .pd") {
                        val dir = File(context.cacheDir, "export").apply { mkdirs() }
                        val file = File(dir, target.name + ".pd")
                        menuTarget = null
                        vm.exportProject(target.id, file, true) { ok ->
                            if (ok) {
                                FileUtils.saveFileToDownloads(context, file, target.name + ".pd", "application/json")
                                vm.message = "已导出到下载目录"
                            } else {
                                vm.message = "导出失败"
                            }
                        }
                    }
                    MenuRow("删除", danger = true) {
                        deleteTarget = target
                        menuTarget = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuTarget = null }) {
                    Text("关闭", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }
}

/** 顶部：字标 + 两个行为。 */
@Composable
private fun AppHeader(onImport: () -> Unit, onPalettes: () -> Unit, onAbout: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ds.c.ground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Ds.gutter, end = Ds.s1, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(Ds.c.clay, Ds.c.amber, Ds.c.sage, Ds.c.sky).forEach { tint ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(tint),
                        )
                    }
                }
                Spacer(Modifier.width(Ds.s2))
                Text("拼豆工坊", style = Ds.h3, color = Ds.c.ink)
            }
            Row(
                Modifier.tourAnchorKey("topbar"),
                horizontalArrangement = Arrangement.spacedBy(Ds.hitGap),
            ) {
                BareIcon(PIcon.Upload, onClick = onImport)
                BareIcon(PIcon.Palette, onClick = onPalettes)
                BareIcon(PIcon.Settings, onClick = onAbout)
            }
        }
        Hairline()
    }
}

@Composable
private fun BareIcon(icon: PIcon, onClick: () -> Unit) {
    // 热区 40dp、视觉图标 20dp：透明扩容，热区之间留 4dp 间隙
    val haptics = com.pindou.studio.ui.components.rememberTapHaptics()
    Box(
        Modifier
            .size(Ds.hitTarget)
            .clip(CircleShape)
            .hapticClick(haptics) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        PIconView(icon, Ds.c.inkSoft, Modifier.size(20.dp))
    }
}

@Composable
private fun MenuRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = Ds.body,
        color = if (danger) Ds.c.accent else Ds.c.ink,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Ds.s3),
    )
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    tint: Color,
    exported: Boolean,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit = {},
) {
    DsCard(
        modifier = modifier.fillMaxWidth(),
        padding = 0.dp,
        onClick = onClick,
        onLongClick = onLongPress,
    ) {
        // 一行一条：左侧同心圆角画板，右侧信息向右铺满整行
        Row(
            Modifier.fillMaxWidth().padding(Ds.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val path = project.thumbnailPath
            val bmp = if (path != null) remember(path) {
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            } else null
            PatternPlate(
                bitmap = bmp?.asImageBitmap(),
                tint = tint,
                modifier = Modifier.size(92.dp),
                // 画板离卡边 8dp，外框圆角取 24 − 8 = 16，再内缩 8 得到白板圆角 8
                radius = Ds.rCard - Ds.s2,
                inset = Ds.s2,
                artPadding = Ds.s2,
            ) {
                PIconView(PIcon.Grid, Ds.c.inkFaint, Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Ds.s3))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = Ds.h3,
                    color = Ds.c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaLabel("${project.width}×${project.height} · ${project.beadCount} 颗")
                    Spacer(Modifier.weight(1f))
                    Text(
                        FileUtils.formatDate(project.updatedAt).substringBefore(' '),
                        style = Ds.label,
                        color = Ds.c.inkFaint,
                        maxLines = 1,
                    )
                }
                // 状态标签：收藏 / 没拼完 / 已导出过图纸
                val tags = buildList {
                    if (favorite) add("已收藏" to Ds.c.rose)
                    if (project.doneBeads in 1 until project.beadCount) add("未完成" to Ds.c.amber)
                    if (exported) add("已导出图纸" to Ds.c.sage)
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(Ds.s2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Ds.s1)) {
                        tags.forEach { (text, color) -> StatusTag(text, color) }
                    }
                }
            }
            // 星标：一键收藏（相当于轻量收藏夹），热区同样 40dp
            val starHaptics = com.pindou.studio.ui.components.rememberTapHaptics()
            Box(
                Modifier
                    .padding(start = Ds.s1)
                    .size(Ds.hitTarget)
                    .clip(CircleShape)
                    .hapticClick(starHaptics) { onToggleFavorite() },
                contentAlignment = Alignment.Center,
            ) {
                PIconView(
                    PIcon.Star,
                    if (favorite) Ds.c.rose else Ds.c.inkFaint,
                    Modifier.size(18.dp),
                )
            }
            // 菜单热区 40dp，并向内收，避免贴着卡片边缘误触
            val menuHaptics = com.pindou.studio.ui.components.rememberTapHaptics()
            Box(
                Modifier
                    .padding(start = Ds.s1, end = Ds.s2)
                    .size(Ds.hitTarget)
                    .clip(CircleShape)
                    .hapticClick(menuHaptics) { onMenu() },
                contentAlignment = Alignment.Center,
            ) {
                PIconView(PIcon.More, Ds.c.inkSoft, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    palettes: List<Palette>,
    defaultPaletteId: String,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("52") }
    var height by remember { mutableStateOf("52") }
    var paletteId by remember { mutableStateOf(defaultPaletteId) }
    var pickerOpen by remember { mutableStateOf(false) }
    val selected = palettes.firstOrNull { it.id == paletteId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ds.c.surface,
        title = { Text("新建作品", style = Ds.h2, color = Ds.c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("作品名称", style = Ds.label) },
                    placeholder = { Text("我的拼豆图纸", style = Ds.small) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Ds.s3))
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s3)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it.filter { ch -> ch.isDigit() }.take(4) },
                        singleLine = true,
                        label = { Text("宽（格）", style = Ds.label) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it.filter { ch -> ch.isDigit() }.take(4) },
                        singleLine = true,
                        label = { Text("高（格）", style = Ds.label) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Ds.s2))
                MetaLabel("常用底板尺寸（格）")
                Spacer(Modifier.height(Ds.s2))
                // 常见拼豆底板规格：29 小方板 / 52 大方板 / 78 / 104（两块 52 拼起来）
                Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                    listOf(29, 52, 78, 104).forEach { preset ->
                        ChipButton("${preset}", width == preset.toString() && height == preset.toString()) {
                            width = preset.toString()
                            height = preset.toString()
                        }
                    }
                }
                Spacer(Modifier.height(Ds.s1))
                Text(
                    "52 钉是常见的大方板；两张 52 拼起来是 78 或 104。尺寸也可以直接手填。",
                    style = Ds.label,
                    color = Ds.c.inkFaint,
                )
                Spacer(Modifier.height(Ds.s4))
                MetaLabel("色卡")
                Spacer(Modifier.height(Ds.s2))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Ds.rControl))
                        .clickable { pickerOpen = true }
                        .padding(vertical = Ds.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selected?.name ?: "选择色卡",
                        style = Ds.body,
                        color = Ds.c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    PIconView(PIcon.Back, Ds.c.inkFaint, Modifier.size(16.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = width.toIntOrNull()?.coerceIn(1, 1024) ?: 52
                val h = height.toIntOrNull()?.coerceIn(1, 1024) ?: 52
                onCreate(name.trim(), w, h, paletteId)
            }) { Text("创建", style = Ds.label, color = Ds.c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", style = Ds.label, color = Ds.c.inkSoft) }
        },
    )

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            containerColor = Ds.c.surface,
            title = { Text("选择色卡", style = Ds.h2, color = Ds.c.ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    palettes.forEach { palette ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    paletteId = palette.id
                                    pickerOpen = false
                                }
                                .padding(vertical = Ds.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                palette.name,
                                style = Ds.body,
                                color = if (palette.id == paletteId) Ds.c.accent else Ds.c.ink,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            MetaLabel("${palette.size} 色")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("关闭", style = Ds.label, color = Ds.c.inkSoft)
                }
            },
        )
    }
}

/** 作品行：可左滑露出快捷操作，长按可拖到另一张上做 MIX 叠加。 */
@Composable
private fun ProjectRow(
    project: ProjectSummary,
    tint: Color,
    exported: Boolean,
    favorite: Boolean,
    /** 拖拽悬停在这张卡片上（MIX 叠加的目标） */
    dropTarget: Boolean = false,
    /** 这张卡片正在被拖动 */
    dragging: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    /** 长按（现在只用于 MIX 拖拽，传 null 表示长按不说话） */
    onLongPress: (() -> Unit)? = null,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionsDp = 192.dp
    val limit = with(LocalDensity.current) { actionsDp.toPx() }
    val offsetX = remember { Animatable(0f) }

    fun close() {
        scope.launch { offsetX.animateTo(0f, Motion.springy()) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    dropTarget -> Modifier
                        .clip(RoundedCornerShape(Ds.rCard))
                        .border(2.dp, Ds.c.clay, RoundedCornerShape(Ds.rCard))
                    dragging -> Modifier
                        .clip(RoundedCornerShape(Ds.rCard))
                        .border(2.dp, Ds.c.inkFaint, RoundedCornerShape(Ds.rCard))
                    else -> Modifier
                },
            ),
    ) {
        Row(
            Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeAction("复制", PIcon.Copy, Ds.c.sky) {
                close(); onDuplicate()
            }
            SwipeAction("导出", PIcon.Download, Ds.c.amber) {
                close(); onExport()
            }
            SwipeAction("删除", PIcon.Trash, Ds.c.rose) {
                close(); onDelete()
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(limit) {
                    // 边缘 20dp 留作手势缓冲：贴着卡片左右边缘起手的滑动不当作左滑
                    val buffer = Ds.scrollBuffer.toPx()
                    var startAllowed = false
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            startAllowed =
                                offset.x > buffer && offset.x < size.width - buffer
                        },
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < -limit * 0.42f) -limit else 0f
                                offsetX.animateTo(target, Motion.springy())
                            }
                        },
                        onHorizontalDrag = { change, drag ->
                            if (startAllowed) {
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + drag).coerceIn(-limit, 0f))
                                }
                            }
                        },
                    )
                },
        ) {
            ProjectCard(
                project = project,
                tint = tint,
                exported = exported,
                favorite = favorite,
                onClick = {
                    if (offsetX.value != 0f) close() else onClick()
                },
                onMenu = onMenu,
                onLongPress = onLongPress,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun SwipeAction(label: String, icon: PIcon, color: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(64.dp)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            PIconView(icon, androidx.compose.ui.graphics.lerp(color, Ds.c.ink, 0.35f), Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Ds.label, color = Ds.c.inkSoft)
    }
}

/** 长按预览：垫板大图 + 打开。 */
@Composable
private fun PreviewDialog(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Ds.c.surface)
                .padding(Ds.s3),
        ) {
            val bmp = if (project.thumbnailPath != null) remember(project.thumbnailPath) {
                runCatching { BitmapFactory.decodeFile(project.thumbnailPath) }.getOrNull()
            } else null
            PatternPlate(
                bitmap = bmp?.asImageBitmap(),
                tint = Ds.c.cyclicSoft(1),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                radius = Ds.rPanel,
                inset = Ds.s2,
                artPadding = Ds.s3,
            ) {
                PIconView(PIcon.Grid, Ds.c.inkFaint, Modifier.size(32.dp))
            }
            Spacer(Modifier.height(Ds.s3))
            Text(project.name, style = Ds.h3, color = Ds.c.ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            MetaLabel("${project.width}×${project.height} · ${project.beadCount} 颗 · ${project.paletteName}")
            Spacer(Modifier.height(Ds.s3))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                com.pindou.studio.ui.components.SecondaryButton(
                    text = "关闭",
                    modifier = Modifier.weight(1f),
                ) { onDismiss() }
                com.pindou.studio.ui.components.PrimaryButton(
                    text = "打开",
                    modifier = Modifier.weight(1f),
                ) { onDismiss(); onOpen() }
            }
        }
    }
}

/** 作品卡上的小状态标签。 */
@Composable
private fun StatusTag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = Ds.kicker,
            color = androidx.compose.ui.graphics.lerp(color, Ds.c.ink, 0.42f),
            maxLines = 1,
        )
    }
}

/** 中文标题结尾的标点染成强调色，其余保持墨色。 */
private fun styledHeadline(text: String, ink: Color, accent: Color) = buildAnnotatedString {
    val last = text.lastOrNull()
    if (last != null && last in "。，！？、；：") {
        withStyle(SpanStyle(color = ink)) { append(text.dropLast(1)) }
        withStyle(SpanStyle(color = accent)) { append(last) }
    } else {
        withStyle(SpanStyle(color = ink)) { append(text) }
    }
}
