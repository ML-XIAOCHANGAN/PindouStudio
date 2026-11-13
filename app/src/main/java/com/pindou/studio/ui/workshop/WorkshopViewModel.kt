package com.pindou.studio.ui.workshop

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.pindou.studio.PindouApp
import com.pindou.studio.core.BeadGrid
import com.pindou.studio.core.ColorCombo
import com.pindou.studio.core.ColorMath
import com.pindou.studio.core.ColorCombos
import com.pindou.studio.core.Palette
import com.pindou.studio.core.PaletteColor
import com.pindou.studio.core.PatternTemplate
import com.pindou.studio.core.PatternTemplates
import com.pindou.studio.core.Project
import com.pindou.studio.core.WorkshopTip
import com.pindou.studio.core.WorkshopTips
import com.pindou.studio.core.nearestIndex
import com.pindou.studio.data.ProjectRepository

/** 创艺工坊：模板 / 配色 / 技法。 */
class WorkshopViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = PindouApp.from(app)
    private val projectRepo = appRef.projectRepository
    private val paletteRepo = appRef.paletteRepository
    private val settings = appRef.settings

    val templates: List<PatternTemplate> = PatternTemplates.all
    val combos: List<ColorCombo> = ColorCombos.all
    val tips: List<WorkshopTip> = WorkshopTips.all

    var message by mutableStateOf<String?>(null)

    private val previewCache = HashMap<String, ImageBitmap>()

    /** 当前用于预览与落图的色卡。 */
    fun workingPalette(): Palette =
        paletteRepo.byId(settings.lastPaletteId) ?: paletteRepo.default()

    /** 模板预览图（缓存）。 */
    fun templatePreview(template: PatternTemplate): ImageBitmap? {
        previewCache[template.id]?.let { return it }
        return try {
            val palette = workingPalette()
            val grid = template.toGrid(palette)
            val bmp = ProjectRepository.renderThumbnail(grid, palette, 360)
            val image = bmp.asImageBitmap()
            previewCache[template.id] = image
            image
        } catch (e: Exception) {
            null
        }
    }

    /** 配色在色卡里的就近色号。 */
    fun comboColorCodes(combo: ColorCombo): List<Pair<PaletteColor, String>> {
        val palette = workingPalette()
        return combo.hexes.mapNotNull { hex ->
            val index = palette.nearestIndex(ColorMath.parseHex(hex))
            palette.colorAt(index)?.let { it to hex }
        }
    }

    /** 用模板新建作品，返回工程 id。 */
    fun createFromTemplate(template: PatternTemplate): String {
        val palette = workingPalette()
        val id = projectRepo.createFromGrid(template.name, template.toGrid(palette), palette)
        settings.lastPaletteId = palette.id
        message = "已按「${template.name}」新建作品"
        return id
    }

    /** 用「字体 DIY」/模板编辑生成的网格建工程。 */
    fun createFromGrid(name: String, grid: BeadGrid, palette: Palette): String {
        val id = projectRepo.createFromGrid(name, grid, palette)
        settings.lastPaletteId = palette.id
        message = "已生成作品「$name」"
        return id
    }

    /** 把一组配色存成限定色卡。 */
    fun saveComboAsPalette(combo: ColorCombo): Palette {
        val base = workingPalette()
        val picked = LinkedHashMap<String, PaletteColor>()
        for (hex in combo.hexes) {
            val index = base.nearestIndex(ColorMath.parseHex(hex))
            val color = base.colorAt(index) ?: continue
            picked.putIfAbsent(color.code, color)
        }
        val palette = Palette(
            id = paletteRepo.newUserId(),
            name = combo.name,
            colors = picked.values.toList(),
            builtin = false,
        )
        paletteRepo.saveUser(palette)
        message = "已把「${combo.name}」存为限定色卡（${palette.size} 色）"
        return palette
    }

    /** 用一组配色新建限定色卡的作品。 */
    fun createWithCombo(combo: ColorCombo, size: Int = 32): String {
        val palette = saveComboAsPalette(combo)
        val id = projectRepo.newId()
        val now = System.currentTimeMillis()
        val project = Project(
            id = id,
            name = combo.name,
            createdAt = now,
            updatedAt = now,
            paletteId = palette.id,
            paletteName = palette.name,
            grid = BeadGrid(size, size),
        )
        projectRepo.save(project, palette)
        settings.lastPaletteId = palette.id
        return id
    }
}
