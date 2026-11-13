package com.pindou.studio.ui.home

/**
 * 首屏轮播文案。
 *
 * 中文两行 + 一句衬线英文，围绕「创造力」说话：短、具体、不打鸡血。
 */
data class HeroCopy(val cn: String, val en: String) {
    companion object {
        val all: List<HeroCopy> = listOf(
            HeroCopy("把想法，\n拿在手上。", "Ideas you can hold."),
            HeroCopy("一颗豆，\n一笔画。", "Every bead, a brushstroke."),
            HeroCopy("小格子，\n大天地。", "Small grid, wide world."),
            HeroCopy("颜色，\n由你说了算。", "Color, on your terms."),
            HeroCopy("先看见，\n再拼出来。", "See it. Then make it."),
            HeroCopy("慢一点，\n拼得漂亮。", "Slow is how it gets good."),
            HeroCopy("从照片，\n到作品。", "From photo to finished piece."),
            HeroCopy("谁都能，\n当像素画家。", "Anyone can be a pixel artist."),
            HeroCopy("差一点，\n才像手作。", "A little off, by hand."),
            HeroCopy("每一颗，\n都算数。", "Every single bead counts."),
        )
    }
}
