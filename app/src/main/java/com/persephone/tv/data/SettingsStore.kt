package com.persephone.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "persephone_settings")

/** 用户配置：直播源 / 影视目录 / 自定义源池 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val LIVE = stringPreferencesKey("live_m3u_url")
        val CATALOG = stringPreferencesKey("catalog_json_url")
        val CUSTOM = stringPreferencesKey("custom_source_list")
    }

    /** 没设置过时用 APK 内置的精选频道，开箱即用 */
    val liveUrl: Flow<String> = context.settingsDataStore.data
        .map { it[Keys.LIVE] ?: DEFAULT_LIVE }
    val catalogUrl: Flow<String> = context.settingsDataStore.data.map { it[Keys.CATALOG].orEmpty() }

    /** 每行一个自定义源，格式 `名称|地址` 或只写地址 */
    val customSources: Flow<String> = context.settingsDataStore.data.map { it[Keys.CUSTOM].orEmpty() }

    suspend fun save(live: String, catalog: String, custom: String) {
        context.settingsDataStore.edit {
            it[Keys.LIVE] = live.trim()
            it[Keys.CATALOG] = catalog.trim()
            it[Keys.CUSTOM] = custom.trim()
        }
    }

    /** 「找源」页导入：只改直播源，不动其它配置 */
    suspend fun setLiveSource(url: String) {
        context.settingsDataStore.edit { it[Keys.LIVE] = url.trim() }
    }

    suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }

    companion object {
        /** 首次启动 / 恢复默认时的直播源：内置精选频道（加密存放），离线可用 */
        const val DEFAULT_LIVE = "encasset:builtin_cn.m3u.enc"
    }
}
