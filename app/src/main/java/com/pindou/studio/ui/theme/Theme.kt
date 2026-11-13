package com.pindou.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计 token —— 拼豆色系。
 *
 * 结构上仍然克制（单一字族、固定间距、1px 描边、零阴影），
 * 但把颜色放开：五个取自真实拼豆的颜色作为功能色，并各自配一层浅底。
 * 文字始终用暖墨色，彩色只出现在按钮、图标、标记和浅色块里，保证可读性。
 */
@Immutable
data class DsPalette(
    /** 正文墨色（暖黑，不用纯黑） */
    val ink: Color,
    /** 次级文字 */
    val inkSoft: Color,
    /** 更弱的提示文字 */
    val inkFaint: Color,
    /** 1px 描边 */
    val hairline: Color,
    /** 页面底色 */
    val ground: Color,
    /** 卡片底色 */
    val surface: Color,
    /** 次级块底色 */
    val surfaceAlt: Color,

    // ---- 五个功能色 ----
    /** 陶土红：主按钮 / 创作 */
    val clay: Color,
    /** 琥珀：创艺工坊 */
    val amber: Color,
    /** 草绿：豆仓 */
    val sage: Color,
    /** 天蓝：工作台 / 画布数据 */
    val sky: Color,
    /** 玫瑰：提醒 / 缺色 */
    val rose: Color,

    // ---- 对应的浅色底 ----
    val claySoft: Color,
    val amberSoft: Color,
    val sageSoft: Color,
    val skySoft: Color,
    val roseSoft: Color,

    /** 彩色块上的文字 */
    val onAccent: Color,
    /** 画布纸底 */
    val canvasGround: Color,
) {
    /** 兼容旧代码：主强调色即陶土红。 */
    val accent: Color get() = clay
    val accentSoft: Color get() = claySoft
    /** 第 index 个功能色（0..4 循环），用于列表 / 分类着色。 */
    fun cyclic(index: Int): Color = when (((index % 5) + 5) % 5) {
        0 -> clay
        1 -> amber
        2 -> sage
        3 -> sky
        else -> rose
    }

    /** 与 [cyclic] 对应的浅底。 */
    fun cyclicSoft(index: Int): Color = when (((index % 5) + 5) % 5) {
        0 -> claySoft
        1 -> amberSoft
        2 -> sageSoft
        3 -> skySoft
        else -> roseSoft
    }
}

val LocalDsPalette = compositionLocalOf { PindouThemes.light("warm") }

/** 设计 token 入口。 */
object Ds {
    val c: DsPalette
        @Composable get() = LocalDsPalette.current

    // ---- 间距 ----
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s12 = 48.dp

    /** 页面左右边距（所有板块共用同一条线） */
    val gutter = 24.dp

    val sectionTop = 32.dp
    val sectionBottom = 24.dp

    // ---- 圆角 ----
    val rCard = 24.dp
    val rPanel = 16.dp
    val rControl = 10.dp

    val stroke = 1.dp

    // ---- 触控热区（移动端触控规范）----
    /** 视觉图标基准尺寸 */
    val iconVisual = 16.dp
    /** 触摸热区下限 */
    val hitMin = 28.dp
    /** 触摸热区推荐值 */
    val hitTarget = 40.dp
    /** 相邻热区之间必须保留的间隙 */
    val hitGap = 4.dp
    /** 滚动容器边缘的手势缓冲带 */
    val scrollBuffer = 20.dp

    // ---- 字阶（仅 400 / 500） ----
    private val family = FontFamily.Default

    val h1 = TextStyle(
        fontFamily = family, fontSize = 32.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.6).sp,
    )

    val h2 = TextStyle(
        fontFamily = family, fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp,
    )

    val h3 = TextStyle(
        fontFamily = family, fontSize = 17.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    )

    val body = TextStyle(
        fontFamily = family, fontSize = 16.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
    )

    val small = TextStyle(
        fontFamily = family, fontSize = 14.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    )

    val label = TextStyle(
        fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )

    val button = TextStyle(
        fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )

    val figure = TextStyle(
        fontFamily = family, fontSize = 22.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp,
    )

    // ---- 编辑式排版：双语小标 / 大标题 / 衬线斜体英文 ----

    /** 宽字距小标（全大写英文 + 中文），用来压住版面 */
    val kicker = TextStyle(
        fontFamily = family, fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 2.8.sp,
    )

    /** 首屏主标题：更大字号、更紧字距 */
    val displayCn = TextStyle(
        fontFamily = family, fontSize = 34.sp, lineHeight = 44.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-1).sp,
    )

    /** 衬线斜体英文副标，负责高级感 */
    val displayEn = TextStyle(
        fontFamily = FontFamily.Serif, fontSize = 14.sp, lineHeight = 22.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp,
    )
}

private fun materialScheme(p: DsPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.clay,
        onPrimary = p.onAccent,
        primaryContainer = p.claySoft,
        onPrimaryContainer = p.ink,
        secondary = p.sage,
        onSecondary = p.onAccent,
        secondaryContainer = p.sageSoft,
        onSecondaryContainer = p.ink,
        tertiary = p.sky,
        onTertiary = p.onAccent,
        background = p.ground,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.surfaceAlt,
        onSurfaceVariant = p.inkSoft,
        outline = p.inkFaint,
        outlineVariant = p.hairline,
        error = Color(0xFFC4553F),
    )
} else {
    lightColorScheme(
        primary = p.clay,
        onPrimary = p.onAccent,
        primaryContainer = p.claySoft,
        onPrimaryContainer = p.ink,
        secondary = p.sage,
        onSecondary = p.onAccent,
        secondaryContainer = p.sageSoft,
        onSecondaryContainer = p.ink,
        tertiary = p.sky,
        onTertiary = p.onAccent,
        background = p.ground,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.surfaceAlt,
        onSurfaceVariant = p.inkSoft,
        outline = p.inkFaint,
        outlineVariant = p.hairline,
        error = Color(0xFFC4553F),
    )
}

private val AppTypography = Typography(
    titleLarge = Ds.h2,
    titleMedium = Ds.h3,
    titleSmall = Ds.small,
    bodyLarge = Ds.body,
    bodyMedium = Ds.body,
    bodySmall = Ds.small,
    labelLarge = Ds.button,
    labelMedium = Ds.label,
    labelSmall = Ds.label,
)

@Composable
fun PindouTheme(
    themeId: String = "warm",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) PindouThemes.dark(themeId) else PindouThemes.light(themeId)
    CompositionLocalProvider(LocalDsPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette, darkTheme),
            typography = AppTypography,
            content = content,
        )
    }
}
