package com.pindou.studio.core

/**
 * 一个拼豆工程。
 */
class Project(
    var id: String,
    var name: String,
    var createdAt: Long,
    var updatedAt: Long,
    var paletteId: String,
    var paletteName: String,
    var grid: BeadGrid,
    var baseImagePath: String? = null,
    var note: String = "",
) {
    /** 已拼格子（行优先下标），用于拼制工作台的进度打点。 */
    val progress: MutableSet<Int> = HashSet()

    val width: Int get() = grid.width
    val height: Int get() = grid.height

    /** 需要拼的格子数（= 已上色格数）。 */
    fun totalBeads(): Int = grid.paintedCount()

    /** 已拼格数（只统计仍然有颜色的格子，避免换了色卡后残留）。 */
    fun doneBeads(): Int = progress.count { idx ->
        idx in grid.cells.indices && grid.cells[idx].toInt() != 0
    }

    fun progressFraction(): Float {
        val total = totalBeads()
        return if (total == 0) 0f else (doneBeads().toFloat() / total).coerceIn(0f, 1f)
    }

    fun touch() {
        updatedAt = System.currentTimeMillis()
    }
}

/** 工程列表项。 */
data class ProjectSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val width: Int,
    val height: Int,
    val paletteName: String,
    val beadCount: Int,
    /** 工作台已打点的颗数（0 表示还没开工）。 */
    val doneBeads: Int = 0,
    val thumbnailPath: String?,
)
