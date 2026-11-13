package com.pindou.studio.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pindou.studio.ui.theme.Ds

/**
 * 品牌标志：四颗字母用拼豆摆成，PDGF = Pindou Studio。
 *
 * 字母按 7×9 的格子写死，每颗豆子是一个圆点，方便做「黑色 → 彩色」的逐颗上色动画。
 */
object BrandLogo {

    const val LETTER_COLS = 7
    const val LETTER_ROWS = 9
    private const val GAP_COLS = 1

    /** 四个字母 + 各自的主色（取自品牌图）。 */
    private val letters: List<Pair<List<String>, Color>> = listOf(
        listOf(
            "XXXXX..",
            "XXXXXX.",
            "XX...XX",
            "XX...XX",
            "XXXXXX.",
            "XXXXX..",
            "XX.....",
            "XX.....",
            "XX.....",
        ) to Color(0xFF1E6B3A),
        listOf(
            "XXXXX..",
            "XXXXXX.",
            "XX...XX",
            "XX...XX",
            "XX...XX",
            "XX...XX",
            "XX...XX",
            "XXXXXX.",
            "XXXXX..",
        ) to Color(0xFFB5DC6B),
        listOf(
            ".XXXXX.",
            "XXXXXXX",
            "XX.....",
            "XX.....",
            "XX..XXX",
            "XX...XX",
            "XX...XX",
            "XXXXXXX",
            ".XXXXX.",
        ) to Color(0xFFF2A03C),
        listOf(
            "XXXXXXX",
            "XXXXXXX",
            "XX.....",
            "XX.....",
            "XXXXX..",
            "XXXXX..",
            "XX.....",
            "XX.....",
            "XX.....",
        ) to Color(0xFF63B8E8),
    )

    /** 整块字母的宽高比，用来按宽度算高度。 */
    val aspect: Float
        get() = (LETTER_COLS * 4 + GAP_COLS * 3).toFloat() / LETTER_ROWS

    /**
     * 画「PDGF」四个字母。
     *
     * @param colorProgress 0 = 纯黑，1 = 完全上色；按字母错开，P 先上色，F 最后。
     */
    @Composable
    fun LetterMark(
        colorProgress: Float,
        modifier: Modifier = Modifier,
        beadGap: Float = 0.08f,
    ) {
        val inkColor = Color(0xFF161616)
        Canvas(modifier) {
            val totalCols = LETTER_COLS * 4 + GAP_COLS * 3
            val cell = size.width / totalCols
            val radius = cell * (1f - beadGap) / 2f
            val originY = (size.height - LETTER_ROWS * cell) / 2f

            letters.forEachIndexed { li, (rows, color) ->
                // 逐字母错开：整体进度被放大 1.55 倍，每个字母再晚 0.16
                val letterProgress =
                    ((colorProgress * 1.55f) - li * 0.16f).coerceIn(0f, 1f)
                val baseColor = lerp(inkColor, color, letterProgress)
                val originX = li * (LETTER_COLS + GAP_COLS) * cell

                for (r in rows.indices) {
                    val row = rows[r]
                    for (c in row.indices) {
                        if (row[c] != 'X') continue
                        val cx = originX + c * cell + cell / 2f
                        val cy = originY + r * cell + cell / 2f
                        drawCircle(baseColor, radius, Offset(cx, cy))
                        // 上色后补一点高光，做出豆子的鼓面
                        if (letterProgress > 0.02f) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.30f * letterProgress),
                                radius = radius * 0.30f,
                                center = Offset(cx - radius * 0.34f, cy - radius * 0.36f),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 完整标志：[lettersWidth] 宽的豆子字母 + 右侧文字。
     * [wordmarkProgress] 从 0 到 1 时，文字从字母右边「抹开」。
     */
    @Composable
    fun Lockup(
        colorProgress: Float,
        wordmarkProgress: Float,
        lettersWidth: Dp = 232.dp,
        showWordmark: Boolean = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterMark(
                colorProgress = colorProgress,
                modifier = Modifier
                    .width(lettersWidth)
                    .height(lettersWidth / aspect),
            )
            if (showWordmark) {
                Spacer(Modifier.width(Ds.s3))
                Column(
                    Modifier.drawWithContent {
                        val w = size.width * wordmarkProgress.coerceIn(0f, 1f)
                        clipRect(right = w) { this@drawWithContent.drawContent() }
                    },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "拼豆工坊",
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF14512E),
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "Pindou Studio",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF7A8F82),
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}
