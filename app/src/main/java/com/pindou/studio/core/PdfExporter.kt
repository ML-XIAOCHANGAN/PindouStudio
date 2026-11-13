package com.pindou.studio.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 把图纸渲染成可打印的 PDF（A4，单页自适应）。
 *
 * 内部先复用 [ChartExporter] 画一张位图，再等比缩放进 A4 纸张的页边距内，
 * 这样屏幕上的坐标、色号、用量表都会原样出现在纸面上。
 */
object PdfExporter {

    /** A4 @150dpi */
    private const val PAGE_W = 1240
    private const val PAGE_H = 1754
    private const val MARGIN = 48
    private const val TITLE_H = 46

    /**
     * @param chartOptions 图纸渲染参数（色号 / 坐标 / 用量表等）
     * @return 写好的 PDF 文件，失败返回 null
     */
    fun export(
        grid: BeadGrid,
        palette: Palette,
        chartOptions: ChartOptions,
        target: File,
        title: String = "",
    ): File? {
        var document: PdfDocument? = null
        return try {
            val chart = ChartExporter.renderWindow(
                grid, palette, chartOptions, 0, 0, grid.width, grid.height,
            )
            document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            var top = MARGIN.toFloat()

            if (title.isNotBlank()) {
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1C1A17")
                    textSize = 30f
                    isFakeBoldText = false
                }
                canvas.drawText(title, MARGIN.toFloat(), top + 30f, titlePaint)
                top += TITLE_H
            }

            val availableW = PAGE_W - MARGIN * 2
            val availableH = PAGE_H - MARGIN - top
            val scale = min(availableW.toFloat() / chart.width, availableH.toFloat() / chart.height)
            val drawW = chart.width * scale
            val drawH = chart.height * scale
            val left = (PAGE_W - drawW) / 2f
            canvas.drawBitmap(
                chart,
                null,
                android.graphics.RectF(left, top, left + drawW, top + drawH),
                paint,
            )
            chart.recycle()

            // ---- 比例校验尺：100mm 实线段，用来判断打印时是否被缩放 ----
            drawScaleRuler(canvas)

            document.finishPage(page)
            FileOutputStream(target).use { out -> document.writeTo(out) }
            target
        } catch (e: Exception) {
            null
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 画一条 100 毫米的实线并标注「请实测」。
     *
     * 家用打印机默认「适应纸张」，一缩比例图纸就全错。
     * 这条尺子是用户唯一能当场验证比例的手段，因此固定画在页面底部。
     */
    private fun drawScaleRuler(canvas: Canvas) {
        val mmToPx = 150f / 25.4f
        val length = 100f * mmToPx
        val left = (PAGE_W - length) / 2f
        val y = PAGE_H - MARGIN.toFloat() - 26f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1A17")
            strokeWidth = 2f
        }
        canvas.drawLine(left, y, left + length, y, paint)
        canvas.drawLine(left, y - 8f, left, y + 8f, paint)
        canvas.drawLine(left + length, y - 8f, left + length, y + 8f, paint)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B6259")
            textSize = 17f
        }
        val label = "校验尺 100mm — 打印后请实测，若长度不符请关闭打印机的缩放选项"
        val w = text.measureText(label)
        canvas.drawText(label, (PAGE_W - w) / 2f, y + 26f, text)
    }
}
