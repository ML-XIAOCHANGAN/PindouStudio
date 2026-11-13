package com.pindou.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.pindou.studio.ui.components.Motion
import com.pindou.studio.ui.components.hapticClick
import com.pindou.studio.ui.components.rememberTapHaptics
import com.pindou.studio.ui.onboarding.tourAnchorKey
import com.pindou.studio.ui.components.beadGridTexture
import com.pindou.studio.ui.components.pressScale
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.delay
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.editor.EditorScreen
import com.pindou.studio.ui.home.HomeScreen
import com.pindou.studio.ui.inventory.InventoryScreen
import com.pindou.studio.ui.palette.PaletteManagerScreen
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.ui.theme.PindouTheme
import com.pindou.studio.ui.workshop.TemplateDetailScreen
import com.pindou.studio.ui.workshop.WorkshopScreen

/** 页面。 */
sealed interface Screen {
    data object Home : Screen
    data object Workshop : Screen
    data object Inventory : Screen
    data class Editor(
    val projectId: String,
    val autoOpenImport: Boolean = false,
    val autoOpenExport: Boolean = false,
    /** 新建 / 生成后直接进编辑态；从「我的作品」打开一律先只读预览 */
    val startEditing: Boolean = false,
) : Screen
    data class Workbench(val projectId: String) : Screen
    data object Palettes : Screen
    data object Settings : Screen

    /** 模板详情：默认预览模式，可切编辑模式 */
    data class Template(val templateId: String) : Screen

    /** MIX 叠加：把 A 拖到 B 上进入 */
    data class Mix(val idA: String, val idB: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val firstLaunch = savedInstanceState == null
        setContent {
            val settings = remember { PindouApp.from(this@MainActivity).settings }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (settings.darkMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            PindouTheme(themeId = settings.themeId, darkTheme = dark) {
                // 开屏动画浮在真实界面之上，结束后直接露出首页
                var showSplash by remember { mutableStateOf(firstLaunch) }
                var showGuide by remember { mutableStateOf(!settings.introShown) }
                val tourAnchors = remember { com.pindou.studio.ui.onboarding.TourAnchors() }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.pindou.studio.ui.onboarding.LocalTourAnchors provides tourAnchors,
                ) {
                    Box(Modifier.fillMaxSize().background(Ds.c.ground)) {
                        PindouRoot(
                            onReplayGuide = { showGuide = true },
                        )
                        if (showSplash) {
                            com.pindou.studio.ui.splash.SplashScreen(
                                onFinished = { showSplash = false },
                            )
                        }
                        // 新手引导：浮在真实首页之上，逐块讲解按钮和功能区
                        if (showGuide && !showSplash) {
                            com.pindou.studio.ui.onboarding.CoachTour(
                                steps = com.pindou.studio.ui.onboarding.HomeTourSteps,
                                onFinish = {
                                    settings.updateIntroShown(true)
                                    showGuide = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PindouRoot(onReplayGuide: () -> Unit = {}) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

    // 编辑器 / 工作台共用一份 EditorViewModel。这份 ViewModelStore 只属于「编辑流」：
    // 离开编辑器就整份清掉，否则每打开一个作品都会在 Activity 的 store 里留下一份
    // 网格（1024² = 2MB）+ 撤销历史 + 底图位图，浏览几个大工程就会 OOM。
    val editorStore = remember { ViewModelStore() }
    val editorOwner = remember(editorStore) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore get() = editorStore
        }
    }
    DisposableEffect(Unit) { onDispose { editorStore.clear() } }
    LaunchedEffect(screen) {
        if (screen !is Screen.Editor && screen !is Screen.Workbench) {
            // 等退场动画走完再清，避免退场过程中重新创建 ViewModel
            delay(400)
            editorStore.clear()
        }
    }

    // 任何界面切换都走同一套过渡：淡入 + 轻微位移，进入下层"往上浮"，返回上层"往下落"。
    // 以前只有编辑/工作台/色卡/设置有滑动，Tab 之间和模板页是硬切。
    fun depth(s: Screen): Int = when (s) {
        is Screen.Home, is Screen.Workshop, is Screen.Inventory -> 0
        is Screen.Editor, is Screen.Template, is Screen.Palettes, is Screen.Settings, is Screen.Mix -> 1
        is Screen.Workbench -> 2
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = depth(targetState) >= depth(initialState)
            val enter = fadeIn(tween(Motion.SCREEN_FADE_MS)) +
                slideInVertically(Motion.soft()) { if (forward) it / 16 else -it / 16 }
            val exit = fadeOut(tween(180)) +
                slideOutVertically(tween(180)) { if (forward) -it / 32 else it / 32 }
            enter togetherWith exit
        },
        label = "screenTransition",
    ) { target ->
        if (target.isTab()) {
            Scaffold(
                containerColor = Ds.c.ground,
                bottomBar = {
                    BottomBar(
                        current = target,
                        onSelect = { next -> screen = next },
                    )
                },
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .beadGridTexture(),
                ) {
                    when (target) {
                        is Screen.Workshop -> WorkshopScreen(
                            // 工坊里点开的都是刚「生成」的作品，直接进编辑态
                            onOpenProject = { id -> screen = Screen.Editor(id, startEditing = true) },
                            onOpenTemplate = { templateId -> screen = Screen.Template(templateId) },
                        )
                        is Screen.Inventory -> InventoryScreen()
                        else -> HomeScreen(
                            onOpenProject = { id, openImport, openExport ->
                                // 「图片转图纸」这类流程进去就要调参数，直接编辑；点列表里的作品先预览
                                screen = Screen.Editor(
                                    id,
                                    openImport,
                                    openExport,
                                    startEditing = openImport,
                                )
                            },
                            onOpenWorkshop = { screen = Screen.Workshop },
                            onOpenPalettes = { screen = Screen.Palettes },
                            onOpenSettings = { screen = Screen.Settings },
                            onOpenMix = { a, b -> screen = Screen.Mix(a, b) },
                        )
                    }
                }
            }
        } else {
            when (target) {
                is Screen.Editor -> CompositionLocalProvider(
                    LocalViewModelStoreOwner provides editorOwner,
                ) {
                    EditorScreen(
                        projectId = target.projectId,
                        autoOpenImport = target.autoOpenImport,
                        autoOpenExport = target.autoOpenExport,
                        startEditing = target.startEditing,
                        onBack = { screen = Screen.Home },
                        onOpenWorkbench = { screen = Screen.Workbench(target.projectId) },
                    )
                }
                is Screen.Workbench -> CompositionLocalProvider(
                    LocalViewModelStoreOwner provides editorOwner,
                ) {
                    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
                        as android.app.Application
                    val vm: com.pindou.studio.ui.editor.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                        key = target.projectId,
                        factory = com.pindou.studio.ui.editor.EditorViewModel.Factory(app, target.projectId),
                    )
                    com.pindou.studio.ui.workbench.WorkbenchScreen(
                        vm = vm,
                        onBack = { screen = Screen.Editor(target.projectId) },
                    )
                }
                is Screen.Template -> {
                    val template = com.pindou.studio.core.PatternTemplates.byId(target.templateId)
                    if (template == null) {
                        LaunchedEffect(target.templateId) { screen = Screen.Workshop }
                    } else {
                        val app = PindouApp.from(androidx.compose.ui.platform.LocalContext.current)
                        TemplateDetailScreen(
                            template = template,
                            onBack = { screen = Screen.Workshop },
                            onSaveAsProject = { grid ->
                                val palette = app.paletteRepository.byId(app.settings.lastPaletteId)
                                    ?: app.paletteRepository.default()
                                val id = app.projectRepository.createFromGrid(template.name, grid, palette)
                                // 刚保存的模板作品，直接进编辑态
                                screen = Screen.Editor(id, startEditing = true)
                            },
                        )
                    }
                }
                is Screen.Mix -> com.pindou.studio.ui.mix.MixScreen(
                    idA = target.idA,
                    idB = target.idB,
                    onBack = { screen = Screen.Home },
                    onCreated = { id -> screen = Screen.Editor(id, startEditing = true) },
                )
                is Screen.Palettes -> PaletteManagerScreen(onBack = { screen = Screen.Home })
                is Screen.Settings -> com.pindou.studio.ui.settings.SettingsScreen(
                    onBack = { screen = Screen.Home },
                    onOpenPalettes = { screen = Screen.Palettes },
                    // 引导的每一步都锚在首页控件上，所以先回首页再开始
                    onReplayGuide = {
                        screen = Screen.Home
                        onReplayGuide()
                    },
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

private fun Screen.isTab(): Boolean =
    this is Screen.Home || this is Screen.Workshop || this is Screen.Inventory

/**
 * 底部导航：悬浮的圆角托盘，托盘上贴着一小串彩色豆子贴纸。
 *
 * 贴纸会跟着当前页面在托盘顶边滑过去，起到「当前在这儿」的标记作用。
 */
@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val activeIndex = when {
        current is Screen.Workshop -> 1
        current is Screen.Inventory -> 2
        else -> 0
    }
    // 每个页面一套豆子配色（不 remember：主题切换时要跟着重新取色）
    val beadSets = listOf(
        listOf(Ds.c.clay, Ds.c.amber, Ds.c.rose, Ds.c.sky),
        listOf(Ds.c.amber, Ds.c.rose, Ds.c.sage, Ds.c.sky),
        listOf(Ds.c.sage, Ds.c.sky, Ds.c.rose, Ds.c.amber),
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(Ds.c.ground)
            .padding(start = Ds.s6, end = Ds.s6, top = Ds.s3, bottom = Ds.s2),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .tourAnchorKey("nav")
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Ds.c.surface)
                .border(Ds.stroke, Ds.c.hairline, RoundedCornerShape(Ds.rCard))
                .padding(horizontal = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(
                icon = PIcon.Grid,
                label = "作品",
                tint = Ds.c.clay,
                selected = activeIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Screen.Home) },
            )
            BottomItem(
                icon = PIcon.Star,
                label = "创意工坊",
                tint = Ds.c.amber,
                selected = activeIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Screen.Workshop) },
            )
            BottomItem(
                icon = PIcon.Box,
                label = "豆仓",
                tint = Ds.c.sage,
                selected = activeIndex == 2,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(Screen.Inventory) },
            )
        }

        // 贴在托盘顶边的小豆子贴纸：三个格子等宽，所以中心点落在 1/6、3/6、5/6
        val stickerHalf = 18.dp
        val targetX = maxWidth * (activeIndex * 2 + 1) / 6f - stickerHalf
        val stickerX by animateDpAsState(
            targetValue = targetX,
            animationSpec = Motion.springy(),
            label = "beadStickerX",
        )
        BeadSticker(
            colors = beadSets[activeIndex],
            modifier = Modifier
                .offset(x = stickerX, y = (-5).dp)
                .graphicsLayer { rotationZ = -5f },
        )

    }
}

/** 一小串彩色豆子贴纸（白边 = 模切贴纸的手感）。 */
@Composable
private fun BeadSticker(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .size(if (index % 2 == 0) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, Ds.c.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun BottomItem(
    icon: PIcon,
    label: String,
    tint: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        if (selected) tint else Ds.c.inkFaint,
        androidx.compose.animation.core.tween(240),
        label = "navColor",
    )
    val pillAlpha by animateFloatAsState(
        if (selected) 0.16f else 0f,
        Motion.springy(),
        label = "navPill",
    )
    val iconScale by animateFloatAsState(
        if (selected) 1.10f else 1f,
        Motion.bouncy(),
        label = "navScale",
    )
    val interaction = remember { MutableInteractionSource() }
    val haptics = com.pindou.studio.ui.components.rememberTapHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            // 左右各让出 2dp：相邻两项的热区之间保留 4dp 间隙，避免热区融合
            .padding(horizontal = 2.dp)
            .pressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(Ds.rPanel))
            .heightIn(min = Ds.hitTarget)
            .hapticClick(haptics, onClick = onClick),
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = pillAlpha))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            PIconView(icon, contentColor, Modifier.size(20.dp).graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            })
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = Ds.label, color = contentColor)
    }
}
