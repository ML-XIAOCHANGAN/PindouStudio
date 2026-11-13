package com.pindou.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pindou.studio.PindouApp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.ChartExporter
import com.pindou.studio.core.ChartOptions
import com.pindou.studio.core.ChartTile
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.DisplayMode
import com.pindou.studio.core.GridHistory
import com.pindou.studio.core.Palette
import com.pindou.studio.core.Project
import com.pindou.studio.core.SelectionRect
import com.pindou.studio.core.SymmetryMode
import com.pindou.studio.core.Tool
import com.pindou.studio.data.InventoryRepository
import com.pindou.studio.data.ProjectPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 一个色号的用量。 */
data class UsageRow(val index: Int, val code: String, val hex: String, val count: Int)

/** 剪贴板内容。 */
class ClipboardData(val width: Int, val height: Int, val cells: ShortArray)

/**
 * 编辑器状态与全部绘制逻辑。
 */
class EditorViewModel(app: Application, private val projectId: String) : AndroidViewModel(app) {

    private val appRef = PindouApp.from(app)
    private val projectRepo = appRef.projectRepository
    private val paletteRepo = appRef.paletteRepository
    private val settings = appRef.settings
    private val inventory = appRef.inventoryRepository

    // 保存 / 加载一律走 viewModelScope + Dispatchers.IO：
    // 不再自建一个永不 cancel 的 Default 作用域，也不再在主线程读写磁盘。
    private var saveJob: Job? = null

    val history = GridHistory()

    // ---------- 核心状态 ----------
    var project by mutableStateOf(
        Project(
            id = projectId,
            name = "未命名",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            paletteId = "",
            paletteName = "",
            grid = BeadGrid(32, 32),
        )
    )
        private set

    var palette by mutableStateOf(paletteRepo.default())
        private set

    var loaded by mutableStateOf(false)
        private set

    /** 首次加载中（磁盘读取 + 解压都在 IO 线程）。 */
    var loading by mutableStateOf(false)
        private set

    var renderTick by mutableStateOf(0)
        private set

    var tool by mutableStateOf(Tool.PEN)
    var colorIndex by mutableStateOf(1)
        private set
    var symmetry by mutableStateOf(SymmetryMode.NONE)
    var displayMode by mutableStateOf(DisplayMode.of(settings.displayMode))

    var showCodes by mutableStateOf(settings.showCodes)
    var showGrid by mutableStateOf(settings.showGrid)
    var showMajorGrid by mutableStateOf(settings.showMajorGrid)
    var showRuler by mutableStateOf(settings.showRuler)
    var roundBeads by mutableStateOf(settings.roundBeads)

    var zoom by mutableStateOf(16f)
        private set
    var offsetX by mutableStateOf(0f)
        private set
    var offsetY by mutableStateOf(0f)
        private set

    var viewportWidth by mutableStateOf(0)
    var viewportHeight by mutableStateOf(0)

    var selection by mutableStateOf<SelectionRect?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)

    var baseImage by mutableStateOf<Bitmap?>(null)
    var baseOpacity by mutableStateOf(0.45f)
    var baseVisible by mutableStateOf(true)
    var baseScale by mutableStateOf(1f)

    var paletteFilter by mutableStateOf("")

    /** 单色隔离：0 关闭，否则只显示该色号（其余淡出）。 */
    var isolateColor by mutableStateOf(0)
        private set

    /** 拼制进度变化计数，供工作台重组。 */
    var progressTick by mutableStateOf(0)
        private set

    // ---------- 笔画状态 ----------
    private var recorder: GridHistory.StrokeRecorder? = null
    private var lastCellC = Int.MIN_VALUE
    private var lastCellR = Int.MIN_VALUE
    private var strokeStarted = false

    private var selectionSnapshot: BeadGrid? = null
    private var selectionDragStart: IntArray? = null
    private var selectionMoveOrigin: SelectionRect? = null
    private var clipboard: ClipboardData? = null

    private val grid: BeadGrid get() = project.grid

    /** 低缩放时一次落笔覆盖的格子边长。 */
    val lodScale: Int get() = max(1, ceil(MIN_CELL_PX / zoom).toInt())

    // ---------- 初始化 ----------
    /**
     * 打开工程：JSON 读取、GZIP 解压、底图解码全部在 IO 线程完成，
     * 主线程只负责把结果塞进 Compose 状态，避免大工程打开时卡住首帧。
     */
    fun load() {
        if (loaded || loading) return
        loading = true
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val hadFileBefore = projectRepo.hasFile(projectId)
                val pair = projectRepo.load(projectId, paletteRepo)
                val bitmap = pair?.first?.baseImagePath?.let { path ->
                    runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
                }
                LoadResult(pair, bitmap, hadFileBefore)
            }
            val loadedPair = snapshot.pair
            if (loadedPair != null) {
                project = loadedPair.first
                palette = loadedPair.second
            } else {
                val pal = paletteRepo.byId(settings.lastPaletteId) ?: paletteRepo.default()
                palette = pal
                project = Project(
                    id = projectId,
                    name = "新作品",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    paletteId = pal.id,
                    paletteName = pal.name,
                    grid = BeadGrid(48, 48),
                )
                if (snapshot.hadFile) {
                    message = "工程文件损坏，已新建空白画布（原文件已另存为 .corrupt 备份）"
                }
            }
            baseImage = snapshot.baseImage
            colorIndex = if (palette.size > 0) 1 else 0
            loaded = true
            loading = false
            bump()
            fitToScreen()
        }
    }

    private class LoadResult(
        val pair: Pair<Project, Palette>?,
        val baseImage: Bitmap?,
        val hadFile: Boolean,
    )

    private fun bump() {
        renderTick++
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    // ---------- 单色隔离 ----------
    fun toggleIsolate(index: Int) {
        isolateColor = if (isolateColor == index) 0 else index
        bump()
    }

    fun clearIsolate() {
        if (isolateColor != 0) {
            isolateColor = 0
            bump()
        }
    }

    // ---------- 拼制进度 ----------
    fun isDone(c: Int, r: Int): Boolean = project.progress.contains(r * grid.width + c)

    fun toggleProgress(c: Int, r: Int) {
        if (!grid.inBounds(c, r)) return
        if (grid.get(c, r) == 0) return
        val idx = r * grid.width + c
        if (!project.progress.remove(idx)) project.progress.add(idx)
        progressTick++
        scheduleProgressSave()
    }

    fun clearProgress() {
        project.progress.clear()
        progressTick++
        scheduleProgressSave()
    }

    fun doneBeads(): Int = project.doneBeads()

    fun totalBeads(): Int = project.totalBeads()

    private var progressSaveJob: Job? = null
    private fun scheduleProgressSave() {
        // 打点也算"未保存的改动"，必须在编辑器里点保存才落盘
        scheduleSave()
    }

    // ---------- 豆仓缺色 ----------
    fun shortageNeeds(): List<InventoryRepository.Need> {
        val usage = HashMap<String, Int>()
        for ((idx, count) in grid.colorUsage()) {
            val code = palette.colorAt(idx)?.code ?: continue
            usage[code] = (usage[code] ?: 0) + count
        }
        return inventory.compare(palette, usage)
    }

    fun inventorySummary(): Pair<Int, Int> = inventory.summary()

    // ---------- 视图变换 ----------
    fun updateViewport(w: Int, h: Int) {
        val wasEmpty = viewportWidth == 0 || viewportHeight == 0
        viewportWidth = w
        viewportHeight = h
        if (wasEmpty && w > 0 && h > 0) fitToScreen()
    }

    fun fitToScreen() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val w = grid.width
        val h = grid.height
        val available = min(
            (viewportWidth - 32f) / w,
            (viewportHeight - 32f) / h,
        )
        zoom = available.coerceIn(MIN_ZOOM, MAX_ZOOM)
        offsetX = (viewportWidth - w * zoom) / 2f
        offsetY = (viewportHeight - h * zoom) / 2f
    }

    fun applyZoom(newZoom: Float) {
        val clamped = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f
        zoomAt(androidx.compose.ui.geometry.Offset(cx, cy), clamped / zoom)
    }

    fun zoomIn() = applyZoom(zoom * 1.3f)
    fun zoomOut() = applyZoom(zoom / 1.3f)

    /** 以某个屏幕点为焦点缩放。 */
    fun zoomAt(focus: androidx.compose.ui.geometry.Offset, factor: Float) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return
        val ratio = newZoom / zoom
        offsetX = focus.x - (focus.x - offsetX) * ratio
        offsetY = focus.y - (focus.y - offsetY) * ratio
        zoom = newZoom
    }

    fun pan(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    fun screenToCell(x: Float, y: Float): Pair<Int, Int> {
        val c = floor((x - offsetX) / zoom).toInt()
        val r = floor((y - offsetY) / zoom).toInt()
        return c to r
    }

    fun cellToScreenX(c: Int): Float = offsetX + c * zoom
    fun cellToScreenY(r: Int): Float = offsetY + r * zoom

    // ---------- 工具操作 ----------
    fun selectTool(t: Tool) {
        if (t != Tool.SELECT) clearSelection()
        tool = t
    }

    fun chooseColor(index: Int) {
        if (index in 1..palette.size) colorIndex = index
        if (tool == Tool.ERASER || tool == Tool.EYEDROPPER) tool = Tool.PEN
    }

    /**
     * 是否处于编辑态。
     *
     * 打开作品默认进**只读预览**（和模板页一致）：先看清这张图纸，再点「进入编辑」。
     * 新建 / 从图片生成 / 模板保存后进入的则直接是编辑态。
     */
    var editing by mutableStateOf(false)
        private set

    fun enterEditing() {
        editing = true
        bump()
    }

    fun exitEditing() {
        editing = false
        clearSelection()
        bump()
    }

    /** 依次切换对称绘制模式，并把当前模式的含义用一句话说明。 */
    fun cycleSymmetry() {
        symmetry = when (symmetry) {
            SymmetryMode.NONE -> SymmetryMode.HORIZONTAL
            SymmetryMode.HORIZONTAL -> SymmetryMode.VERTICAL
            SymmetryMode.VERTICAL -> SymmetryMode.QUAD
            SymmetryMode.QUAD -> SymmetryMode.DIAGONAL
            SymmetryMode.DIAGONAL -> SymmetryMode.NONE
        }
        message = "对称绘制：${symmetry.label} —— ${symmetry.hint}"
    }

    /**
     * 放弃本次作品：移入回收站（可恢复）。
     *
     * 「从图片生成」这类流程会先建一个工程，如果试完不满意，
     * 以前只能回首页再从菜单里删，现在编辑器里一步就能丢掉。
     */
    fun discardProject() {
        val id = project.id
        // 先掐掉所有排队中的保存，再标记为未加载，避免删完又被写回来
        saveJob?.cancel()
        progressSaveJob?.cancel()
        loaded = false
        saving = false
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectRepo.deleteSerialized(id)
                com.pindou.studio.data.ProjectFlags.forget(getApplication(), id)
            }
        }
    }

    val activeColorHex: String
        get() = palette.colorAt(colorIndex)?.hex ?: "#000000"

    val activeColorCode: String
        get() = palette.colorAt(colorIndex)?.code ?: "-"

    /** 手指按下。 */
    fun onPointerDown(x: Float, y: Float) {
        val (c, r) = screenToCell(x, y)
        when (tool) {
            Tool.PEN, Tool.ERASER -> beginStroke(c, r)
            Tool.FILL -> doFill(c, r)
            Tool.EYEDROPPER -> doPick(c, r)
            Tool.SELECT -> beginSelection(c, r)
            Tool.PAN -> Unit
        }
    }

    /** 手指移动。 */
    fun onPointerMove(x: Float, y: Float) {
        val (c, r) = screenToCell(x, y)
        when (tool) {
            Tool.PEN, Tool.ERASER -> continueStroke(c, r)
            Tool.SELECT -> continueSelection(c, r)
            else -> Unit
        }
    }

    /** 手指抬起。 */
    fun onPointerUp() {
        when (tool) {
            Tool.PEN, Tool.ERASER -> endStroke()
            Tool.SELECT -> endSelection()
            else -> Unit
        }
    }

    fun cancelStroke() {
        if (strokeStarted) {
            endStroke()
        }
    }

    private fun beginStroke(c: Int, r: Int) {
        recorder = GridHistory.StrokeRecorder(grid)
        strokeStarted = true
        lastCellC = Int.MIN_VALUE
        lastCellR = Int.MIN_VALUE
        paintAt(c, r)
    }

    private fun continueStroke(c: Int, r: Int) {
        if (!strokeStarted) return
        paintAt(c, r)
    }

    private fun endStroke() {
        if (!strokeStarted) return
        strokeStarted = false
        val patch = recorder?.build()
        recorder = null
        if (patch != null) {
            history.push(patch)
            scheduleSave()
            bump()
        }
    }

    /** 在某个格子上落笔（含对称、LOD、拖拽插值）。 */
    private fun paintAt(c: Int, r: Int) {
        val previousC = lastCellC
        val previousR = lastCellR
        lastCellC = c
        lastCellR = r
        val value = if (tool == Tool.ERASER) 0 else colorIndex
        if (previousC == Int.MIN_VALUE) {
            forEachSymmetry(c, r) { cc, rr -> paintBlock(cc, rr, value) }
            bump()
            return
        }
        // Bresenham：避免快速拖动断线
        var x0 = previousC
        var y0 = previousR
        val x1 = c
        val y1 = r
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var guard = 0
        while (guard++ < 4096) {
            forEachSymmetry(x0, y0) { cc, rr -> paintBlock(cc, rr, value) }
            if (x0 == x1 && y0 == y1) break
            val e2 = err * 2
            if (e2 > -dy) { err -= dy; x0 += sx }
            if (e2 < dx) { err += dx; y0 += sy }
        }
        bump()
    }

    private fun paintBlock(c: Int, r: Int, value: Int) {
        val lod = lodScale
        val c0 = if (lod == 1) c else Math.floorDiv(c, lod) * lod
        val r0 = if (lod == 1) r else Math.floorDiv(r, lod) * lod
        for (dr in 0 until lod) {
            for (dc in 0 until lod) {
                val cc = c0 + dc
                val rr = r0 + dr
                if (grid.inBounds(cc, rr)) {
                    recorder?.record(cc, rr, value)
                }
            }
        }
    }

    private inline fun forEachSymmetry(c: Int, r: Int, action: (Int, Int) -> Unit) {
        action(c, r)
        when (symmetry) {
            SymmetryMode.NONE -> Unit
            SymmetryMode.HORIZONTAL -> action(grid.width - 1 - c, r)
            SymmetryMode.VERTICAL -> action(c, grid.height - 1 - r)
            SymmetryMode.QUAD -> {
                action(grid.width - 1 - c, r)
                action(c, grid.height - 1 - r)
                action(grid.width - 1 - c, grid.height - 1 - r)
            }
            SymmetryMode.DIAGONAL -> action(r, c)
        }
    }

    private fun doFill(c: Int, r: Int) {
        if (!grid.inBounds(c, r)) return
        val before = GridHistory.snapshotOf(grid)
        val changed = grid.floodFill(c, r, colorIndex)
        if (changed > 0) {
            history.push(before)
            scheduleSave()
            bump()
        }
    }

    private fun doPick(c: Int, r: Int) {
        val idx = grid.get(c, r)
        if (idx > 0) {
            colorIndex = idx
            tool = Tool.PEN
            message = "已吸取 ${palette.colorAt(idx)?.code ?: ""}"
        } else {
            message = "该位置为空"
        }
    }

    // ---------- 选区 ----------
    private fun beginSelection(c: Int, r: Int) {
        val sel = selection
        if (sel != null && sel.contains(c, r)) {
            // 拖动选区内容
            selectionSnapshot = grid.copy()
            selectionMoveOrigin = sel
            selectionDragStart = intArrayOf(c, r)
            return
        }
        selection = null
        selectionDragStart = intArrayOf(c, r)
        selectionSnapshot = null
        selectionMoveOrigin = null
    }

    private fun continueSelection(c: Int, r: Int) {
        val start = selectionDragStart ?: return
        if (!grid.inBounds(start[0], start[1]) && !grid.inBounds(c, r)) {
            if (selectionSnapshot == null) return
        }
        val snapshot = selectionSnapshot
        val origin = selectionMoveOrigin
        if (snapshot != null && origin != null) {
            // 移动选区内容：先恢复原图，再把内容整体搬到新坐标
            val dc = c - start[0]
            val dr = r - start[1]
            grid.restoreFrom(snapshot)
            val moved = SelectionRect(
                (origin.left + dc).coerceIn(0, grid.width - 1),
                (origin.top + dr).coerceIn(0, grid.height - 1),
                (origin.right + dc).coerceIn(0, grid.width - 1),
                (origin.bottom + dr).coerceIn(0, grid.height - 1),
            )
            for (rr in origin.top..origin.bottom) {
                for (cc in origin.left..origin.right) grid.set(cc, rr, 0)
            }
            for (rr in origin.top..origin.bottom) {
                for (cc in origin.left..origin.right) {
                    val v = snapshot.get(cc, rr)
                    val nc = cc + dc
                    val nr = rr + dr
                    if (grid.inBounds(nc, nr)) grid.set(nc, nr, v)
                }
            }
            selection = moved
            bump()
        } else {
            selection = SelectionRect.fromPoints(start[0], start[1], c, r)
                .let { clipSelection(it) }
            bump()
        }
    }

    private fun endSelection() {
        val snapshot = selectionSnapshot
        if (snapshot != null) {
            history.push(GridHistory.snapshotOf(snapshot))
            scheduleSave()
        }
        selectionSnapshot = null
        selectionDragStart = null
        selectionMoveOrigin = null
        bump()
    }

    private fun clipSelection(sel: SelectionRect): SelectionRect = SelectionRect(
        sel.left.coerceIn(0, grid.width - 1),
        sel.top.coerceIn(0, grid.height - 1),
        sel.right.coerceIn(0, grid.width - 1),
        sel.bottom.coerceIn(0, grid.height - 1),
    )

    fun clearSelection() {
        selection = null
        selectionSnapshot = null
        selectionDragStart = null
        selectionMoveOrigin = null
        bump()
    }

    fun selectAll() {
        selection = SelectionRect(0, 0, grid.width - 1, grid.height - 1)
        bump()
    }

    fun fillSelection() {
        val sel = selection ?: return
        val before = GridHistory.snapshotOf(grid)
        for (r in sel.top..sel.bottom) {
            for (c in sel.left..sel.right) {
                if (grid.inBounds(c, r)) grid.set(c, r, colorIndex)
            }
        }
        history.push(before)
        scheduleSave()
        bump()
    }

    fun deleteSelection() {
        val sel = selection ?: return
        val before = GridHistory.snapshotOf(grid)
        for (r in sel.top..sel.bottom) {
            for (c in sel.left..sel.right) {
                if (grid.inBounds(c, r)) grid.set(c, r, 0)
            }
        }
        history.push(before)
        scheduleSave()
        bump()
    }

    fun copySelection() {
        val sel = selection ?: return
        clipboard = ClipboardData(
            sel.width, sel.height,
            ShortArray(sel.width * sel.height) { i ->
                val c = sel.left + i % sel.width
                val r = sel.top + i / sel.width
                grid.get(c, r).toShort()
            },
        )
        message = "已复制 ${sel.width}×${sel.height} 选区"
    }

    fun cutSelection() {
        copySelection()
        deleteSelection()
    }

    fun pasteSelection() {
        val clip = clipboard ?: run {
            message = "剪贴板为空"
            return
        }
        val before = GridHistory.snapshotOf(grid)
        val originC = selection?.left ?: 0
        val originR = selection?.top ?: 0
        for (r in 0 until clip.height) {
            for (c in 0 until clip.width) {
                val targetC = originC + c
                val targetR = originR + r
                if (grid.inBounds(targetC, targetR)) {
                    grid.set(targetC, targetR, clip.cells[r * clip.width + c].toInt())
                }
            }
        }
        history.push(before)
        scheduleSave()
        bump()
    }

    // ---------- 历史 ----------
    fun undo() {
        val result = history.undo(grid) ?: return
        project.grid = result
        selection = null
        scheduleSave()
        bump()
        fitIfNeeded()
    }

    fun redo() {
        val result = history.redo(grid) ?: return
        project.grid = result
        selection = null
        scheduleSave()
        bump()
        fitIfNeeded()
    }

    private fun fitIfNeeded() {
        // 尺寸变化后重新适配视图
        if (offsetX.isNaN() || offsetY.isNaN()) fitToScreen()
    }

    fun clearCanvas() {
        val before = GridHistory.snapshotOf(grid)
        grid.clear()
        history.push(before)
        selection = null
        scheduleSave()
        bump()
    }

    // ---------- 图像操作 ----------
    fun flipHorizontal() = applyGridOp { it.flipHorizontal() }
    fun flipVertical() = applyGridOp { it.flipVertical() }

    fun rotateClockwise() = applyGridOpReplace { it.rotatedClockwise() }
    fun rotateCounterClockwise() = applyGridOpReplace { it.rotatedCounterClockwise() }

    private fun applyGridOp(op: (BeadGrid) -> Unit) {
        val before = GridHistory.snapshotOf(grid)
        op(grid)
        history.push(before)
        selection = null
        scheduleSave()
        bump()
    }

    private fun applyGridOpReplace(op: (BeadGrid) -> BeadGrid) {
        val before = grid
        val result = op(grid)
        project.grid = result
        history.push(GridHistory.snapshotOf(before))
        selection = null
        scheduleSave()
        bump()
        fitToScreen()
    }

    /** 扩裁画布：[anchor] 0=左上 1=居中。 */
    fun resizeCanvas(newWidth: Int, newHeight: Int, anchor: Int) {
        val w = newWidth.coerceIn(1, BeadGrid.MAX_SIZE)
        val h = newHeight.coerceIn(1, BeadGrid.MAX_SIZE)
        if (w == grid.width && h == grid.height) return
        val dx = if (anchor == 1) (w - grid.width) / 2 else 0
        val dy = if (anchor == 1) (h - grid.height) / 2 else 0
        val before = grid
        val resized = grid.resized(w, h, dx, dy)
        project.grid = resized
        history.push(GridHistory.snapshotOf(before))
        selection = null
        scheduleSave()
        bump()
        fitToScreen()
    }

    /** 应用图片转换结果（可能改变画布尺寸）。 */
    fun applyImportedGrid(newGrid: BeadGrid) {
        val before = grid
        project.grid = newGrid
        history.push(GridHistory.snapshotOf(before))
        selection = null
        scheduleSave()
        bump()
        fitToScreen()
    }

    /** 新建空画布。 */
    fun newCanvas(w: Int, h: Int) {
        val before = grid
        project.grid = BeadGrid(w.coerceIn(1, BeadGrid.MAX_SIZE), h.coerceIn(1, BeadGrid.MAX_SIZE))
        history.push(GridHistory.snapshotOf(before))
        selection = null
        scheduleSave()
        bump()
        fitToScreen()
    }

    // ---------- 色卡 ----------
    fun switchPalette(newPalette: Palette) {
        if (newPalette.id == palette.id) return
        // 按色号把旧颜色映射到新色卡，缺失的色号就近取色
        val oldPalette = palette
        val remapped = BeadGrid(grid.width, grid.height)
        val matcherCache = HashMap<Int, Int>()
        for (r in 0 until grid.height) {
            for (c in 0 until grid.width) {
                val idx = grid.get(c, r)
                if (idx == 0) continue
                val oldColor = oldPalette.colorAt(idx) ?: continue
                var newIdx = newPalette.indexOf(oldColor.code)
                if (newIdx == 0) {
                    newIdx = matcherCache.getOrPut(idx) {
                        nearestIndex(newPalette, oldColor.argb)
                    }
                }
                remapped.set(c, r, newIdx)
            }
        }
        val before = grid
        project.grid = remapped
        project.paletteId = newPalette.id
        project.paletteName = newPalette.name
        palette = newPalette
        colorIndex = if (newPalette.size > 0) colorIndex.coerceIn(1, newPalette.size) else 0
        history.clear()
        settings.lastPaletteId = newPalette.id
        scheduleSave()
        bump()
        message = "已切换到 ${newPalette.name}"
        if (before.width != remapped.width) fitToScreen()
    }

    private fun nearestIndex(pal: Palette, argb: Int): Int {
        val lab = ColorMath.rgbToOklab(argb)
        var best = 1
        var bestD = Float.MAX_VALUE
        pal.colors.forEachIndexed { i, color ->
            val d = ColorMath.oklabDistance(lab, color.lab)
            if (d < bestD) { bestD = d; best = i + 1 }
        }
        return best
    }

    // ---------- 底图 ----------
    fun setBaseImage(bitmap: Bitmap?, persist: Boolean = true) {
        baseImage = bitmap
        baseScale = 1f
        if (!persist) return
        if (bitmap == null) {
            val old = project.baseImagePath
            project.baseImagePath = null
            viewModelScope.launch(Dispatchers.IO) {
                old?.let { runCatching { java.io.File(it).delete() } }
            }
            scheduleSave()
            return
        }
        // PNG 压缩放 IO 线程；路径先记下来，保证这一刻退出编辑器也不会丢掉底图引用
        val target = java.io.File(projectRepo.baseDir(), project.id + ".png")
        project.baseImagePath = target.absolutePath
        markEdited()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.FileOutputStream(target).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
                    }
                }.isSuccess
            }
            if (ok) {
                scheduleSave()
            } else {
                project.baseImagePath = null
                message = "底图保存失败"
                scheduleSave()
            }
        }
    }

    // ---------- 设置 ----------
    fun toggleShowCodes() { showCodes = !showCodes; settings.showCodes = showCodes }
    fun toggleShowGrid() { showGrid = !showGrid; settings.showGrid = showGrid }
    fun toggleMajorGrid() { showMajorGrid = !showMajorGrid; settings.showMajorGrid = showMajorGrid }
    fun toggleRuler() { showRuler = !showRuler; settings.showRuler = showRuler }
    fun toggleRoundBeads() { roundBeads = !roundBeads; settings.roundBeads = roundBeads }

    fun changeDisplayMode(mode: DisplayMode) {
        displayMode = mode
        settings.displayMode = mode.key
        bump()
    }

    // ---------- 统计 ----------
    fun usageRows(): List<UsageRow> {
        val rows = ArrayList<UsageRow>()
        for ((idx, count) in grid.colorUsage()) {
            val color = palette.colorAt(idx) ?: continue
            rows.add(UsageRow(idx, color.code, color.hex, count))
        }
        return rows.sortedBy { palette.indexOf(it.code) }
    }

    // ---------- 保存 ----------
    /**
     * 有未保存的改动（顶栏显示「未保存」，退出时据此弹确认）。
     *
     * 和模板页统一：**编辑不再自动落盘**，必须点「保存」或退出时选「保存并退出」才算数。
     */
    var dirty by mutableStateOf(false)
        private set

    /**
     * 内容变了：只标记"未保存"，不写盘。
     *
     * （以前这里是 700ms 防抖自动保存；为了和模板页统一成"确认才保存"，改成纯标记。）
     */
    fun scheduleSave() {
        markEdited()
        dirty = true
    }

    /** 保存到磁盘（唯一的确认口）。不阻塞界面。 */
    fun persistNow() {
        saveJob?.cancel()
        submitSave(writeThumbnail = true)
    }

    private fun submitSave(writeThumbnail: Boolean) {
        if (!loaded) return
        val version = editVersion
        val payload = try {
            project.touch()
            ProjectPayload.capture(project, palette)
        } catch (e: Exception) {
            message = "保存失败：${e.message}"
            return
        }
        settings.lastPaletteId = palette.id
        settings.lastProjectId = project.id
        saving = true
        projectRepo.saveAsync(payload, writeThumbnail) { ok ->
            saving = false
            if (ok) {
                if (version > savedVersion) savedVersion = version
                if (version >= editVersion) dirty = false
                saveFailureNotified = false
            } else if (!saveFailureNotified) {
                // 只提示一次，避免自动保存失败时反复弹 snackbar
                saveFailureNotified = true
                message = "保存失败，请检查存储空间"
            }
        }
    }

    private var saveFailureNotified = false

    /**
     * 改动版本号 / 已落盘版本号。
     *
     * 防抖窗口里改动会被 `saveJob` 取消掉，所以不能只看「有没有在写」，
     * 得看「最后一次落盘覆盖到了第几次改动」——退出编辑器时据此决定是否再补保存一次。
     * 两者都只在主线程读写。
     */
    private var editVersion = 0
    private var savedVersion = 0

    /** 每次内容改变后调用：标记有未落盘的改动。 */
    private fun markEdited() {
        editVersion++
    }

    fun rename(newName: String) {
        if (newName.isBlank()) return
        project.name = newName
        project.touch()
        markEdited()
        persistNow()
        bump()
    }

    // ---------- 导出 ----------
    fun exportChart(options: ChartOptions): List<ChartTile> =
        ChartExporter.render(grid, palette, options)

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
        progressSaveJob?.cancel()
        // viewModelScope 到这里已经被取消，收尾保存交给仓库自己的 IO 作用域写完，
        // 否则退出编辑器前最后几百毫秒的操作会丢。
        // 不再在销毁时偷偷保存：没点「保存」的改动就是要丢弃（和模板页一致）
        baseImage = null
    }

    class Factory(private val app: Application, private val projectId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(app, projectId) as T
        }
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 72f
        const val MIN_CELL_PX = 9f
    }
}
