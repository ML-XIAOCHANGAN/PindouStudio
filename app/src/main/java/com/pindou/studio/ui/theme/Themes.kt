package com.pindou.studio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 一套配色：底色 + 墨色 + 五个功能色（柔色由功能色推出来）。 */
@Immutable
data class ColorScheme5(
    val ground: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val canvasGround: Color,
    val clay: Color,
    val amber: Color,
    val sage: Color,
    val sky: Color,
    val rose: Color,
)

/** 主题定义。 */
@Immutable
data class ThemeDef(
    val id: String,
    val name: String,
    val note: String,
    val light: ColorScheme5,
    val dark: ColorScheme5,
)

/** 把颜色和参考色按比例混合（t = 参考色占比）。 */
private fun blend(base: Color, ref: Color, t: Float): Color = Color(
    red = base.red + (ref.red - base.red) * t,
    green = base.green + (ref.green - base.green) * t,
    blue = base.blue + (ref.blue - base.blue) * t,
    alpha = 1f,
)

private fun lightPalette(s: ColorScheme5): DsPalette = DsPalette(
    ink = s.ink,
    inkSoft = s.inkSoft,
    inkFaint = s.inkFaint,
    hairline = s.ink.copy(alpha = 0.12f),
    ground = s.ground,
    surface = s.surface,
    surfaceAlt = s.surfaceAlt,
    clay = s.clay,
    amber = s.amber,
    sage = s.sage,
    sky = s.sky,
    rose = s.rose,
    claySoft = blend(s.surface, s.clay, 0.13f),
    amberSoft = blend(s.surface, s.amber, 0.15f),
    sageSoft = blend(s.surface, s.sage, 0.14f),
    skySoft = blend(s.surface, s.sky, 0.14f),
    roseSoft = blend(s.surface, s.rose, 0.14f),
    onAccent = Color(0xFFFFFCF8),
    canvasGround = s.canvasGround,
)

private fun darkPalette(s: ColorScheme5): DsPalette = DsPalette(
    ink = s.ink,
    inkSoft = s.inkSoft,
    inkFaint = s.inkFaint,
    hairline = s.ink.copy(alpha = 0.18f),
    ground = s.ground,
    surface = s.surface,
    surfaceAlt = s.surfaceAlt,
    clay = s.clay,
    amber = s.amber,
    sage = s.sage,
    sky = s.sky,
    rose = s.rose,
    claySoft = blend(s.ground, s.clay, 0.24f),
    amberSoft = blend(s.ground, s.amber, 0.24f),
    sageSoft = blend(s.ground, s.sage, 0.24f),
    skySoft = blend(s.ground, s.sky, 0.24f),
    roseSoft = blend(s.ground, s.rose, 0.24f),
    onAccent = Color(0xFF1A1310),
    canvasGround = s.canvasGround,
)

/** 五套可切换的配色。 */
object PindouThemes {

    val all: List<ThemeDef> = listOf(
        ThemeDef(
            id = "warm",
            name = "暖木",
            note = "奶油纸底 + 陶土红，最耐看",
            light = ColorScheme5(
                ground = Color(0xFFFBF7EF), surface = Color(0xFFFFFDFA), surfaceAlt = Color(0xFFF2EBDF),
                ink = Color(0xFF201B15), inkSoft = Color(0xFF7B7266), inkFaint = Color(0xFFABA294),
                canvasGround = Color(0xFFF5EFE4),
                clay = Color(0xFFA9552E), amber = Color(0xFFC98A1E), sage = Color(0xFF5E8B57),
                sky = Color(0xFF46749C), rose = Color(0xFFB85B72),
            ),
            // 暖木 · 夜：深咖啡底 + 陶土暖光，卡片比底色亮一档
            dark = ColorScheme5(
                ground = Color(0xFF161310), surface = Color(0xFF201B15), surfaceAlt = Color(0xFF2A241C),
                ink = Color(0xFFF2ECE2), inkSoft = Color(0xFFB5AA9B), inkFaint = Color(0xFF7E7568),
                canvasGround = Color(0xFF1B1712),
                clay = Color(0xFFD98F63), amber = Color(0xFFE0B45E), sage = Color(0xFF93BE85),
                sky = Color(0xFF7FA9CF), rose = Color(0xFFE091A3),
            ),
        ),
        ThemeDef(
            id = "morandi",
            name = "莫兰迪",
            note = "低饱和灰调，安静克制",
            light = ColorScheme5(
                ground = Color(0xFFF4F2EE), surface = Color(0xFFFCFBF8), surfaceAlt = Color(0xFFE9E6DF),
                ink = Color(0xFF2C2A27), inkSoft = Color(0xFF7C7871), inkFaint = Color(0xFFACA79E),
                canvasGround = Color(0xFFEDEAE3),
                clay = Color(0xFF8C7A6B), amber = Color(0xFFB79A5E), sage = Color(0xFF7E8F72),
                sky = Color(0xFF708596), rose = Color(0xFFAC8189),
            ),
            // 莫兰迪 · 夜：暖灰底 + 灰调五色，最安静的一套
            dark = ColorScheme5(
                ground = Color(0xFF171614), surface = Color(0xFF201F1C), surfaceAlt = Color(0xFF2A2926),
                ink = Color(0xFFEAE7E1), inkSoft = Color(0xFFADA9A1), inkFaint = Color(0xFF7A766F),
                canvasGround = Color(0xFF1B1A18),
                clay = Color(0xFFC3AC96), amber = Color(0xFFD3B77E), sage = Color(0xFFA0B391),
                sky = Color(0xFF9CB2C4), rose = Color(0xFFCFA5AC),
            ),
        ),
        ThemeDef(
            id = "sea",
            name = "海盐",
            note = "清透青绿，像海边的风",
            light = ColorScheme5(
                ground = Color(0xFFF1F8F6), surface = Color(0xFFFBFEFD), surfaceAlt = Color(0xFFE1EFEB),
                ink = Color(0xFF16302C), inkSoft = Color(0xFF5E7B76), inkFaint = Color(0xFF97AFAA),
                canvasGround = Color(0xFFE9F2EF),
                clay = Color(0xFF2F7F6F), amber = Color(0xFFC9A24B), sage = Color(0xFF3E8E6E),
                sky = Color(0xFF2E6E93), rose = Color(0xFFCE7466),
            ),
            // 海盐 · 夜：墨青底（不是纯黑）+ 薄荷主色
            dark = ColorScheme5(
                ground = Color(0xFF0F1618), surface = Color(0xFF172023), surfaceAlt = Color(0xFF1F2A2D),
                ink = Color(0xFFE2EEF0), inkSoft = Color(0xFF9FB3B8), inkFaint = Color(0xFF6E8288),
                canvasGround = Color(0xFF121A1D),
                clay = Color(0xFF6FC0B4), amber = Color(0xFFDCBB72), sage = Color(0xFF84C6A2),
                sky = Color(0xFF7FB6DC), rose = Color(0xFFE79E93),
            ),
        ),
        ThemeDef(
            id = "sakura",
            name = "樱花",
            note = "粉调温柔，适合小挂件",
            light = ColorScheme5(
                ground = Color(0xFFFDF6F4), surface = Color(0xFFFFFBFB), surfaceAlt = Color(0xFFF6E7E6),
                ink = Color(0xFF332024), inkSoft = Color(0xFF846C71), inkFaint = Color(0xFFB6A3A8),
                canvasGround = Color(0xFFF7EDEB),
                clay = Color(0xFFC4617C), amber = Color(0xFFD19A4E), sage = Color(0xFF7B9A72),
                sky = Color(0xFF6E8CA8), rose = Color(0xFFB65B6E),
            ),
            // 樱花 · 夜：深梅子底 + 樱粉主色
            dark = ColorScheme5(
                ground = Color(0xFF191214), surface = Color(0xFF221A1D), surfaceAlt = Color(0xFF2C2226),
                ink = Color(0xFFF4E8EA), inkSoft = Color(0xFFBCA3A9), inkFaint = Color(0xFF877077),
                canvasGround = Color(0xFF1D1518),
                clay = Color(0xFFE094A8), amber = Color(0xFFE2B273), sage = Color(0xFFA2BE97),
                sky = Color(0xFF9BB4CF), rose = Color(0xFFE88FA4),
            ),
        ),
        ThemeDef(
            id = "candy",
            name = "糖果",
            note = "明快高饱和，适合拍照分享",
            light = ColorScheme5(
                ground = Color(0xFFFFFBF2), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF7EFE0),
                ink = Color(0xFF241E1A), inkSoft = Color(0xFF7C6F60), inkFaint = Color(0xFFB3A797),
                canvasGround = Color(0xFFF8F1E3),
                clay = Color(0xFFE4572E), amber = Color(0xFFE0A020), sage = Color(0xFF2F9E63),
                sky = Color(0xFF2F7FC4), rose = Color(0xFFD4488A),
            ),
            // 糖果 · 夜：中性深底，把饱和度全留给强调色
            dark = ColorScheme5(
                ground = Color(0xFF141210), surface = Color(0xFF1E1A16), surfaceAlt = Color(0xFF28221C),
                ink = Color(0xFFF6EFE3), inkSoft = Color(0xFFB6A894), inkFaint = Color(0xFF857867),
                canvasGround = Color(0xFF18150F),
                clay = Color(0xFFF07A50), amber = Color(0xFFE9B44E), sage = Color(0xFF57BE80),
                sky = Color(0xFF6FA9DF), rose = Color(0xFFE8709F),
            ),
        ),
    )

    fun byId(id: String): ThemeDef = all.firstOrNull { it.id == id } ?: all.first()

    fun light(id: String): DsPalette = lightPalette(byId(id).light)

    fun dark(id: String): DsPalette = darkPalette(byId(id).dark)
}
