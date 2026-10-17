package com.persephone.tv.data

/**
 * 一个「源」= 一个播放列表地址。
 *
 * 三种形态：
 *   `encasset:` 开头 → APK 里加密存放的内置列表，读出来先解混淆（见 [EncodedSource]）
 *   `asset:`   开头 → APK 里明文存放的内置列表
 *   `http(s)://`    → 联网拉取
 *
 * 联网源只收录公开、开源、可自由访问的频道索引（iptv-org 这类公开列表），
 * 不抓取任何需要授权或来路不明的站点。
 */
data class SourceEntry(
    val id: String,
    val name: String,
    val url: String,
    val note: String = "",
    val builtin: Boolean = true
) {
    /** 内置资源，不用联网、也不用测速 */
    val isAsset: Boolean
        get() = url.startsWith(ASSET_PREFIX, ignoreCase = true) ||
            url.startsWith(ENC_ASSET_PREFIX, ignoreCase = true)

    /** 内置资源是否是加密存放的 */
    val encoded: Boolean
        get() = url.startsWith(ENC_ASSET_PREFIX, ignoreCase = true)

    /** 内置资源在 assets 里的文件名 */
    val assetName: String get() = url.substring(url.indexOf(':') + 1)

    companion object {
        const val ASSET_PREFIX = "asset:"
        const val ENC_ASSET_PREFIX = "encasset:"
    }
}

object SourceCatalog {

    /** 内置候选源：内置精选 + 公开频道索引，可按需增删 */
    val builtin: List<SourceEntry> = listOf(
        SourceEntry(
            id = "builtin-cn",
            name = "内置精选 · 中国频道",
            url = "${SourceEntry.ENC_ASSET_PREFIX}builtin_cn.m3u.enc",
            note = "随 APK 内置，离线可用，无需联网"
        ),
        SourceEntry(
            id = "cn",
            name = "中文频道 · 公开列表",
            url = "https://iptv-org.github.io/iptv/countries/cn.m3u",
            note = "iptv-org 收录的公开华语频道，体积小、起播快"
        ),
        SourceEntry(
            id = "news",
            name = "新闻频道 · 公开列表",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u",
            note = "全球公开新闻频道"
        ),
        SourceEntry(
            id = "sports",
            name = "体育频道 · 公开列表",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
            note = "全球公开体育频道"
        ),
        SourceEntry(
            id = "documentary",
            name = "纪录片 · 公开列表",
            url = "https://iptv-org.github.io/iptv/categories/documentary.m3u",
            note = "纪录片类公开频道"
        ),
        SourceEntry(
            id = "music",
            name = "音乐频道 · 公开列表",
            url = "https://iptv-org.github.io/iptv/categories/music.m3u",
            note = "音乐类公开频道"
        ),
        SourceEntry(
            id = "index",
            name = "全量索引 · 公开列表",
            url = "https://iptv-org.github.io/iptv/index.m3u",
            note = "上万个频道，拉取较慢，建议配合关键词过滤"
        )
    )

    /**
     * 把设置页里用户手填的自定义源（每行一个，支持 `名称|地址` 或只写地址）解析成条目。
     */
    fun withCustom(raw: String): List<SourceEntry> {
        val result = builtin.toMutableList()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEachIndexed { index, line ->
                val hasName = line.contains('|')
                val name = if (hasName) line.substringBefore('|').trim() else ""
                val url = if (hasName) line.substringAfter('|').trim() else line
                if (!url.startsWith("http", ignoreCase = true)) return@forEachIndexed
                result += SourceEntry(
                    id = "custom-$index-$url",
                    name = name.ifBlank { "自定义源 ${index + 1}" },
                    url = url,
                    note = "自定义",
                    builtin = false
                )
            }
        return result
    }
}
