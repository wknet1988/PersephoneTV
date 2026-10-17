package com.persephone.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** 单个源的检索结果 */
data class ProbeResult(
    val entry: SourceEntry,
    val ok: Boolean,
    val channels: List<Channel> = emptyList(),
    val latencyMs: Long = 0L,
    val error: String? = null
)

/**
 * 联网检索直播源。
 *
 * 只做「拉取公开 M3U 索引 → 解析 → 去重 → 测速」，
 * 不抓取任何需要授权或来路不明的站点。
 */
object SourceFinder {

    private const val UA = "PersephoneTV/1.0 (Android TV)"

    /** 进程内缓存：切换页面回来不用重新联网 */
    private val cache = HashMap<String, ProbeResult>()

    fun cached(entryId: String): ProbeResult? = cache[entryId]

    fun remember(result: ProbeResult) {
        cache[result.entry.id] = result
    }

    fun forgetAll() = cache.clear()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** 检索一个源：下载 + 解析 + 记录耗时 */
    suspend fun probe(entry: SourceEntry): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        runCatching {
            val text = fetch(entry.url)
            val channels = M3uParser.parse(text)
            val cost = System.currentTimeMillis() - start
            if (channels.isEmpty()) {
                ProbeResult(entry, ok = false, latencyMs = cost, error = "解析到 0 个频道")
            } else {
                // 顺手排好序，找源页拉下来就是音序
                ProbeResult(entry, ok = true, channels = M3uParser.sorted(dedupe(channels)), latencyMs = cost)
            }
        }.getOrElse { e ->
            ProbeResult(
                entry = entry,
                ok = false,
                latencyMs = System.currentTimeMillis() - start,
                error = friendlyError(e)
            )
        }
    }

    /** 并发检索全部源，每完成一个就回调一次，UI 可以逐个刷新 */
    suspend fun probeAll(
        entries: List<SourceEntry>,
        onResult: suspend (ProbeResult) -> Unit
    ) = coroutineScope {
        entries.forEach { entry ->
            launch { onResult(probe(entry)) }
        }
    }

    /** 合并多个源的结果，按播放地址去重，保留先出现的名称 */
    fun merge(results: List<ProbeResult>): List<Channel> {
        val seen = HashSet<String>()
        val out = ArrayList<Channel>()
        results.filter { it.ok }.forEach { r ->
            r.channels.forEach { ch ->
                if (seen.add(normalize(ch.url))) out += ch
            }
        }
        return out
    }

    /** 按关键词过滤频道（中英文名或分组命中都算，忽略大小写） */
    fun search(channels: List<Channel>, keyword: String): List<Channel> {
        val k = keyword.trim()
        if (k.isEmpty()) return channels
        return channels.filter {
            it.name.contains(k, ignoreCase = true) ||
                it.nameZh?.contains(k, ignoreCase = true) == true ||
                it.nameEn?.contains(k, ignoreCase = true) == true ||
                it.group.contains(k, ignoreCase = true)
        }
    }

    private fun dedupe(channels: List<Channel>): List<Channel> {
        val seen = HashSet<String>()
        return channels.filter { seen.add(normalize(it.url)) }
    }

    private fun normalize(url: String): String =
        url.trim().lowercase().substringBefore('#')

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("响应内容为空")
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is UnknownHostException -> "域名解析失败，可能网络不通"
        is SocketTimeoutException -> "连接超时"
        else -> e.message ?: e.javaClass.simpleName
    }
}
