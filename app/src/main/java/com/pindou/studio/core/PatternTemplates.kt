package com.pindou.studio.core

/**
 * 创艺工坊的入门模板。
 *
 * 模板用字符行描述：'.' 表示留白，其余字符在 [colors] 里映射到具体颜色。
 * 颜色用十六进制而不是色号，落图时再按 OKLab 就近匹配当前色卡，
 * 这样任何一套色卡（MARD / Perler / DMC…）都能用同一份模板。
 */
class PatternTemplate(
    val id: String,
    val name: String,
    val note: String,
    private val rows: List<String>,
    val colors: Map<Char, String>,
    /** 分类：基础形状 / 植物 / 天气 / 图案 */
    val category: String = "基础形状",
) {
    /** 归一化后的行（右侧补 '.'，保证等宽）。 */
    val normalizedRows: List<String> by lazy {
        val width = rows.maxOfOrNull { it.length } ?: 0
        rows.map { it.padEnd(width, '.') }
    }

    val width: Int get() = normalizedRows.firstOrNull()?.length ?: 0
    val height: Int get() = normalizedRows.size

    /** 需要落豆的格子数。 */
    val beadCount: Int by lazy {
        normalizedRows.sumOf { row -> row.count { it != '.' } }
    }

    /** 色块种类数。 */
    val colorCount: Int get() = colors.size

    /** 把模板转成拼豆网格，颜色按就近匹配落到 [palette]。 */
    fun toGrid(palette: Palette): BeadGrid {
        val grid = BeadGrid(width.coerceAtLeast(1), height.coerceAtLeast(1))
        val cache = HashMap<Char, Int>()
        for ((r, row) in normalizedRows.withIndex()) {
            for ((c, ch) in row.withIndex()) {
                if (ch == '.') continue
                val index = cache.getOrPut(ch) {
                    val hex = colors[ch] ?: return@getOrPut 0
                    palette.nearestIndex(ColorMath.parseHex(hex))
                }
                if (index > 0) grid.set(c, r, index)
            }
        }
        return grid
    }
}

/** 在色卡里按 OKLab 就近取色号（1-based），空色卡返回 0。 */
fun Palette.nearestIndex(argb: Int): Int {
    if (colors.isEmpty()) return 0
    val lab = ColorMath.rgbToOklab(argb)
    var best = 1
    var bestD = Float.MAX_VALUE
    colors.forEachIndexed { i, color ->
        val d = ColorMath.oklabDistance(lab, color.lab)
        if (d < bestD) {
            bestD = d
            best = i + 1
        }
    }
    return best
}


/** 内置模板库：文字 / 月薪喵 / 小汤圆 / 植物 / 人物 / INS 六个分类。 */
object PatternTemplates {

    /** 按块大小交替的棋盘格。 */
    private fun checkerBlock(size: Int, block: Int, a: Char, b: Char): List<String> =
        (0 until size).map { r ->
            (0 until size).joinToString("") { c ->
                (if ((r / block + c / block) % 2 == 0) a else b).toString()
            }
        }

    /** 半圆弧彩虹：以底边中心为圆心的同心色带（由外到内：红、橙、黄、绿、蓝）。 */
    private fun rainbowArc(width: Int, height: Int): List<String> {
        val bands = listOf('D', 'C', 'B', 'A', '#')
        val edges = listOf(0.22f, 0.42f, 0.62f, 0.84f, 1.10f)
        val cx = (width - 1) / 2f
        val cy = height - 0.5f
        val halfW = width / 2f
        return (0 until height).map { r ->
            (0 until width).joinToString("") { c ->
                val dx = (c - cx) / halfW
                val dy = (r - cy) / height
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val band = edges.indexOfFirst { dist <= it }
                if (band >= 0 && dist > 0.10f) bands[band].toString() else "."
            }
        }
    }

    val all: List<PatternTemplate> = listOf(
        // ---------- 基础形状（新手第一次拼最常选的三个） ----------
        PatternTemplate(
            id = "shape-heart",
            name = "小爱心",
            note = "13×10 的红心，一块小板就能拼完，适合第一次上手",
            rows = listOf(
                "..XX....XX...",
                ".XooXXXXXXXX.",
                "XoooXXXXXXXXX",
                "XXXXXXXXXXXXX",
                ".XXXXXXXXXXX.",
                "..XXXXXXXXX..",
                "...XXXXXXX...",
                "....XXXXX....",
                ".....XXX.....",
                "......X......",
            ),
            colors = mapOf('X' to "#E0565B", 'o' to "#F2929B"),
        ),
        PatternTemplate(
            id = "shape-star",
            name = "小星星",
            note = "五角星，单色就能拼，也可以换成黄色做夜灯",
            rows = listOf(
                "......X......",
                ".....XXX.....",
                ".....XXX.....",
                "XXXXXXXXXXXXX",
                ".XXXXXXXXXXX.",
                "..XXXXXXXXX..",
                "...XXXXXXX...",
                "...XXXXXXX...",
                "..XXX...XXX..",
                ".XXX.....XXX.",
            ),
            colors = mapOf('X' to "#F2C43C"),
        ),
        PatternTemplate(
            id = "shape-smiley",
            name = "笑脸",
            note = "12×10 的小笑脸，做冰箱贴或者胸针都合适",
            rows = listOf(
                "...XXXXXX...",
                ".XXXXXXXXXX.",
                "XXXXXXXXXXXX",
                "XXmmXXXXmmXX",
                "XXmmXXXXmmXX",
                "XXXXXXXXXXXX",
                "XXmXXXXXXmXX",
                "XXXmmmmmmXXX",
                ".XXXXXXXXXX.",
                "...XXXXXX...",
            ),
            colors = mapOf('X' to "#F6C945", 'm' to "#3A3330"),
        ),

        // ---------- 文字 ----------
        PatternTemplate(
            id = "text-love",
            name = "LOVE 字母牌",
            note = "四个字母一排，挂墙或者做钥匙牌都合适",
            rows = listOf(
                "X.....AAA..V...V.EEEEE",
                "X....A...A.V...V.E....",
                "X....A...A.V...V.E....",
                "X....A...A.V...V.EEEE.",
                "X....A...A.V...V.E....",
                "X....A...A..V.V..E....",
                "XXXXX.AAA....V...EEEEE",
            ),
            colors = mapOf('X' to "#E0565B", 'A' to "#F0A93C", 'V' to "#4CAF7D", 'E' to "#3B93C4"),
            category = "文字",
        ),
        PatternTemplate(
            id = "text-hi",
            name = "HI 打招呼",
            note = "十来个格子的小尺寸，新手十分钟能拼完",
            rows = listOf(
                "H...H.III",
                "H...H..I.",
                "H...H..I.",
                "HHHHH..I.",
                "H...H..I.",
                "H...H..I.",
                "H...H.III",
            ),
            colors = mapOf('H' to "#E0565B", 'I' to "#3B93C4"),
            category = "文字",
        ),

        // ---------- 月薪喵（App 内「以图生图」直出的 64 宽图纸，见 CatTemplates.kt）----------
    ) + CatTemplates.all + listOf(

        // ---------- 小汤圆 ----------
        PatternTemplate(
            id = "tangyuan",
            name = "小汤圆",
            note = "圆滚滚一颗，元宵节的应景小物",
            rows = listOf(
                "...XXXXX...",
                ".XXXXXXXXX.",
                "XXXXXXXXXXX",
                "XXEEXXXEEXX",
                "XXXXXXXXXXX",
                "XXXCXXXCXXX",
                "XXXXXVXXXXX",
                "XXXXXXXXXXX",
                ".XXXXXXXXX.",
                "...XXXXX...",
            ),
            colors = mapOf('X' to "#FFF3E0", 'E' to "#4A4A4A", 'C' to "#F4A9A8", 'V' to "#E0565B"),
            category = "小汤圆",
        ),
        PatternTemplate(
            id = "tangyuan-sesame",
            name = "芝麻汤圆",
            note = "黑芝麻馅，永远的神",
            rows = listOf(
                "...XXXXX...",
                ".XXSXXXXSX.",
                "XXXSXXXXXXX",
                "XXEEXXXEEXX",
                "XXXXXXXSXXX",
                "XSXCXXXCXXX",
                "XXXXXVXXXXX",
                "XXXXSXXXXXX",
                ".XXXXXXXXX.",
                "...XSXXXX..",
            ),
            colors = mapOf('X' to "#FFF3E0", 'E' to "#4A4A4A", 'C' to "#F4A9A8", 'V' to "#E0565B", 'S' to "#3A3A3A"),
            category = "小汤圆",
        ),

        // ---------- 植物 ----------
        PatternTemplate(
            id = "cactus",
            name = "小仙人掌",
            note = "永远不用浇水的那一盆",
            rows = listOf(
                "....XXX....",
                "....XXX....",
                ".X..XXX..X.",
                "XXX.XXX.XXX",
                "XXX.XXX.XXX",
                ".X..XXX..X.",
                "....XXX....",
                "...XXXXX...",
                "..XXXXXXX..",
                ".PPPPPPPPP.",
                ".PPPPPPPPP.",
            ),
            colors = mapOf('X' to "#4CAF7D", 'P' to "#C87B4A"),
            category = "植物",
        ),
        PatternTemplate(
            id = "clover",
            name = "四叶草",
            note = "四片叶子，图个好运",
            rows = listOf(
                "..XXX.XXX..",
                ".XXXX.XXXX.",
                ".XXXXXXXXX.",
                ".XXX.X.XXX.",
                "..X..X..X..",
                "..X..X..X..",
                ".XXX.X.XXX.",
                ".XXXXXXXXX.",
                ".XXXX.XXXX.",
                "..XXX.XXX..",
            ),
            colors = mapOf('X' to "#4CAF7D"),
            category = "植物",
        ),

        // ---------- 人物 ----------
        PatternTemplate(
            id = "avatar",
            name = "小头像",
            note = "给朋友拼一个，当头像或者挂饰",
            rows = listOf(
                "...HHHHH...",
                "..HHHHHHH..",
                "..HSSSSSH..",
                "..HSESEHS..",
                "..HSSSSSH..",
                "..HSSVSSH..",
                "...HSSSH...",
                "..SSSSSSS..",
                ".SSSSSSSSS.",
                "SSSSSSSSSSS",
                "SSSSSSSSSSS",
            ),
            colors = mapOf('H' to "#5C4A3D", 'S' to "#F5D0B0", 'E' to "#3A3A3A", 'V' to "#E0565B"),
            category = "人物",
        ),
        PatternTemplate(
            id = "person",
            name = "小人剪影",
            note = "最简单的全身人形，留给自定义颜色发挥",
            rows = listOf(
                "...XXX...",
                "..XXXXX..",
                "..XXXXX..",
                "...XXX...",
                ".XXXXXXX.",
                "XXXXXXXXX",
                "XXXXXXXXX",
                "..XXXXX..",
                "..XX.XX..",
                "..XX.XX..",
                ".XXX.XXX.",
            ),
            colors = mapOf('X' to "#4A4A4A"),
            category = "人物",
        ),

        // ---------- INS ----------
        PatternTemplate(
            id = "ins-checker",
            name = "棋盘格",
            note = "最省事的背景板，两个颜色就够",
            rows = checkerBlock(12, 2, 'X', 'O'),
            colors = mapOf('X' to "#F4EDE4", 'O' to "#3A3A3A"),
            category = "INS",
        ),
        PatternTemplate(
            id = "ins-rainbow",
            name = "彩虹弧",
            note = "五色渐变，挂在窗边很上镜",
            rows = rainbowArc(15, 9),
            colors = mapOf(
                '#' to "#E0565B",
                'A' to "#F0A93C",
                'B' to "#F2CE4B",
                'C' to "#4CAF7D",
                'D' to "#3B93C4",
            ),
            category = "INS",
        ),
    )

    /** 模板分类，按模板出现顺序去重。 */
    fun categories(): List<String> = all.map { it.category }.distinct()

    fun byId(id: String): PatternTemplate? = all.firstOrNull { it.id == id }
}