package com.pindou.studio.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.Palette
import com.pindou.studio.core.Project
import com.pindou.studio.core.ProjectSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * 工程仓库：工程以 JSON + 缩略图 PNG 保存在应用私有目录。
 *
 * ## 写盘策略
 * - **原子写**：先写 `<id>.json.tmp`，fsync 后再 rename 成 `<id>.json`，
 *   旧文件保留为 `<id>.json.bak`。进程在任何时刻被杀，都不会留下半个 JSON。
 * - **读盘兜底**：主文件解析失败时依次尝试 `.bak` / `.tmp`，成功即把好文件复位；
 *   三个都读不出来才隔离成 `.corrupt-<时间戳>`，原文不删，留给用户抢救。
 * - **写盘串行**：同一时刻只允许一个写盘任务（[writeLock]），两次保存不会交错。
 * - **写盘独立于界面**：[ioScope] 不随任何 ViewModel 销毁而取消，
 *   退出编辑器后那一次收尾保存不会被掐断。
 */
class ProjectRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** 保存专用作用域：生命周期跟随 Application。 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 同一时刻只写一个文件。 */
    private val writeLock = Mutex()

    /** 保存回调统一回主线程，调用方可以直接改 Compose 状态。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 色卡 → DTO 的缓存，避免每次保存都重新 map 上千个颜色。 */
    @Volatile
    private var cachedPalette: Palette? = null

    @Volatile
    private var cachedPaletteDtos: List<PaletteColorDto> = emptyList()

    private val projectDir: File by lazy { File(context.filesDir, "projects").apply { mkdirs() } }
    private val thumbDir: File by lazy { File(context.filesDir, "thumbs").apply { mkdirs() } }
    private val baseDir: File by lazy { File(context.filesDir, "base").apply { mkdirs() } }
    private val trashDir: File by lazy { File(context.filesDir, "trash").apply { mkdirs() } }

    fun baseDir(): File = baseDir

    fun newId(): String = "p" + System.currentTimeMillis() + "-" + (1000..9999).random()

    fun thumbnailFile(id: String): File = File(thumbDir, "$id.png")

    private fun projectFile(id: String): File = File(projectDir, "$id.json")

    private fun backupFile(id: String): File = File(projectDir, "$id.json$BAK")

    /** 工程文件是否存在（不含备份）。 */
    fun hasFile(id: String): Boolean = projectFile(id).exists()

    // ---------------------------------------------------------------- 保存

    /**
     * 同步保存：**只允许在 IO 线程调用**，或用于「新建工程」这类几十毫秒内跑完的小网格。
     */
    fun saveBlocking(payload: ProjectPayload, writeThumbnail: Boolean = true): Boolean =
        writePayload(payload, writeThumbnail)

    /**
     * 兼容旧调用点的同步保存（新建 / 复制 / 导入工程）。
     * 小网格 + 内置色卡时单次只有几 KB，主线程上可以接受。
     */
    fun save(project: Project, palette: Palette, writeThumbnail: Boolean = true): Boolean =
        saveBlocking(ProjectPayload.capture(project, palette), writeThumbnail)

    /**
     * 用一张现成的网格新建工程（模板、模板编辑、文字 DIY 都走这里）。
     * @return 新工程 id（写入失败也返回 id，调用方可继续进入编辑器）
     */
    fun createFromGrid(name: String, grid: BeadGrid, palette: Palette): String {
        val id = newId()
        val now = System.currentTimeMillis()
        val project = Project(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now,
            paletteId = palette.id,
            paletteName = palette.name,
            grid = grid,
        )
        save(project, palette)
        return id
    }

    /**
     * 异步保存：立即返回，写盘在仓库自己的 IO 作用域完成。
     * [onFinished] 固定回调在主线程，调用方可以放心改 Compose 状态。
     */
    fun saveAsync(
        payload: ProjectPayload,
        writeThumbnail: Boolean = true,
        onFinished: (Boolean) -> Unit = {},
    ) {
        ioScope.launch {
            val ok = writeLock.withLock { writePayload(payload, writeThumbnail) }
            mainHandler.post { onFinished(ok) }
        }
    }

    private fun writePayload(payload: ProjectPayload, writeThumbnail: Boolean): Boolean {
        return try {
            val dto = ProjectDto(
                id = payload.id,
                name = payload.name,
                createdAt = payload.createdAt,
                updatedAt = System.currentTimeMillis(),
                paletteId = payload.paletteId,
                paletteName = payload.paletteName,
                // 内置色卡靠 id 即可还原，不再把上千个色号抄进每一份工程；
                // 用户色卡 / 导入色卡仍然内联快照，删掉色卡后工程也不会变色。
                paletteColors = if (payload.palette.builtin) emptyList() else paletteDtos(payload.palette),
                width = payload.width,
                height = payload.height,
                cells = Codec.encodeCells(payload.cells),
                baseImagePath = payload.baseImagePath,
                note = payload.note,
                beadCount = payload.beadCount,
                progress = Codec.encodeIntSet(payload.progress),
            )
            val text = json.encodeToString(ProjectDto.serializer(), dto)
            if (!writeAtomically(projectFile(payload.id), text)) return false
            if (writeThumbnail) {
                writeThumbnail(payload.id, payload.cells, payload.width, payload.height, payload.palette)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "保存工程失败: ${payload.id}", e)
            false
        }
    }

    private fun paletteDtos(palette: Palette): List<PaletteColorDto> {
        if (cachedPalette === palette) return cachedPaletteDtos
        val dtos = palette.colors.map { PaletteColorDto.fromModel(it) }
        cachedPalette = palette
        cachedPaletteDtos = dtos
        return dtos
    }

    /**
     * 原子写：tmp → fsync → 旧文件转 .bak → rename。
     * 任何一步失败都会返回 false，不会把主文件写成半截。
     */
    private fun writeAtomically(target: File, text: String): Boolean {
        val dir = target.parentFile ?: return false
        dir.mkdirs()
        val tmp = File(dir, target.name + TMP)
        val bak = File(dir, target.name + BAK)
        return try {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (target.exists()) {
                bak.delete()
                if (!target.renameTo(bak)) Log.w(TAG, "无法备份旧工程: ${target.name}")
            }
            if (!tmp.renameTo(target)) {
                // 极端情况下 rename 失败（目录被占等），退化为复制，至少内容是对的
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "写入工程失败: ${target.name}", e)
            tmp.delete()
            false
        }
    }

    // ---------------------------------------------------------------- 导出

    /** 把工程导出为可移植文件（.pdp），始终内联完整色卡。 */
    fun exportToFile(project: Project, palette: Palette, target: File): Boolean = try {
        val dto = ProjectDto(
            id = project.id,
            name = project.name,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            paletteId = project.paletteId,
            paletteName = project.paletteName,
            paletteColors = palette.colors.map { PaletteColorDto.fromModel(it) },
            width = project.width,
            height = project.height,
            cells = Codec.encodeCells(project.grid),
            baseImagePath = null,
            note = project.note,
            beadCount = project.grid.paintedCount(),
            progress = Codec.encodeIntSet(project.progress),
        )
        writeAtomically(target, json.encodeToString(ProjectDto.serializer(), dto))
    } catch (e: Exception) {
        Log.w(TAG, "导出工程失败", e)
        false
    }

    /** 从 .pdp 文件读取工程。 */
    fun importFromFile(
        file: File,
        fallbackPalette: Palette? = null,
    ): Triple<Project, Palette, Boolean>? = try {
        val dto = json.decodeFromString(ProjectDto.serializer(), file.readText())
        val palette = if (dto.paletteColors.isNotEmpty()) {
            Palette(dto.paletteId, dto.paletteName.ifBlank { "导入色卡" }, dto.paletteColors.map { it.toModel() }, builtin = false)
        } else {
            fallbackPalette
                ?: Palette(dto.paletteId, dto.paletteName.ifBlank { "导入色卡" }, emptyList(), builtin = false)
        }
        val grid = Codec.decodeCells(dto.cells, dto.width, dto.height)
        val now = System.currentTimeMillis()
        val project = Project(
            id = newId(),
            name = dto.name.ifBlank { file.nameWithoutExtension },
            createdAt = now,
            updatedAt = now,
            paletteId = dto.paletteId,
            paletteName = dto.paletteName,
            grid = grid,
            note = dto.note,
        )
        Triple(project, palette, dto.paletteColors.isNotEmpty())
    } catch (e: Exception) {
        Log.w(TAG, "导入工程失败: ${file.name}", e)
        null
    }

    // ---------------------------------------------------------------- 读取

    fun list(): List<ProjectSummary> {
        val files = projectDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val out = ArrayList<ProjectSummary>(files.size)
        for (file in files) {
            try {
                val dto = json.decodeFromString(ProjectDto.serializer(), file.readText())
                val thumb = thumbnailFile(dto.id)
                val done = runCatching {
                    Codec.decodeIntSet(dto.progress, dto.width * dto.height).size
                }.getOrDefault(0)
                out.add(
                    ProjectSummary(
                        id = dto.id,
                        name = dto.name,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt,
                        width = dto.width,
                        height = dto.height,
                        paletteName = dto.paletteName,
                        beadCount = dto.beadCount,
                        doneBeads = done,
                        thumbnailPath = if (thumb.exists()) thumb.absolutePath else null,
                    )
                )
            } catch (_: Exception) {
                // 忽略损坏文件（.bak / .tmp 不会被 extension == "json" 选中）
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    /**
     * 读取工程。
     * @return 工程与其色卡；色卡优先使用工程内快照。
     */
    fun load(id: String, paletteRepository: PaletteRepository): Pair<Project, Palette>? {
        val dto = readDto(id) ?: return null
        return try {
            val palette = when {
                dto.paletteColors.isNotEmpty() -> Palette(
                    dto.paletteId,
                    dto.paletteName.ifBlank { "工程色卡" },
                    dto.paletteColors.map { it.toModel() },
                    builtin = false,
                )
                else -> paletteRepository.byId(dto.paletteId) ?: paletteRepository.default()
            }
            val grid = Codec.decodeCells(dto.cells, dto.width, dto.height)
            val project = Project(
                id = dto.id,
                name = dto.name,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt,
                paletteId = palette.id,
                paletteName = palette.name,
                grid = grid,
                baseImagePath = dto.baseImagePath,
                note = dto.note,
            )
            project.progress.addAll(Codec.decodeIntSet(dto.progress, grid.cells.size))
            project to palette
        } catch (e: Exception) {
            Log.w(TAG, "解析工程失败: $id", e)
            null
        }
    }

    /** 读 JSON：主文件 → .bak → .tmp，读出来哪份就把哪份复位成主文件。 */
    private fun readDto(id: String): ProjectDto? {
        val main = projectFile(id)
        val candidates = listOf(
            main,
            backupFile(id),
            File(projectDir, main.name + TMP),
        )
        for (file in candidates) {
            if (!file.exists() || file.length() == 0L) continue
            val dto = runCatching {
                json.decodeFromString(ProjectDto.serializer(), file.readText())
            }.getOrNull() ?: continue
            if (file != main) {
                Log.w(TAG, "工程 $id 主文件不可用，已从 ${file.name} 恢复")
                runCatching {
                    main.delete()
                    file.copyTo(main, overwrite = true)
                }
            }
            return dto
        }
        // 主文件存在却三份都解析失败：隔离原文，避免被下一次保存直接覆盖掉
        if (main.exists() && main.length() > 0) {
            val quarantine = File(projectDir, main.name + ".corrupt-" + System.currentTimeMillis())
            runCatching { main.renameTo(quarantine) }
            Log.w(TAG, "工程 $id 无法解析，已隔离为 ${quarantine.name}")
        }
        return null
    }

    // ---------------------------------------------------------------- 删除 / 回收站

    /**
     * 删除作品：先把 json + 缩略图移进回收站，可直接「恢复」，
     * 避免误删把用户几天的图纸弄丢。
     */
    fun delete(id: String): Boolean {
        val file = projectFile(id)
        if (!file.exists()) return false
        return try {
            trashDir.mkdirs()
            file.renameTo(File(trashDir, id + ".json"))
            val bak = backupFile(id)
            if (bak.exists()) bak.renameTo(File(trashDir, id + ".json$BAK"))
            val thumb = thumbnailFile(id)
            if (thumb.exists()) thumb.renameTo(File(trashDir, id + ".png"))
            true
        } catch (e: Exception) {
            Log.w(TAG, "删除工程失败: $id", e)
            file.delete()
        }
    }

    /**
     * 删除工程（与保存共用同一把锁）：保证「放弃作品」时不会有排队中的保存
     * 在删除之后又把文件写回来。
     */
    suspend fun deleteSerialized(id: String): Boolean = writeLock.withLock { delete(id) }

    /** 回收站里的作品数。 */
    fun trashCount(): Int =
        trashDir.listFiles { f -> f.extension == "json" }?.size ?: 0

    /** 把回收站里的作品全部恢复。 */
    fun restoreTrash(): Int {
        val files = trashDir.listFiles { f -> f.extension == "json" } ?: return 0
        var restored = 0
        for (f in files) {
            val id = f.nameWithoutExtension
            if (f.renameTo(projectFile(id))) {
                val bak = File(trashDir, id + ".json$BAK")
                if (bak.exists()) bak.renameTo(backupFile(id))
                val thumb = File(trashDir, id + ".png")
                if (thumb.exists()) thumb.renameTo(thumbnailFile(id))
                restored++
            }
        }
        return restored
    }

    /** 清空回收站。 */
    fun purgeTrash() {
        trashDir.listFiles()?.forEach { it.delete() }
    }

    fun rename(id: String, newName: String, paletteRepository: PaletteRepository): Boolean {
        val loaded = load(id, paletteRepository) ?: return false
        loaded.first.name = newName
        loaded.first.touch()
        return save(loaded.first, loaded.second)
    }

    // ---------------------------------------------------------------- 缩略图

    fun writeThumbnail(
        id: String,
        cells: ShortArray,
        width: Int,
        height: Int,
        palette: Palette,
        maxSide: Int = 420,
    ) {
        try {
            val bmp = renderThumbnail(cells, width, height, palette, maxSide)
            FileOutputStream(thumbnailFile(id)).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "缩略图写入失败: $id", e)
        }
    }

    companion object {
        private const val TAG = "ProjectRepository"
        private const val BAK = ".bak"
        private const val TMP = ".tmp"
        private const val THUMB_MAX_SIDE = 420

        /** 渲染缩略图（保持比例，透明处为白）。 */
        fun renderThumbnail(grid: BeadGrid, palette: Palette, maxSide: Int = 360): Bitmap =
            renderThumbnail(grid.cells, grid.width, grid.height, palette, maxSide)

        fun renderThumbnail(
            cells: ShortArray,
            width: Int,
            height: Int,
            palette: Palette,
            maxSide: Int = THUMB_MAX_SIDE,
        ): Bitmap {
            val b = cellsBounds(cells, width, height)
            val minC: Int
            val minR: Int
            val cols: Int
            val rows: Int
            if (b == null) {
                minC = 0; minR = 0; cols = width; rows = height
            } else {
                minC = b[0]; minR = b[1]; cols = b[2] - b[0] + 1; rows = b[3] - b[1] + 1
            }
            val scale = maxSide.toFloat() / max(cols, rows)
            val cw = max(1, (cols * scale).toInt())
            val ch = max(1, (rows * scale).toInt())
            val bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(0xFFFFFFFF.toInt())
            val paint = Paint().apply { isAntiAlias = false }
            val cellW = cw.toFloat() / cols
            val cellH = ch.toFloat() / rows
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val sr = minR + r
                    val sc = minC + c
                    if (sr !in 0 until height || sc !in 0 until width) continue
                    val idx = cells[sr * width + sc].toInt()
                    if (idx == 0) continue
                    val color = palette.colorAt(idx) ?: continue
                    paint.color = color.argb
                    canvas.drawRect(
                        c * cellW, r * cellH, (c + 1) * cellW + 0.5f, (r + 1) * cellH + 0.5f, paint,
                    )
                }
            }
            return bmp
        }

        private fun cellsBounds(cells: ShortArray, width: Int, height: Int): IntArray? {
            var minC = width
            var maxC = -1
            var minR = height
            var maxR = -1
            for (r in 0 until height) {
                val base = r * width
                for (c in 0 until width) {
                    if (cells[base + c].toInt() != 0) {
                        if (c < minC) minC = c
                        if (c > maxC) maxC = c
                        if (r < minR) minR = r
                        if (r > maxR) maxR = r
                    }
                }
            }
            if (maxC < 0) return null
            return intArrayOf(minC, minR, maxC, maxR)
        }
    }
}
