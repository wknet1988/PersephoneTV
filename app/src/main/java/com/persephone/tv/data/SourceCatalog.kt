package com.persephone.tv.data

/**
 * 一个「源」= 一个播放列表地址。
 *
 * 三种形态：
 *   `encasset:` 开头 → APK 里随包附带的频道清单（加密存放），读出来先解混淆（见 [EncodedSource]）
 *   `asset:`   开头 → APK 里随包附带的资源（明文）
 *   `http(s)://`    → 联网拉取
 *
 * 注意：`asset:` 系只是「播放列表不靠联网拉」，**播放流本身仍然需要联网**。
 *
 * 联网源只收录公开、开源、可自由访问的频道索引（iptv-org 这类公开列表），
 * 不抓取任何需要授权或来路不明的站点。
 */
data class SourceEntry(
    val id: String,
    val name: String,
    val url: String,
    val note: String = "",
    /** 是不是本程序自带（相对用户在设置里手填的自定义源而言） */
    val bundled: Boolean = true
) {
    /** 包内资源，不用联网拉播放列表、也不用测速 */
    val isAsset: Boolean
        get() = url.startsWith(ASSET_PREFIX, ignoreCase = true) ||
            url.startsWith(ENC_ASSET_PREFIX, ignoreCase = true)

    /** 包内资源是否是加密存放的 */
    val encoded: Boolean
        get() = url.startsWith(ENC_ASSET_PREFIX, ignoreCase = true)

    /** 包内资源在 assets 里的文件名 */
    val assetName: String get() = url.substring(url.indexOf(':') + 1)

    companion object {
        const val ASSET_PREFIX = "asset:"
        const val ENC_ASSET_PREFIX = "encasset:"
    }
}

object SourceCatalog {

    /** 候选源：随包附带的频道清单 + 公开频道索引，可按需增删 */
    val bundled: List<SourceEntry> = listOf(
        SourceEntry(
            id = "bundled-cn",
            name = "附带频道清单 · 中国频道",
            url = "${SourceEntry.ENC_ASSET_PREFIX}builtin_cn.m3u.enc",
            note = "90 条第三方频道地址，清单随 APK 附带；播放仍需联网"
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
        val result = bundled.toMutableList()
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
                    bundled = false
                )
            }
        return result
    }
}
