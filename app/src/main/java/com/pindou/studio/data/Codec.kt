package com.pindou.studio.data

import android.util.Base64
import com.pindou.studio.core.BeadGrid
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 网格 ↔ 紧凑字符串（Base64 + GZIP）。 */
object Codec {

    fun encodeCells(grid: BeadGrid): String = encodeCells(grid.cells)

    fun encodeCells(cells: ShortArray): String {
        val raw = ByteArray(cells.size * 2)
        for (i in cells.indices) {
            val v = cells[i].toInt()
            raw[i * 2] = (v and 0xFF).toByte()
            raw[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** 把「已拼格子下标」集合压成 Base64(GZIP(varint 增量))，空集合返回 ""。 */
    fun encodeIntSet(values: Collection<Int>): String = encodeIntSet(values.toIntArray())

    /** IntArray 版本：只读入参，内部排序自己的副本，调用方可以继续用原数组。 */
    fun encodeIntSet(values: IntArray): String {
        if (values.isEmpty()) return ""
        val sorted = values.sortedArray()
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz ->
            var prev = 0
            for (v in sorted) {
                var delta = v - prev
                prev = v
                while (delta >= 0x80) {
                    gz.write((delta and 0x7F) or 0x80)
                    delta = delta ushr 7
                }
                gz.write(delta)
            }
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** 还原下标集合；超出 [size] 的下标会被丢弃。 */
    fun decodeIntSet(data: String, size: Int): MutableSet<Int> {
        val set = HashSet<Int>()
        if (data.isBlank()) return set
        return try {
            val raw = GZIPInputStream(ByteArrayInputStream(Base64.decode(data, Base64.NO_WRAP)))
                .use { it.readBytes() }
            var i = 0
            var cur = 0
            while (i < raw.size) {
                var shift = 0
                var delta = 0
                while (i < raw.size) {
                    val b = raw[i++].toInt() and 0xFF
                    delta = delta or ((b and 0x7F) shl shift)
                    if (b and 0x80 == 0) break
                    shift += 7
                }
                cur += delta
                if (cur in 0 until size) set.add(cur)
            }
            set
        } catch (e: Exception) {
            set
        }
    }

    fun decodeCells(data: String, width: Int, height: Int): BeadGrid {
        val grid = BeadGrid(width.coerceIn(1, BeadGrid.MAX_SIZE), height.coerceIn(1, BeadGrid.MAX_SIZE))
        if (data.isBlank()) return grid
        return try {
            val compressed = Base64.decode(data, Base64.NO_WRAP)
            val raw = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
            val count = minOf(grid.cells.size, raw.size / 2)
            for (i in 0 until count) {
                val lo = raw[i * 2].toInt() and 0xFF
                val hi = raw[i * 2 + 1].toInt() and 0xFF
                grid.cells[i] = ((hi shl 8) or lo).toShort()
            }
            grid
        } catch (e: Exception) {
            grid
        }
    }
}
