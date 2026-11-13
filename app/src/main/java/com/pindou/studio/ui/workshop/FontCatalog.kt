package com.pindou.studio.ui.workshop

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.LinkedHashMap

/**
 * 字体清单与加载。
 *
 * 字体分成四层，选择器里能一眼区分：
 * 1. **内置**：随包放在 `assets/fonts`，清单由同目录的 `catalog.json` 描述（名字 / 分类 / 是否含中文）
 * 2. **完整字库**：可选构建（`-PpindouFullFonts=true`）会把 `fonts-full/fonts` 一起并进 assets
 * 3. **导入**：用户从手机里挑的 .ttf/.otf，复制到 `files/fonts` 长期可用
 * 4. **系统**：黑体 / 宋体 / 等宽，不占包体
 *
 * 字体文件解析后很占内存（一个中文字体可能几 MB），所以 [loadCached] 用 **LRU 只留 6 个**，
 * 选择器里滚动时靠惰性加载，不会把上百个字体全解到内存里。
 */
object FontCatalog {

    @Serializable
    private data class CatalogDto(val version: Int = 1, val fonts: List<FontDto> = emptyList())

    @Serializable
    private data class FontDto(
        val file: String,
        val name: String,
        val category: String = "其它",
        val script: String = "zh",
        val bundled: Boolean = true,
        val kb: Int = 0,
    )

    data class Entry(
        val key: String,
        val name: String,
        /** 手写 / 黑体 / 宋体 / 圆体 / 像素 / 装饰 / 英文 / 系统 / 导入 */
        val category: String,
        /** 是否含中文字形（英文装饰字体只覆盖拉丁，选中时会缺字） */
        val hasChinese: Boolean,
        val assetPath: String? = null,
        val systemFamily: String? = null,
        val filePath: String? = null,
        val sizeKb: Int = 0,
    ) {
        val isSystem: Boolean get() = systemFamily != null
        val isUser: Boolean get() = filePath != null
        /** 选择器里的副标题 */
        val subtitle: String
            get() = when {
                isSystem -> "系统字体 · 不占包体"
                isUser -> "我导入的 · " + formatSize(sizeKb)
                hasChinese -> formatSize(sizeKb) + " · 全中文"
                else -> formatSize(sizeKb) + " · 仅英文"
            }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val prefsName = "font_catalog"
    private val recentKey = "recent"

    @Volatile
    private var builtinCache: List<Entry>? = null

    private val systemEntries = listOf(
        Entry("sys_sans", "系统黑体", "系统", hasChinese = true, systemFamily = "sans-serif"),
        Entry("sys_serif", "系统宋体", "系统", hasChinese = true, systemFamily = "serif"),
        Entry("sys_mono", "系统等宽", "系统", hasChinese = true, systemFamily = "monospace"),
    )

    // ---------------------------------------------------------------- 清单

    /** 内置字体（按 catalog.json，且只保留 assets 里真实存在的文件）。 */
    fun builtin(context: Context): List<Entry> {
        builtinCache?.let { return it }
        val present = runCatching {
            context.assets.list("fonts")?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())

        val list = ArrayList<Entry>()
        val catalog = runCatching {
            context.assets.open("fonts/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { raw ->
            runCatching { json.decodeFromString(CatalogDto.serializer(), raw) }.getOrNull()
        }

        if (catalog != null && catalog.fonts.isNotEmpty()) {
            for (f in catalog.fonts) {
                if (f.file !in present) continue
                list.add(
                    Entry(
                        key = "asset_" + f.file,
                        name = f.name,
                        category = f.category,
                        hasChinese = f.script == "zh",
                        assetPath = "fonts/" + f.file,
                        sizeKb = f.kb,
                    )
                )
            }
        } else {
            // 没有清单时退化为扫描目录（老版本兼容）
            present.sorted().forEach { file ->
                val lower = file.lowercase()
                if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) return@forEach
                list.add(
                    Entry(
                        key = "asset_$file",
                        name = file.substringBeforeLast('.'),
                        category = "其它",
                        hasChinese = true,
                        assetPath = "fonts/$file",
                    )
                )
            }
        }
        builtinCache = list
        return list
    }

    /** 用户导入的字体（files/fonts）。 */
    fun user(context: Context): List<Entry> {
        val dir = userDir(context)
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && (it.name.endsWith(".ttf", true) || it.name.endsWith(".otf", true)) }
            .sortedBy { it.name }
            .map { f ->
                Entry(
                    key = "user_" + f.name,
                    name = f.nameWithoutExtension,
                    category = "导入",
                    hasChinese = true,
                    filePath = f.absolutePath,
                    sizeKb = (f.length() / 1024).toInt(),
                )
            }
    }

    fun all(context: Context): List<Entry> = builtin(context) + user(context) + systemEntries

    /** 全部类别（按选择器里的展示顺序）。 */
    fun categories(list: List<Entry>): List<String> {
        val order = listOf("手写", "黑体", "宋体", "圆体", "像素", "装饰", "英文", "导入", "系统", "其它")
        val present = list.map { it.category }.toSet()
        return order.filter { it in present }
    }

    /** 搜索：名字、类别、拼音首字母都能命中（拼音靠用户导入时的名字）。 */
    fun filter(list: List<Entry>, query: String, category: String?): List<Entry> {
        val q = query.trim().lowercase()
        return list.filter { e ->
            (category == null || e.category == category) &&
                (q.isEmpty() || e.name.lowercase().contains(q) || e.category.lowercase().contains(q) ||
                    (e.hasChinese && q == "中文") || (!e.hasChinese && (q == "英文" || q == "english")))
        }
    }

    /** 最近用过的字体 key（最多 5 个，新的在前）。 */
    fun recentKeys(context: Context): List<String> =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(recentKey, null)
            ?.split("|")?.filter { it.isNotBlank() }
            ?: emptyList()

    fun markUsed(context: Context, key: String) {
        val current = recentKeys(context).filterNot { it == key }
        val next = (listOf(key) + current).take(5)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().putString(recentKey, next.joinToString("|")).apply()
    }

    // ---------------------------------------------------------------- 加载

    fun load(context: Context, entry: Entry): Typeface {
        entry.assetPath?.let { path ->
            runCatching { Typeface.createFromAsset(context.assets, path) }.getOrNull()?.let { return it }
        }
        entry.filePath?.let { path ->
            runCatching { Typeface.createFromFile(File(path)) }.getOrNull()?.let { return it }
        }
        entry.systemFamily?.let { family ->
            return runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrDefault(Typeface.DEFAULT)
        }
        return Typeface.DEFAULT
    }

    /** LRU 缓存：最多留 6 个已解析字体，选中别的会自动淘汰最久未用的。 */
    private val cache = object : LinkedHashMap<String, Typeface>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Typeface>?): Boolean =
            size > 6
    }

    fun loadCached(context: Context, entry: Entry): Typeface = synchronized(cache) {
        cache[entry.key] ?: load(context, entry).also { cache[entry.key] = it }
    }

    /** 导入一个字体文件（复制到 files/fonts，长期可用）。 */
    fun importFont(context: Context, source: File, displayName: String? = null): Entry? {
        val ext = if (source.name.endsWith(".otf", true)) ".otf" else ".ttf"
        val name = (displayName ?: source.nameWithoutExtension)
            .replace(Regex("[^\\p{L}\\p{N}_.-]"), "_")
            .take(40)
        val target = File(userDir(context), name + ext)
        return runCatching {
            source.copyTo(target, overwrite = true)
            user(context).firstOrNull { it.key == "user_" + target.name }
        }.onFailure { Log.w("FontCatalog", "导入字体失败", it) }.getOrNull()
    }

    fun deleteUserFont(context: Context, entry: Entry): Boolean {
        val path = entry.filePath ?: return false
        return runCatching { File(path).delete() }.getOrDefault(false)
    }

    /** 用户导入的字体目录（也会被「字体包」导入复用）。 */
    fun userDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    private fun formatSize(kb: Int): String =
        if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else "$kb KB"
}
