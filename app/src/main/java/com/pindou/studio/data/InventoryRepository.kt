package com.pindou.studio.data

import android.content.Context
import com.pindou.studio.core.Palette
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 豆仓：记录「色号 → 手头数量」。
 *
 * 键统一用大写色号（例如 A1、B25），跨色卡同名色号视为同一种豆子，
 * 这与玩家实际收纳方式一致。
 */
class InventoryRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File = File(context.filesDir, "inventory.json")
    private val serializer = MapSerializer(String.serializer(), Int.serializer())
    private var cache: MutableMap<String, Int>? = null

    fun all(): Map<String, Int> {
        cache?.let { return it }
        val loaded = try {
            if (file.exists()) {
                json.decodeFromString(serializer, file.readText())
                    .mapKeys { it.key.uppercase() }
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
        } catch (e: Exception) {
            mutableMapOf()
        }
        cache = loaded
        return loaded
    }

    fun countOf(code: String): Int = all()[code.uppercase()] ?: 0

    fun setCount(code: String, count: Int) {
        val map = all().toMutableMap()
        val key = code.uppercase()
        if (count <= 0) map.remove(key) else map[key] = count
        cache = map
        persist()
    }

    fun addCount(code: String, delta: Int) = setCount(code, countOf(code) + delta)

    fun remove(code: String) = setCount(code, 0)

    fun clear() {
        cache = mutableMapOf()
        persist()
    }

    /** 已录入多少种色号 / 共多少颗。 */
    fun summary(): Pair<Int, Int> {
        val map = all()
        return map.size to map.values.sum()
    }

    private fun persist() {
        try {
            file.writeText(json.encodeToString(serializer, cache ?: emptyMap()))
        } catch (_: Exception) {
        }
    }

    /** 一份图纸的备料结果。 */
    data class Need(
        val code: String,
        val hex: String,
        val required: Int,
        val owned: Int,
    ) {
        val missing: Int get() = (required - owned).coerceAtLeast(0)
        val enough: Boolean get() = owned >= required
        /** 拼完这图纸后剩余。 */
        val left: Int get() = owned - required
    }

    /**
     * 用当前图纸用量对比库存。
     * @param usage 色号 → 需要颗数
     */
    fun compare(palette: Palette, usage: Map<String, Int>): List<Need> {
        val inventory = all()
        return usage.entries
            .sortedBy { palette.indexOf(it.key).takeIf { i -> i > 0 } ?: Int.MAX_VALUE }
            .map { (code, required) ->
                val color = palette.colorByCode[code.uppercase()]
                Need(
                    code = code,
                    hex = color?.hex ?: "#000000",
                    required = required,
                    owned = inventory[code.uppercase()] ?: 0,
                )
            }
    }
}
