package com.pindou.studio.core

import kotlin.math.max

/**
 * 「MIX 叠加」的合成内核：把两张图纸按「下层 + 上层 + 蒙版 + 透明度」压成一张。
 *
 * 拼豆没有真正的 alpha 通道，所以**透明度用抖动**来表达：
 * 网格里按 4×4 有序抖动（Bayer）打点，50% 就隔一颗露一颗，
 * 远看就是"半透明"的效果，近看仍然是实心豆子——这也是绣图/像素画的常规做法。
 *
 * 合成规则（每个格子）：
 * 1. 上层这一格是空的（0）→ 露出下层；
 * 2. 上层有颜色，且 `mask * opacity > 抖动阈值` → 用上层颜色覆盖；
 * 3. 否则保留下层。
 */
object LayerMixer {

    /** 4×4 Bayer 有序抖动矩阵，归一化到 (0,1)。 */
    private val BAYER = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    private fun threshold(c: Int, r: Int): Float =
        (BAYER[(r and 3) * 4 + (c and 3)] + 0.5f) / 16f

    /** 合成结果 + 尺寸信息（上下层按中心对齐）。 */
    class Result(
        val grid: BeadGrid,
        /** 把上层网格坐标映射到输出坐标时的偏移 */
        val topOffsetX: Int,
        val topOffsetY: Int,
    )

    /**
     * @param base 下层网格
     * @param top 上层网格（必须已经换成下层同一套色卡里的索引）
     * @param mask 蒙版，长度 = 输出宽×高，1 = 完全露出上层，0 = 完全隐藏；null 表示全露出
     * @param opacity 上层整体透明度 0..1
     * @param dither 是否用抖动表达透明度（关掉时用 0.5 阈值硬切）
     * @param offsetX/offsetY 上层相对中心位置的微调（格）
     */
    fun compose(
        base: BeadGrid,
        top: BeadGrid,
        mask: FloatArray? = null,
        opacity: Float = 1f,
        dither: Boolean = true,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ): BeadGrid {
        val width = max(base.width, top.width)
        val height = max(base.height, top.height)
        val out = BeadGrid(width, height)

        val baseOx = (width - base.width) / 2
        val baseOy = (height - base.height) / 2
        val topOx = (width - top.width) / 2 + offsetX
        val topOy = (height - top.height) / 2 + offsetY

        val alpha = opacity.coerceIn(0f, 1f)
        if (mask != null) require(mask.size == width * height) {
            "mask size ${mask.size} != ${width * height}"
        }

        for (r in 0 until height) {
            for (c in 0 until width) {
                val baseIdx = base.get(c - baseOx, r - baseOy)
                val topIdx = top.get(c - topOx, r - topOy)
                val keep = if (topIdx == 0) {
                    false
                } else {
                    val m = mask?.get(r * width + c) ?: 1f
                    val reveal = m * alpha
                    if (dither) reveal > threshold(c, r) else reveal >= 0.5f
                }
                val value = if (keep) topIdx else baseIdx
                if (value != 0) out.set(c, r, value)
            }
        }
        return out
    }

    /** 新建一张"全露出"的蒙版。 */
    fun freshMask(width: Int, height: Int): FloatArray = FloatArray(width * height) { 1f }

    /** 蒙版反相（0 ↔ 1）。 */
    fun invertMask(mask: FloatArray) {
        for (i in mask.indices) mask[i] = 1f - mask[i]
    }

    /**
     * 合并两套色卡：A 原样打底（索引不变），B 里 A 没有的色号追加在后面。
     *
     * 叠加两张不同色卡的作品时必须先合并，否则把上层硬塞进下层色卡，
     * 上层独有的颜色会被"就近"映射成下层已有的颜色 —— 看起来就是颜色丢失。
     */
    fun mergePalettes(a: Palette, b: Palette): Palette {
        if (a.id == b.id) return a
        val colors = ArrayList<PaletteColor>(a.colors)
        val seen = a.colors.map { it.code.uppercase() }.toHashSet()
        for (c in b.colors) {
            if (seen.add(c.code.uppercase())) colors.add(c)
        }
        return Palette(
            id = "mix-" + a.id + "-" + b.id,
            name = a.name.take(8) + " + " + b.name.take(8),
            colors = colors,
            builtin = false,
        )
    }

    /**
     * 颜色索引重映射：把 [from] 色卡里的索引翻译成 [to] 色卡里最接近的索引。
     *
     * 两张图纸可能用的是不同品牌色卡，直接拿索引合成会串色，所以上层统一换到下层色卡。
     */
    fun remap(source: BeadGrid, from: Palette, to: Palette): BeadGrid {
        if (from === to) return source
        val out = BeadGrid(source.width, source.height)
        val cache = HashMap<Int, Int>()
        for (r in 0 until source.height) {
            for (c in 0 until source.width) {
                val idx = source.get(c, r)
                if (idx == 0) continue
                val mapped = cache.getOrPut(idx) {
                    val color = from.colorAt(idx) ?: return@getOrPut 0
                    val same = to.indexOf(color.code)
                    if (same > 0) same else to.nearestIndex(color.argb)
                }
                if (mapped > 0) out.set(c, r, mapped)
            }
        }
        return out
    }
}
