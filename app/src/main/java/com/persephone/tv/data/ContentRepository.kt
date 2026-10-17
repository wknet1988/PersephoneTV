package com.persephone.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 内容加载入口。
 * 地址为空 → 读 assets 内置演示数据；
 * `encasset:` → 读 assets 里加密存放的内置列表（先解混淆）；
 * `asset:` → 读 assets 里指定的内置列表；
 * http(s):// → 网络拉取；file:// 或绝对路径 → 读本地文件。
 */
object ContentRepository {

    private const val UA = "PersephoneTV/1.0 (Android TV)"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun loadLive(context: Context, url: String): Result<List<Channel>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = sourceText(context, url, "demo_live.m3u")
                val channels = M3uParser.parse(text)
                if (channels.isEmpty()) throw IOException("播放列表解析结果为空")
                channels
            }
        }

    suspend fun loadCatalog(context: Context, url: String): Result<List<VodCategory>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = sourceText(context, url, "demo_catalog.json")
                CatalogParser.parse(text)
            }
        }

    private fun sourceText(context: Context, url: String, assetName: String): String {
        if (url.isBlank()) {
            return context.assets.open(assetName).bufferedReader().use { it.readText() }
        }
        if (url.startsWith(SourceEntry.ENC_ASSET_PREFIX, ignoreCase = true)) {
            val name = url.substring(SourceEntry.ENC_ASSET_PREFIX.length)
            return EncodedSource.readAsset(context, name, encoded = true)
        }
        if (url.startsWith(SourceEntry.ASSET_PREFIX, ignoreCase = true)) {
            val name = url.substring(SourceEntry.ASSET_PREFIX.length)
            return context.assets.open(name).bufferedReader().use { it.readText() }
        }
        if (url.startsWith("file://", ignoreCase = true)) {
            val path = url.removePrefix("file://")
            return File(path).readText()
        }
        if (url.startsWith("/")) {
            return File(url).readText()
        }
        return fetch(url)
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("响应内容为空")
        }
    }
}
