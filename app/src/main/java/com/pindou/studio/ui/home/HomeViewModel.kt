package com.pindou.studio.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pindou.studio.PindouApp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.Palette
import com.pindou.studio.core.Project
import com.pindou.studio.core.ProjectSummary
import com.pindou.studio.data.PdFormat
import com.pindou.studio.data.ProjectFlags
import com.pindou.studio.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 首页状态。 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = PindouApp.from(app)
    private val projectRepo = appRef.projectRepository
    private val paletteRepo = appRef.paletteRepository
    private val settings = appRef.settings

    var projects by mutableStateOf<List<ProjectSummary>>(emptyList())
        private set
    var palettes by mutableStateOf<List<Palette>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
    var trashCount by mutableStateOf(0)
        private set
    var exportedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 收藏的作品 id（轻量收藏夹，存在 SharedPreferences 里）。 */
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 首屏数据是否已经读过一遍（避免进入页面时先闪一下空状态）。 */
    var loaded by mutableStateOf(false)
        private set

    private var refreshing = false
    private var refreshQueued = false

    init {
        refresh()
    }

    /**
     * 重新读取作品列表。
     *
     * 所有磁盘解析（逐个工程 JSON + GZIP 解开进度）都放到 IO 线程，
     * 首页不再因为作品变多而卡住主线程。
     */
    fun refresh() {
        if (refreshing) {
            // 正在读盘时又收到刷新请求（例如刚删完作品），读完后再补一轮，
            // 避免把过期列表留在页面上
            refreshQueued = true
            return
        }
        refreshing = true
        viewModelScope.launch {
            do {
                refreshQueued = false
                val data = withContext(Dispatchers.IO) {
                    HomeData(
                        projects = projectRepo.list(),
                        palettes = paletteRepo.all(),
                        trash = projectRepo.trashCount(),
                        exported = ProjectFlags.exportedIds(getApplication()),
                        favorites = ProjectFlags.favoriteIds(getApplication()),
                    )
                }
                projects = data.projects
                palettes = data.palettes
                trashCount = data.trash
                exportedIds = data.exported
                favorites = data.favorites
                loaded = true
            } while (refreshQueued)
            refreshing = false
        }
    }

    private class HomeData(
        val projects: List<ProjectSummary>,
        val palettes: List<Palette>,
        val trash: Int,
        val exported: Set<String>,
        val favorites: Set<String>,
    )

    /** 收藏 / 取消收藏（本地偏好，同步完成，不用等 IO）。 */
    fun toggleFavorite(id: String) {
        val nowFavorite = ProjectFlags.toggleFavorite(getApplication(), id)
        favorites = ProjectFlags.favoriteIds(getApplication())
        message = if (nowFavorite) "已收藏，可在「收藏」筛选里找到" else "已取消收藏"
    }

    /** 从回收站恢复。 */
    fun restoreTrash() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { projectRepo.restoreTrash() }
            message = if (n > 0) "已恢复 $n 个作品" else "回收站是空的"
            refresh()
        }
    }

    fun defaultPaletteId(): String =
        palettes.firstOrNull { it.id == settings.lastPaletteId }?.id
            ?: palettes.firstOrNull()?.id
            ?: ""

    /**
     * 新建工程，返回工程 id。
     *
     * 空画布 + 内置色卡的写盘量很小（几 KB），保持同步以便立即跳转到编辑器；
     * 真正耗时的列表刷新仍然是异步的。
     */
    fun createProject(name: String, width: Int, height: Int, paletteId: String): String {
        val palette = paletteRepo.byId(paletteId) ?: paletteRepo.default()
        val id = projectRepo.newId()
        val now = System.currentTimeMillis()
        val project = Project(
            id = id,
            name = name.ifBlank { "未命名作品" },
            createdAt = now,
            updatedAt = now,
            paletteId = palette.id,
            paletteName = palette.name,
            grid = BeadGrid(width.coerceIn(1, BeadGrid.MAX_SIZE), height.coerceIn(1, BeadGrid.MAX_SIZE)),
        )
        val ok = projectRepo.save(project, palette)
        if (!ok) message = "新建失败，请检查存储空间"
        settings.lastPaletteId = palette.id
        refresh()
        return id
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { projectRepo.delete(id) }
            message = "已移入回收站，可在首页「恢复已删除」找回"
            refresh()
        }
    }

    fun rename(id: String, newName: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                projectRepo.rename(id, newName, paletteRepo)
            }
            if (!ok) message = "重命名失败"
            refresh()
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val loadedPair = projectRepo.load(id, paletteRepo) ?: return@withContext false
                val (project, palette) = loadedPair
                val copy = Project(
                    id = projectRepo.newId(),
                    name = project.name + " 副本",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    paletteId = palette.id,
                    paletteName = palette.name,
                    grid = project.grid.copy(),
                    note = project.note,
                )
                projectRepo.save(copy, palette)
            }
            if (!ok) message = "复制失败"
            refresh()
        }
    }

    /** 导出工程到临时文件（读 + 写都在 IO 线程）。 */
    fun exportProject(id: String, target: File, asPd: Boolean, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val loadedPair = projectRepo.load(id, paletteRepo) ?: return@withContext false
                val (project, palette) = loadedPair
                if (asPd) PdFormat.write(project, palette, target)
                else projectRepo.exportToFile(project, palette, target)
            }
            onDone(ok)
        }
    }

    /**
     * 导入工程文件（.pd / .pdp / .json）。
     *
     * 读文件、解析、解码网格、写回全部在 IO 线程，完成后回调新工程 id（失败为 null）。
     */
    fun importProject(file: File, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { importProjectBlocking(file) }
            refresh()
            result.message?.let { message = it }
            onDone(result.id)
        }
    }

    /** 从系统选择器返回的 Uri 导入：复制到缓存 + 解析都放在 IO 线程。 */
    fun importProjectFromUri(uri: android.net.Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                FileUtils.copyUriToCache(
                    getApplication(),
                    uri,
                    "import_" + System.currentTimeMillis() + ".json",
                )
            }
            if (file == null) {
                message = "读取文件失败"
                onDone(null)
                return@launch
            }
            importProject(file, onDone)
        }
    }

    private class ImportResult(val id: String?, val message: String? = null)

    private fun importProjectBlocking(file: File): ImportResult {
        val text = runCatching { file.readText() }.getOrNull()
            ?: return ImportResult(null, "读取文件失败")
        val normalized = text.trimStart()
        val looksLikePd = normalized.startsWith("{") &&
            normalized.contains("\"canvas\"") &&
            normalized.contains("\"grid\"")
        val name = file.name.lowercase()
        val fallback = paletteRepo.byId(settings.lastPaletteId) ?: paletteRepo.default()

        if (looksLikePd || name.endsWith(".pd")) {
            val result = PdFormat.read(file, projectRepo.newId(), fallback)
            if (result != null) {
                val (project, palette, hasPalette) = result
                if (!projectRepo.save(project, palette)) {
                    return ImportResult(null, "写入工程失败，请检查存储空间")
                }
                return ImportResult(
                    project.id,
                    if (hasPalette) "已导入 PD 工程（含色卡）" else "已导入 PD 工程（使用当前色卡）",
                )
            }
        }
        val imported = projectRepo.importFromFile(file, fallback)
        if (imported != null) {
            val (project, palette, _) = imported
            if (!projectRepo.save(project, palette)) {
                return ImportResult(null, "写入工程失败，请检查存储空间")
            }
            return ImportResult(project.id)
        }
        return ImportResult(null, "无法识别的工程文件")
    }
}
