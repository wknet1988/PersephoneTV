package com.persephone.tv.data

/**
 * 一个「源」= 一个可联网拉取的 M3U 播放列表地址。
 * 内置源只收录公开、开源、可自由访问的频道索引（iptv-org 这类公开列表），
 * 不内置任何需要授权或来路不明的聚合站。
 */
data class SourceEntry(
    val id: String,
    val name: String,
    val url: String,
    val note: String = "",
    val builtin: Boolean = true
)

object SourceCatalog {

    /** 内置候选源：公开频道索引，可按需增删 */
    val builtin: List<SourceEntry> = listOf(
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
