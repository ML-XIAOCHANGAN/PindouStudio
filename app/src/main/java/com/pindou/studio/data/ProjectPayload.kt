package com.pindou.studio.data

import com.pindou.studio.core.Palette
import com.pindou.studio.core.Project

/**
 * 不可变的「保存载荷」。
 *
 * 由主线程一次性拍摄（拷贝 cells / progress），再整份交给 IO 线程落盘。
 * 这样保存过程与画布绘制互不干扰：
 *
 * - 不会再出现「保存协程遍历 HashSet 时主线程正在增删」的并发修改异常；
 * - 不会再写出「读到一半被改掉」的半截工程文件；
 * - 主线程只做一次内存拷贝（1M 格 ≈ 2MB，亚毫秒级），磁盘和压缩全部在 IO 线程。
 */
class ProjectPayload private constructor(
    val id: String,
    val name: String,
    val createdAt: Long,
    val paletteId: String,
    val paletteName: String,
    val width: Int,
    val height: Int,
    /** 网格快照（已拷贝） */
    val cells: ShortArray,
    /** 拼制进度下标（已拷贝，升序无关紧要，编码时会排序） */
    val progress: IntArray,
    val beadCount: Int,
    val baseImagePath: String?,
    val note: String,
    /** 缩略图渲染需要；Palette 本身不可变，跨线程持有安全。 */
    val palette: Palette,
) {
    companion object {
        /** 在持有 [Project] 的线程（通常是主线程）上调用。 */
        fun capture(project: Project, palette: Palette): ProjectPayload {
            val cells = project.grid.cells.copyOf()
            var painted = 0
            for (v in cells) if (v.toInt() != 0) painted++
            return ProjectPayload(
                id = project.id,
                name = project.name,
                createdAt = project.createdAt,
                paletteId = palette.id,
                paletteName = palette.name,
                width = project.grid.width,
                height = project.grid.height,
                cells = cells,
                progress = project.progress.toIntArray(),
                beadCount = painted,
                baseImagePath = project.baseImagePath,
                note = project.note,
                palette = palette,
            )
        }
    }
}
