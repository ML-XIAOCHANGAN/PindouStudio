package com.pindou.studio.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 文字对齐方式。 */
enum class TextAlign { LEFT, CENTER, RIGHT }

/**
 * 文字转图纸的参数。
 *
 * 字高、字距、行距统一用「格」表达，因为用户真正关心的是这个字占几格豆子。
 */
data class TextSpec(
    val text: String,
    val typeface: Typeface?,
    /** 单字高度（格），汉字 em 高度等于该值 */
    val cellHeight: Int = 14,
    /** 字间距（格） */
    val letterSpacing: Int = 1,
    /** 行间距（格） */
    val lineSpacing: Int = 2,
    /**
     * 单行最大宽度（格）。
     *
     * **默认 0 = 不自动换行**：一行要多长就多长，换行只认用户在输入框里敲的回车。
     * 想恢复自动折行时传一个正数即可。
     */
    val maxCols: Int = 0,
    /** 覆盖率阈值，0.3~0.7 */
    val threshold: Float = 0.45f,
    /** 描边外扩格数，0 表示不描边 */
    val outlineWidth: Int = 1,
    /** 断笔修复 */
    val repair: Boolean = true,
    val align: TextAlign = TextAlign.CENTER,
    /** 加粗（对单字重字体走伪粗体，同样生效） */
    val bold: Boolean = false,
    /** 倾斜 */
    val italic: Boolean = false,
    /** 字宽横向缩放，0.7~1.3，1 为原始宽度 */
    val widthScale: Float = 1f,
    /** 超采样倍率，2/4/6，越大细节越多、计算越慢 */
    val supersample: Int = 4,
)

/** 栅格化结果：主体掩膜 + 描边掩膜。 */
class TextMasks(
    val width: Int,
    val height: Int,
    private val body: BooleanArray,
    private val outline: BooleanArray,
    /** 断笔修复补了多少格，用于界面提示 */
    val repairedCells: Int,
) {
    fun isBody(c: Int, r: Int): Boolean = body[r * width + c]
    fun isOutline(c: Int, r: Int): Boolean = outline[r * width + c]
    fun eachCell(action: (c: Int, r: Int, isBody: Boolean) -> Unit) {
        for (r in 0 until height) for (c in 0 until width) {
            val b = body[r * width + c]
            if (b || outline[r * width + c]) action(c, r, b)
        }
    }
}

/**
 * 文字 → 拼豆网格。
 *
 * 流程：排版（按格换算字号、逐字测宽、自动换行）→ 4 倍超采样栅格化 →
 * 覆盖率场 → 阈值判定 → 断笔修复 → 八邻域外扩生成描边。
 */
object TextBeadEngine {

    /** 行首禁区：这些标点不允许出现在行首（简单避头尾）。 */
    private const val NO_LINE_START = "，。、；：？！）】》」』’”%.!?,;:)"

    fun layout(spec: TextSpec): TextMasks? {
        val text = spec.text.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return null

        val ss = spec.supersample.coerceIn(2, 8)
        val cellPx = ss.toFloat()
        val emPx = spec.cellHeight * cellPx
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = spec.typeface ?: Typeface.DEFAULT
            textSize = emPx
            color = android.graphics.Color.WHITE
            isSubpixelText = false
            isDither = false
            isFakeBoldText = spec.bold
            textSkewX = if (spec.italic) -0.22f else 0f
        }
        val widthScale = spec.widthScale.coerceIn(0.6f, 1.6f)
        val fm = paint.fontMetrics
        val linePx = (fm.descent - fm.ascent) + spec.lineSpacing * cellPx
        val spacingPx = spec.letterSpacing * cellPx
        // 0 = 不折行；只有显式给了上限才自动换行
        val maxLinePx = if (spec.maxCols > 0) spec.maxCols * cellPx else Float.MAX_VALUE

        // ---- 逐字测宽 + 换行 ----
        val lines = ArrayList<ArrayList<String>>()
        var current = ArrayList<String>()
        var currentWidth = 0f
        fun flush() {
            if (current.isNotEmpty()) {
                lines.add(current)
                current = ArrayList()
                currentWidth = 0f
            }
        }
        fun advanceOf(s: String): Float = paint.measureText(s) * widthScale + spacingPx

        val manualLines = text.split('\n')
        for ((li, raw) in manualLines.withIndex()) {
            if (raw.isEmpty()) {
                lines.add(ArrayList())
                continue
            }
            for (ch in raw) {
                val s = ch.toString()
                val w = advanceOf(s)
                if (maxLinePx != Float.MAX_VALUE &&
                    currentWidth + w > maxLinePx && current.isNotEmpty() && ch !in NO_LINE_START
                ) {
                    flush()
                }
                // 避头尾只在自动折行时才有意义；不折行时不做"把上一个字带下来"的处理
                if (maxLinePx != Float.MAX_VALUE &&
                    current.isEmpty() && ch in NO_LINE_START && lines.isNotEmpty()
                ) {
                    val prev = lines.last()
                    if (prev.size > 1) {
                        val moved = prev.removeAt(prev.size - 1)
                        current.add(moved)
                        currentWidth = advanceOf(moved)
                    }
                }
                current.add(s)
                currentWidth += w
            }
            flush()
        }
        if (lines.isEmpty()) return null

        // ---- 画布尺寸 ----
        val lineWidths = lines.map { line -> line.sumOf { advanceOf(it).toDouble() }.toFloat() - spacingPx }
            .map { it.coerceAtLeast(0f) }
        val contentW = lineWidths.maxOrNull() ?: 0f
        val padCells = spec.outlineWidth + 1
        val padPx = padCells * cellPx
        val cols = max(1, ceil((contentW + padPx * 2) / cellPx).toInt())
        val rows = max(1, ceil((lines.size * linePx + padPx * 2) / cellPx).toInt())

        // 不折行之后，一行可能很长：超出单张画布上限就直接放弃，
        // 由界面提示「调小字高 / 多敲几个回车」，避免 OOM
        if (cols > BeadGrid.MAX_SIZE || rows > BeadGrid.MAX_SIZE) return null

        val bmpW = cols * ss
        val bmpH = rows * ss
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        for ((i, line) in lines.withIndex()) {
            if (line.isEmpty()) continue
            val text = line.joinToString("")
            val lineW = lineWidths[i]
            val x = when (spec.align) {
                TextAlign.LEFT -> padPx
                TextAlign.CENTER -> (bmpW - lineW) / 2f
                TextAlign.RIGHT -> bmpW - padPx - lineW
            }
            val baseline = padPx + i * linePx - fm.ascent
            // 逐字绘制：支持字间距与横向缩放
            var cx = x
            for (ch in line) {
                canvas.save()
                canvas.translate(cx, 0f)
                canvas.scale(widthScale, 1f)
                canvas.drawText(ch, 0f, baseline, paint)
                canvas.restore()
                cx += paint.measureText(ch) * widthScale + spacingPx
            }
        }

        // ---- 覆盖率 ----
        val pixels = IntArray(bmpW * bmpH)
        bmp.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
        val coverage = FloatArray(cols * rows)
        val inv = 1f / (ss * ss * 255f)
        for (r in 0 until rows) for (c in 0 until cols) {
            var sum = 0
            for (dy in 0 until ss) for (dx in 0 until ss) {
                val px = c * ss + dx
                val py = r * ss + dy
                if (px < bmpW && py < bmpH) sum += (pixels[py * bmpW + px] ushr 24) and 0xFF
            }
            coverage[r * cols + c] = sum * inv
        }
        bmp.recycle()

        // ---- 阈值 ----
        var body = BooleanArray(cols * rows) { coverage[it] >= spec.threshold }

        // ---- 断笔修复 ----
        var repaired = 0
        if (spec.repair) {
            val result = repair(body, coverage, cols, rows)
            body = result.first
            repaired = result.second
        }

        // ---- 描边 ----
        val outline = if (spec.outlineWidth > 0) {
            val dilated = dilate(body, cols, rows, spec.outlineWidth)
            BooleanArray(cols * rows) { dilated[it] && !body[it] }
        } else {
            BooleanArray(cols * rows)
        }

        // ---- 裁掉四周空白 ----
        return trim(body, outline, cols, rows, repaired)
    }

    /** 三类修复规则：孤点去除、横画断点补格、斜向连通。保证幂等。 */
    private fun repair(
        input: BooleanArray,
        coverage: FloatArray,
        cols: Int,
        rows: Int,
    ): Pair<BooleanArray, Int> {
        val grid = input.copyOf()
        var changedTotal = 0
        for (pass in 0 until 2) {
            val snapshot = grid.copyOf()
            var changed = 0
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                fun at(cc: Int, rr: Int): Boolean =
                    cc in 0 until cols && rr in 0 until rows && snapshot[rr * cols + cc]
                fun cov(cc: Int, rr: Int): Float =
                    if (cc in 0 until cols && rr in 0 until rows) coverage[rr * cols + cc] else 0f

                if (snapshot[i]) {
                    // 规则一：孤点（两格内没有任何邻居）视为噪点
                    var neighbors = 0
                    for (dy in -2..2) for (dx in -2..2) {
                        if (dx == 0 && dy == 0) continue
                        if (at(c + dx, r + dy)) neighbors++
                    }
                    if (neighbors == 0) {
                        grid[i] = false
                        changed++
                    }
                } else {
                    // 规则二：横画断裂
                    val left = at(c - 1, r)
                    val right = at(c + 1, r)
                    val up = at(c, r - 1)
                    val down = at(c, r + 1)
                    if (left && right && !up && !down &&
                        cov(c - 1, r) + cov(c, r) + cov(c + 1, r) > 1.2f
                    ) {
                        grid[i] = true
                        changed++
                        continue
                    }
                    // 规则三：撇捺断裂
                    val diag = (at(c - 1, r - 1) && at(c + 1, r + 1)) ||
                        (at(c + 1, r - 1) && at(c - 1, r + 1))
                    if (diag && !left && !right && !up && !down && cov(c, r) > 0.3f) {
                        grid[i] = true
                        changed++
                    }
                }
            }
            changedTotal += changed
            if (changed == 0) break
        }
        return grid to changedTotal
    }

    /** 八邻域迭代膨胀。 */
    private fun dilate(src: BooleanArray, cols: Int, rows: Int, times: Int): BooleanArray {
        var cur = src.copyOf()
        repeat(times) {
            val next = cur.copyOf()
            for (r in 0 until rows) for (c in 0 until cols) {
                if (!cur[r * cols + c]) continue
                for (dy in -1..1) for (dx in -1..1) {
                    val nc = c + dx
                    val nr = r + dy
                    if (nc in 0 until cols && nr in 0 until rows) next[nr * cols + nc] = true
                }
            }
            cur = next
        }
        return cur
    }

    private fun trim(
        body: BooleanArray,
        outline: BooleanArray,
        cols: Int,
        rows: Int,
        repaired: Int,
    ): TextMasks {
        var minC = cols; var maxC = -1; var minR = rows; var maxR = -1
        for (r in 0 until rows) for (c in 0 until cols) {
            if (body[r * cols + c] || outline[r * cols + c]) {
                if (c < minC) minC = c
                if (c > maxC) maxC = c
                if (r < minR) minR = r
                if (r > maxR) maxR = r
            }
        }
        if (maxC < 0) return TextMasks(1, 1, BooleanArray(1), BooleanArray(1), repaired)
        val w = maxC - minC + 1
        val h = maxR - minR + 1
        val b = BooleanArray(w * h)
        val o = BooleanArray(w * h)
        for (r in 0 until h) for (c in 0 until w) {
            b[r * w + c] = body[(r + minR) * cols + (c + minC)]
            o[r * w + c] = outline[(r + minR) * cols + (c + minC)]
        }
        return TextMasks(w, h, b, o, repaired)
    }

    /** 掩膜落成拼豆网格：主体色、描边色按色卡就近取号。 */
    fun toGrid(masks: TextMasks, palette: Palette, bodyArgb: Int, outlineArgb: Int): BeadGrid {
        val grid = BeadGrid(masks.width, masks.height)
        if (palette.colors.isEmpty()) return grid
        val bodyIndex = palette.nearestIndex(bodyArgb)
        val outlineIndex = palette.nearestIndex(outlineArgb)
        masks.eachCell { c, r, isBody ->
            grid.set(c, r, if (isBody) bodyIndex else outlineIndex)
        }
        return grid
    }

    /** 预览位图（每格 px 像素）；[boardSize] > 0 时画出分板缝线。 */
    fun preview(
        masks: TextMasks,
        bodyArgb: Int,
        outlineArgb: Int,
        px: Int = 6,
        boardSize: Int = 0,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(masks.width * px, masks.height * px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val paint = Paint().apply { isAntiAlias = false }
        masks.eachCell { c, r, isBody ->
            paint.color = if (isBody) bodyArgb else outlineArgb
            canvas.drawRect(
                (c * px).toFloat(), (r * px).toFloat(),
                ((c + 1) * px).toFloat(), ((r + 1) * px).toFloat(),
                paint,
            )
        }
        if (boardSize > 0) {
            val seam = Paint().apply {
                color = 0xFF3A3A3A.toInt()
                strokeWidth = (px * 0.34f).coerceAtLeast(1.4f)
                isAntiAlias = false
            }
            var c = boardSize
            while (c < masks.width) {
                canvas.drawLine((c * px).toFloat(), 0f, (c * px).toFloat(), (masks.height * px).toFloat(), seam)
                c += boardSize
            }
            var r = boardSize
            while (r < masks.height) {
                canvas.drawLine(0f, (r * px).toFloat(), (masks.width * px).toFloat(), (r * px).toFloat(), seam)
                r += boardSize
            }
            bmp.setPixel(0, 0, bmp.getPixel(0, 0))
        }
        return bmp
    }

    /** 按底板边长切分，返回「横向块数 × 纵向块数」。 */
    fun boardCount(width: Int, height: Int, boardSize: Int): Int {
        if (boardSize <= 0) return 1
        val cols = (width + boardSize - 1) / boardSize
        val rows = (height + boardSize - 1) / boardSize
        return cols * rows
    }
}
