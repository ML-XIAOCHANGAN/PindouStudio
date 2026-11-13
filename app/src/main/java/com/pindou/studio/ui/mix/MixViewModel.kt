package com.pindou.studio.ui.mix

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pindou.studio.PindouApp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.LayerMixer
import com.pindou.studio.core.Palette
import com.pindou.studio.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * MIX 叠加的工作台状态：两张作品 + 层级 / 蒙版 / 透明度。
 *
 * 上层统一重映射到下层所用的色卡，避免两张图用不同品牌色卡时串色。
 */
class MixViewModel(
    app: Application,
    private val idA: String,
    private val idB: String,
) : AndroidViewModel(app) {

    private val appRef = PindouApp.from(app)
    private val projectRepo = appRef.projectRepository
    private val paletteRepo = appRef.paletteRepository

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var nameA by mutableStateOf("作品 A")
        private set
    var nameB by mutableStateOf("作品 B")
        private set

    var palette by mutableStateOf(paletteRepo.default())
        private set

    private var gridA: BeadGrid? = null
    private var gridB: BeadGrid? = null

    /** true = B 在上层（默认：先拖的那个在下，被拖到的那个在上）。 */
    var secondOnTop by mutableStateOf(true)
        private set

    var opacity by mutableStateOf(1f)
        private set

    /** 蒙版（输出坐标，1 = 露出上层）。 */
    private var mask: FloatArray = FloatArray(1) { 1f }

    var outWidth by mutableIntStateOf(1)
        private set
    var outHeight by mutableIntStateOf(1)
        private set

    /** 每次参数变化 +1，界面据此重算合成结果。 */
    var version by mutableIntStateOf(0)
        private set

    var showMask by mutableStateOf(false)
        private set

    /** 蒙版画笔：true = 擦除（露出下层），false = 恢复。 */
    var maskErase by mutableStateOf(true)
        private set

    fun toggleShowMask() { showMask = !showMask; version++ }
    fun updateMaskErase(erase: Boolean) { maskErase = erase }

    fun load() {
        if (!loading) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val a = projectRepo.load(idA, paletteRepo)
                val b = projectRepo.load(idB, paletteRepo)
                if (a == null || b == null) null else Triple(a, b, Unit)
            }
            if (result == null) {
                error = "作品读取失败"
                loading = false
                return@launch
            }
            val (pa, pb) = result.first to result.second
            nameA = pa.first.name
            nameB = pb.first.name
            // 关键：**合并两套色卡**，而不是把上层塞进下层的色卡。
            // 之前用下层色卡做容器，下层若只有 3 个颜色（比如小花的粉黄），
            // 上层那张紫色文字图的紫/品红就会被"就近"吃成粉色——颜色直接丢失。
            val merged = LayerMixer.mergePalettes(pa.second, pb.second)
            palette = merged
            // A 的颜色排在合并色卡最前面，索引不变；B 需要重新映射进来
            gridA = LayerMixer.remap(pa.first.grid, pa.second, merged)
            gridB = LayerMixer.remap(pb.first.grid, pb.second, merged)
            outWidth = max(pa.first.grid.width, pb.first.grid.width)
            outHeight = max(pa.first.grid.height, pb.first.grid.height)
            mask = LayerMixer.freshMask(outWidth, outHeight)
            loading = false
            version++
        }
    }

    /** 上层 / 下层互换。 */
    fun swapLayers() {
        secondOnTop = !secondOnTop
        version++
    }

    fun updateOpacity(v: Float) {
        opacity = v.coerceIn(0f, 1f)
        version++
    }

    /** 用画笔涂蒙版（[radius] 为格数半径，1 表示只改一格）。 */
    fun paintMask(c: Int, r: Int, radius: Int = 1) {
        val value = if (maskErase) 0f else 1f
        for (rr in (r - radius + 1)..(r + radius - 1)) {
            for (cc in (c - radius + 1)..(c + radius - 1)) {
                if (cc in 0 until outWidth && rr in 0 until outHeight) {
                    mask[rr * outWidth + cc] = value
                }
            }
        }
        version++
    }

    fun invertMask() {
        LayerMixer.invertMask(mask)
        version++
    }

    fun resetMask() {
        mask = LayerMixer.freshMask(outWidth, outHeight)
        version++
    }

    /** 当前合成结果。 */
    fun compose(): BeadGrid? {
        val a = gridA ?: return null
        val b = gridB ?: return null
        val base = if (secondOnTop) a else b
        val top = if (secondOnTop) b else a
        return LayerMixer.compose(
            base = base,
            top = top,
            mask = mask.copyOf(),
            opacity = opacity,
        )
    }

    /** 蒙版快照（界面画蒙版视图用）。 */
    fun maskSnapshot(): FloatArray = mask.copyOf()

    /** 存成新作品，返回工程 id。 */
    fun saveAsProject(name: String, onDone: (String?) -> Unit) {
        val grid = compose()
        if (grid == null) {
            onDone(null)
            return
        }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                projectRepo.createFromGrid(name.ifBlank { "$nameA × $nameB" }, grid, palette)
            }
            onDone(id)
        }
    }

    class Factory(
        private val app: Application,
        private val idA: String,
        private val idB: String,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            MixViewModel(app, idA, idB) as T
    }
}
