package com.pindou.studio.core

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 撤销 / 重做历史。
 *
 * - 笔画（拖动绘制）记录成补丁（只存被改动的格子），非常省内存；
 * - 一次性操作（填充 / 清空 / 图片导入 / 旋转 / 扩裁 / 切色卡）记录整张快照，
 *   快照以 **deflate 压缩** 存放：1024×1024 的画布未压缩是 2MB，压完通常只有几十 KB，
 *   80 步历史也不至于把内存吃光（旧实现是每步 2MB 的裸拷贝，理论峰值 160MB）。
 * - 除了步数上限，还额外按 **压缩后字节数** 兜一层预算 [snapshotBudgetBytes]。
 */
class GridHistory(
    private val limit: Int = DEFAULT_LIMIT,
    private val snapshotBudgetBytes: Long = DEFAULT_SNAPSHOT_BUDGET,
) {

    /** 一条历史记录。 */
    sealed class Entry {
        abstract val width: Int
        abstract val height: Int
    }

    /** 补丁型记录：只包含被修改的格子。 */
    class Patch(
        override val width: Int,
        override val height: Int,
        val index: IntArray,
        val before: ShortArray,
        val after: ShortArray,
    ) : Entry()

    /**
     * 快照型记录：把整张网格还原到 [state]。
     *
     * 构造时就地压缩，**不保留对入参数组的引用**，所以可以直接把画布当前的
     * `cells` 交进来（见 [snapshotOf]），省掉一次整表拷贝。
     */
    class Snapshot(
        override val width: Int,
        override val height: Int,
        state: ShortArray,
    ) : Entry() {

        private val packed: ByteArray = packShortArray(state)

        /** 压缩后占用的字节数，用于内存预算。 */
        val byteSize: Int get() = packed.size

        fun restore(): ShortArray = unpackShortArray(packed, width * height)
    }

    private val undoStack = ArrayList<Entry>()
    private val redoStack = ArrayList<Entry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    /** 当前历史里快照占用的压缩后字节数（调试 / 测试用）。 */
    fun snapshotBytes(): Long {
        var total = 0L
        for (entry in undoStack) if (entry is Snapshot) total += entry.byteSize
        for (entry in redoStack) if (entry is Snapshot) total += entry.byteSize
        return total
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun push(entry: Entry) {
        undoStack.add(entry)
        redoStack.clear()
        while (undoStack.size > limit) undoStack.removeAt(0)
        trimToBudget()
    }

    /** 压缩后仍超预算时，先挤掉最老的快照（补丁很便宜，不动）。 */
    private fun trimToBudget() {
        if (snapshotBudgetBytes <= 0) return
        while (snapshotBytes() > snapshotBudgetBytes) {
            val index = undoStack.indexOfFirst { it is Snapshot }
            if (index < 0) return
            undoStack.removeAt(index)
        }
    }

    /**
     * 撤销一步。
     * @return 需要恢复到的网格，null 表示没有可撤销的内容。
     */
    fun undo(current: BeadGrid): BeadGrid? {
        if (undoStack.isEmpty()) return null
        val entry = undoStack.removeAt(undoStack.size - 1)
        return when (entry) {
            is Patch -> {
                redoStack.add(entry)
                current.applyPatch(entry.index, entry.before)
                current
            }
            is Snapshot -> {
                redoStack.add(snapshotOf(current))
                BeadGrid(entry.width, entry.height, entry.restore())
            }
        }
    }

    /**
     * 重做一步。
     * @return 需要恢复到的网格，null 表示没有可重做的内容。
     */
    fun redo(current: BeadGrid): BeadGrid? {
        if (redoStack.isEmpty()) return null
        val entry = redoStack.removeAt(redoStack.size - 1)
        return when (entry) {
            is Patch -> {
                undoStack.add(entry)
                current.applyPatch(entry.index, entry.after)
                current
            }
            is Snapshot -> {
                undoStack.add(snapshotOf(current))
                BeadGrid(entry.width, entry.height, entry.restore())
            }
        }
    }

    /** 累加一次笔画产生的改动。 */
    class StrokeRecorder(private val grid: BeadGrid) {
        private val indexBuf = ArrayList<Int>()
        private val beforeBuf = ArrayList<Short>()
        private val afterBuf = ArrayList<Short>()
        private val touched = HashMap<Int, Int>() // cellIndex -> position in buffers

        fun record(c: Int, r: Int, newValue: Int) {
            if (!grid.inBounds(c, r)) return
            val idx = r * grid.width + c
            val old = grid.cells[idx]
            if (old.toInt() == newValue) return
            val existing = touched[idx]
            if (existing != null) {
                afterBuf[existing] = newValue.toShort()
                grid.cells[idx] = newValue.toShort()
                if (beforeBuf[existing] == afterBuf[existing]) {
                    // 恢复原状，从补丁里剔除
                    touched.remove(idx)
                }
                return
            }
            touched[idx] = indexBuf.size
            indexBuf.add(idx)
            beforeBuf.add(old)
            afterBuf.add(newValue.toShort())
            grid.cells[idx] = newValue.toShort()
        }

        fun hasChanges(): Boolean = touched.isNotEmpty()

        fun build(): Patch? {
            if (touched.isEmpty()) return null
            val keep = indexBuf.indices.filter { i ->
                beforeBuf[i] != afterBuf[i]
            }
            if (keep.isEmpty()) return null
            return Patch(
                grid.width,
                grid.height,
                IntArray(keep.size) { indexBuf[keep[it]] },
                ShortArray(keep.size) { beforeBuf[keep[it]] },
                ShortArray(keep.size) { afterBuf[keep[it]] },
            )
        }
    }

    companion object {
        /** 默认保留步数。 */
        const val DEFAULT_LIMIT = 80

        /** 快照压缩后的总预算：24MB，超大画布 + 大量快照时兜底。 */
        const val DEFAULT_SNAPSHOT_BUDGET = 24L * 1024 * 1024

        /**
         * 直接为网格拍一张快照：立刻压缩，不保留对 `cells` 的引用，
         * 因此不需要先 `copyOf()` 一次。
         */
        fun snapshotOf(grid: BeadGrid): Snapshot =
            Snapshot(grid.width, grid.height, grid.cells)
    }
}

/** 把补丁应用到网格。 */
fun BeadGrid.applyPatch(index: IntArray, values: ShortArray) {
    for (i in index.indices) {
        val idx = index[i]
        if (idx in cells.indices) cells[idx] = values[i]
    }
}

// ---------------------------------------------------------------- 快照压缩

private fun packShortArray(state: ShortArray): ByteArray {
    val raw = ByteArray(state.size * 2)
    for (i in state.indices) {
        val v = state[i].toInt()
        raw[i * 2] = (v and 0xFF).toByte()
        raw[(i * 2) + 1] = ((v ushr 8) and 0xFF).toByte()
    }
    val deflater = Deflater(Deflater.BEST_SPEED)
    return try {
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream(raw.size / 4 + 64)
        val buf = ByteArray(8 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        out.toByteArray()
    } finally {
        deflater.end()
    }
}

private fun unpackShortArray(packed: ByteArray, cellCount: Int): ShortArray {
    val out = ShortArray(cellCount)
    if (cellCount <= 0 || packed.isEmpty()) return out
    val inflater = Inflater()
    return try {
        inflater.setInput(packed)
        val raw = ByteArray(cellCount * 2)
        var offset = 0
        while (offset < raw.size) {
            val n = inflater.inflate(raw, offset, raw.size - offset)
            if (n <= 0) break
            offset += n
        }
        for (i in 0 until cellCount) {
            val lo = raw[i * 2].toInt() and 0xFF
            val hi = raw[(i * 2) + 1].toInt() and 0xFF
            out[i] = ((hi shl 8) or lo).toShort()
        }
        out
    } catch (e: Exception) {
        ShortArray(cellCount)
    } finally {
        inflater.end()
    }
}
