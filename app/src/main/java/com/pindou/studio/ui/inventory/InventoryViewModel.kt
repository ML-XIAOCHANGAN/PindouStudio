package com.pindou.studio.ui.inventory

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.pindou.studio.PindouApp
import com.pindou.studio.core.Palette

/** 豆仓：色号库存录入与查询。 */
class InventoryViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = PindouApp.from(app)
    private val inventory = appRef.inventoryRepository
    private val paletteRepo = appRef.paletteRepository
    private val settings = appRef.settings

    val palettes: List<Palette> = paletteRepo.all()

    var paletteId by mutableStateOf(
        settings.lastPaletteId.takeIf { id -> palettes.any { it.id == id } }
            ?: palettes.firstOrNull()?.id.orEmpty()
    )
        private set

    var query by mutableStateOf("")
    var onlyOwned by mutableStateOf(false)

    /** 库存快照，变更后自增触发重组。 */
    var stockTick by mutableStateOf(0)
        private set

    fun palette(): Palette = palettes.firstOrNull { it.id == paletteId } ?: palettes.firstOrNull()
        ?: Palette("empty", "空色卡", emptyList())

    fun selectPalette(id: String) {
        paletteId = id
        settings.lastPaletteId = id
    }

    fun countOf(code: String): Int = inventory.countOf(code)

    fun setCount(code: String, count: Int) {
        inventory.setCount(code, count)
        stockTick++
    }

    fun addCount(code: String, delta: Int) {
        inventory.addCount(code, delta)
        stockTick++
    }

    fun clearAll() {
        inventory.clear()
        stockTick++
    }

    fun summary(): Pair<Int, Int> {
        @Suppress("UNUSED_EXPRESSION") stockTick
        return inventory.summary()
    }

    /** 当前色卡下、按筛选条件列出的色号。 */
    fun visibleColors(): List<androidx.compose.ui.graphics.Color> = emptyList()
}
