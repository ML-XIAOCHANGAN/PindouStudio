package com.pindou.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/** 开屏 / 空态共用的彩豆图案。 */
object BeadArt {

    /** 小爱心（9×8），'X' 表示落豆。 */
    val HEART = listOf(
        "..XX.XX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..XXXXX..",
        "...XXX...",
        "....X....",
    )

    /** 彩虹豆色，从暖到冷。 */
    val RAINBOW = listOf(
        Color(0xFFF0663F), Color(0xFFF3A23C), Color(0xFFF2CE4B),
        Color(0xFF9BC65B), Color(0xFF3FA97A), Color(0xFF3B93C4),
        Color(0xFF6E77CE), Color(0xFFC76BB4), Color(0xFFE96A8B),
    )
}

/** 静态的彩豆爱心，可用在空态、卡片里。 */
@Composable
fun BeadHeart(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Canvas(modifier) {
        val cols = BeadArt.HEART.first().length
        val rows = BeadArt.HEART.size
        val cell = size.minDimension / (cols + 1f)
        val gap = cell * 0.16f
        val bead = cell - gap
        val originX = (size.width - cols * cell) / 2f
        val originY = (size.height - rows * cell) / 2f
        for ((r, row) in BeadArt.HEART.withIndex()) {
            for ((c, ch) in row.withIndex()) {
                if (ch != 'X') continue
                val color = BeadArt.RAINBOW[(r + c) % BeadArt.RAINBOW.size]
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(
                        originX + c * cell + gap / 2f,
                        originY + r * cell + gap / 2f,
                    ),
                    size = Size(bead, bead),
                    cornerRadius = CornerRadius(bead * 0.28f, bead * 0.28f),
                )
            }
        }
    }
}

/** 四角小星（点缀用）。 */
@Composable
fun Sparkle(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = size.minDimension / 2f
        val w = arm * 0.22f
        val path = Path().apply {
            moveTo(cx, cy - arm)
            lineTo(cx + w, cy)
            lineTo(cx, cy + arm)
            lineTo(cx - w, cy)
            close()
        }
        drawPath(path, color)
        val p2 = Path().apply {
            moveTo(cx - arm, cy)
            lineTo(cx, cy - w)
            lineTo(cx + arm, cy)
            lineTo(cx, cy + w)
            close()
        }
        drawPath(p2, color)
    }
}
