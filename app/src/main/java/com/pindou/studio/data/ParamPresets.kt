package com.pindou.studio.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 参数预设：把一整套 DIY 参数存下来，下次一键套用。
 *
 * 按 scope 分开存（text = 字体 DIY，image = 以图生图），互不干扰。
 */
object ParamPresets {

    @Serializable
    data class Stored(
        val id: String,
        val name: String,
        /** 参数本体，由各面板自己序列化 */
        val payload: String,
    )

    private const val PREFS = "param_presets"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lastKey(scope: String) = "last:" + scope

    /**
     * 上次用过的参数（自动保存，用户不用手动存预设）。
     *
     * 「以图生图」和「字体 DIY」每次调完参数都写一份，下次打开面板直接沿用，
     * 省得每次都重新配一遍「合并相近色 / 阈值 / 字高」这些。
     */
    fun lastPayload(context: Context, scope: String): String? =
        prefs(context).getString(lastKey(scope), null)

    fun saveLastPayload(context: Context, scope: String, payload: String) {
        prefs(context).edit().putString(lastKey(scope), payload).apply()
    }

    fun list(context: Context, scope: String): List<Stored> {
        val raw = prefs(context).getString(scope, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Stored.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, scope: String, name: String, payload: String): List<Stored> {
        val current = list(context, scope).filterNot { it.name == name }
        val stored = Stored(
            id = "p" + System.currentTimeMillis(),
            name = name,
            payload = payload,
        )
        val next = current + stored
        prefs(context).edit()
            .putString(
                scope,
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Stored.serializer()), next),
            )
            .apply()
        return next
    }

    fun delete(context: Context, scope: String, id: String): List<Stored> {
        val next = list(context, scope).filterNot { it.id == id }
        prefs(context).edit()
            .putString(
                scope,
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Stored.serializer()), next),
            )
            .apply()
        return next
    }
}
