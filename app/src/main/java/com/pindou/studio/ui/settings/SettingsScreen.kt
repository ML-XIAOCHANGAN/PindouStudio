package com.pindou.studio.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pindou.studio.PindouApp
import com.pindou.studio.ui.components.ColoredChip
import com.pindou.studio.ui.components.Hairline
import com.pindou.studio.ui.components.IconSquare
import com.pindou.studio.ui.components.pressScale
import com.pindou.studio.ui.components.MetaLabel
import com.pindou.studio.ui.components.PIcon
import com.pindou.studio.ui.components.PIconView
import com.pindou.studio.ui.components.SectionHeader
import com.pindou.studio.ui.components.TextLink
import com.pindou.studio.ui.theme.Ds
import com.pindou.studio.ui.theme.PindouThemes

/** 设置：主题配色 / 外观 / 其他。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPalettes: () -> Unit,
    onReplayGuide: () -> Unit,
) {
    val context = LocalContext.current
    val app = PindouApp.from(context)
    val settings = app.settings
    val overlayDark = androidx.compose.foundation.isSystemInDarkTheme()

    Column(Modifier.fillMaxSize().background(Ds.c.ground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Ds.s1, end = Ds.s2, top = Ds.s1, bottom = Ds.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconSquare(PIcon.Back, size = 40, onClick = onBack)
            Text("设置", style = Ds.h3, color = Ds.c.ink, modifier = Modifier.padding(horizontal = Ds.s2))
        }
        Hairline()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Ds.gutter)) {
            SectionHeader("主题配色", if (PindouThemes.all.size > 0) "${PindouThemes.all.size} 套" else null)
            MetaLabel("选一套喜欢的，立即生效")
            Spacer(Modifier.height(Ds.s3))

            PindouThemes.all.forEach { theme ->
                val selected = settings.themeId == theme.id
                val scheme = if (overlayDark) theme.dark else theme.light
                val interaction = androidx.compose.runtime.remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(interaction, 0.975f)
                        .clip(RoundedCornerShape(Ds.rPanel))
                        .background(if (selected) Ds.c.claySoft else Ds.c.surface)
                        .border(
                            if (selected) 2.dp else Ds.stroke,
                            if (selected) Ds.c.clay else Ds.c.hairline,
                            RoundedCornerShape(Ds.rPanel),
                        )
                        .clickable(interactionSource = interaction, indication = null) {
                            settings.updateTheme(theme.id)
                        }
                        .padding(horizontal = Ds.s3, vertical = Ds.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 五个功能色 + 底色预览
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.ground)
                            .border(Ds.stroke, scheme.ink.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(scheme.clay, scheme.amber, scheme.sage).forEach { tint ->
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(tint),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(Ds.s3))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(theme.name, style = Ds.h3, color = Ds.c.ink)
                            if (selected) {
                                Spacer(Modifier.width(Ds.s2))
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Ds.c.clay),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PIconView(PIcon.Check, Ds.c.onAccent, Modifier.size(10.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(theme.note, style = Ds.label, color = Ds.c.inkSoft)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            scheme.clay, scheme.amber, scheme.sage, scheme.sky, scheme.rose,
                        ).forEach { tint ->
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(tint),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Ds.s2))
            }

            SectionHeader("外观")
            Row(horizontalArrangement = Arrangement.spacedBy(Ds.s2)) {
                listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                    ColoredChip(label, Ds.c.sky, settings.darkMode == index) {
                        settings.updateDarkMode(index)
                    }
                }
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: ""
            }

            SectionHeader("其他")
            SettingRow("色卡管理", "内置 17 套 / 支持导入", PIcon.Palette) { onOpenPalettes() }
            SettingRow("重新观看新手指引", "在首页逐步讲解每个功能区", PIcon.Star) { onReplayGuide() }

            Spacer(Modifier.height(Ds.s6))
            // 品牌标志 + 版本
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        id = com.pindou.studio.R.drawable.brand_logo,
                    ),
                    contentDescription = "拼豆工坊",
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(Ds.rPanel)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Spacer(Modifier.width(Ds.s3))
                Column {
                    Text("拼豆工坊", style = Ds.h3, color = Ds.c.ink)
                    Spacer(Modifier.height(2.dp))
                    MetaLabel("版本 $versionName")
                    Spacer(Modifier.height(Ds.s2))
                    Text(
                        "离线优先的拼豆图纸编辑器：画图纸、以图生图、边拼边打点、豆仓备料。",
                        style = Ds.label,
                        color = Ds.c.inkFaint,
                    )
                }
            }
            Spacer(Modifier.height(Ds.s12))
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: PIcon, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = Ds.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PIconView(icon, Ds.c.inkSoft, Modifier.size(20.dp))
            Spacer(Modifier.width(Ds.s3))
            Column(Modifier.weight(1f)) {
                Text(title, style = Ds.body, color = Ds.c.ink)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Ds.label, color = Ds.c.inkSoft)
            }
            PIconView(PIcon.ChevronRight, Ds.c.inkFaint, Modifier.size(16.dp))
        }
        Hairline()
    }
}
