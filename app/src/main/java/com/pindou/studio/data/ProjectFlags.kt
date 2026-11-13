package com.pindou.studio.data

import android.content.Context

/**
 * 工程的轻量标记，单独存在偏好里，不污染工程 JSON。
 *
 * - 是否导出过图纸（列表上用来打「已导出图纸」标签）
 * - 是否收藏（首页「收藏」筛选，相当于轻量收藏夹）
 */
object ProjectFlags {

    private const val PREFS = "project_flags"
    private const val KEY_EXPORTED = "exported"
    private const val KEY_FAVORITE = "favorite"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 已导出

    fun markExported(context: Context, projectId: String) {
        addTo(context, KEY_EXPORTED, projectId)
    }

    fun exportedIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXPORTED, emptySet()).orEmpty().toSet()

    /** 删除作品时顺手清掉标记。 */
    fun forget(context: Context, projectId: String) {
        removeFrom(context, KEY_EXPORTED, projectId)
        removeFrom(context, KEY_FAVORITE, projectId)
    }

    // ---------------------------------------------------------------- 收藏

    fun favoriteIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FAVORITE, emptySet()).orEmpty().toSet()

    /** 切换收藏状态，返回切换后是否已收藏。 */
    fun toggleFavorite(context: Context, projectId: String): Boolean {
        val current = favoriteIds(context)
        return if (projectId in current) {
            removeFrom(context, KEY_FAVORITE, projectId)
            false
        } else {
            addTo(context, KEY_FAVORITE, projectId)
            true
        }
    }

    // ---------------------------------------------------------------- 内部

    private fun addTo(context: Context, key: String, projectId: String) {
        val current = prefs(context).getStringSet(key, emptySet()).orEmpty()
        prefs(context).edit().putStringSet(key, current + projectId).apply()
    }

    private fun removeFrom(context: Context, key: String, projectId: String) {
        val current = prefs(context).getStringSet(key, emptySet()).orEmpty()
        if (projectId !in current) return
        // 必须写入一份新的 Set，不能就地改 SharedPreferences 返回的对象
        prefs(context).edit().putStringSet(key, current - projectId).apply()
    }
}
