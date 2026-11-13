package com.pindou.studio.core

/** 绘制工具。 */
enum class Tool(val label: String) {
    PEN("画笔"),
    ERASER("橡皮"),
    FILL("油漆桶"),
    EYEDROPPER("吸管"),
    SELECT("选区"),
    PAN("移动画布"),
}

/**
 * 对称绘制模式。
 * [hint] 是给用户看的一句话解释——「左右 / 上下 / 四向 / 对角」光看名字并不直观。
 */
enum class SymmetryMode(val label: String, val hint: String) {
    NONE("无", "不做对称，落笔就是画哪儿是哪儿"),
    HORIZONTAL("左右", "落笔会同时画到左右镜像的位置"),
    VERTICAL("上下", "落笔会同时画到上下镜像的位置"),
    QUAD("四向", "一次落笔同时画到四个对称位置"),
    DIAGONAL("对角", "沿对角线镜像，适合斜向对称的图案"),
}

/**
 * 画布光效显示模式。
 *
 * 这几种模式只对**特殊色号**（光变 / 温变 / 夜光 / 珠光 / 半透明）起作用，
 * 普通色号前后一模一样——这也是很多人「看不出区别」的原因，所以每种模式配一句 [hint]。
 */
enum class DisplayMode(val key: String, val label: String, val hint: String) {
    STANDARD("standard", "标准", "正常显示，和实物豆子的颜色一致"),
    NIGHT("night", "夜间", "整体压暗到 50%，只有夜光色号保持原色，晚上拼不刺眼"),
    TEMPERATURE("temperatrue", "温变", "温变色号显示「变色后」的颜色，其余色号不变"),
    LIGHT("light", "光变", "光变色号显示「见光后」的颜色，其余色号不变"),
    SPECIAL("special", "特殊", "夜光 / 珠光 / 半透明色号都换成各自的展示色");

    companion object {
        fun of(key: String): DisplayMode = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/** 根据显示模式解析某个颜色最终应显示的颜色。 */
object DisplayResolver {

    fun resolve(color: PaletteColor, mode: DisplayMode): Int {
        val base = color.argb
        val activated = color.argb2
        var out = when (mode) {
            DisplayMode.STANDARD -> base
            DisplayMode.NIGHT -> if (color.type == PaletteColor.TYPE_GLOW) base else ColorMath.scale(base, 0.5f)
            DisplayMode.LIGHT -> if (color.type == PaletteColor.TYPE_LIGHT) activated else base
            DisplayMode.TEMPERATURE -> if (color.type == PaletteColor.TYPE_TEMPERATURE) activated else base
            DisplayMode.SPECIAL -> if (color.isDualState) activated else base
        }
        if (color.type == PaletteColor.TYPE_TRANSPARENT) {
            out = (out and 0x00FFFFFF) or (0x60 shl 24)
        } else if (color.type == PaletteColor.TYPE_PEARLESCENT && mode != DisplayMode.STANDARD) {
            out = ColorMath.mix(out, 0xFFFFFFFF.toInt(), 0.18f)
        }
        return out
    }
}

/** 选区（含端点，闭区间）。 */
data class SelectionRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1

    fun contains(c: Int, r: Int): Boolean = c in left..right && r in top..bottom

    fun normalized(): SelectionRect = SelectionRect(
        minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom),
    )

    companion object {
        fun fromPoints(c0: Int, r0: Int, c1: Int, r1: Int) =
            SelectionRect(minOf(c0, c1), minOf(r0, r1), maxOf(c0, c1), maxOf(r0, r1))
    }
}
