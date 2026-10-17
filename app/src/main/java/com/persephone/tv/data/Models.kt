package com.persephone.tv.data

/** 直播频道 */
data class Channel(
    /** 源里给的原始名，作为兜底显示 */
    val name: String,
    val group: String,
    val url: String,
    val logo: String? = null,
    /** 中文名，由 ChannelNames 解析得到，可能为空 */
    val nameZh: String? = null,
    /** 英文名，同上 */
    val nameEn: String? = null
) {
    /** 主标题：有中文名就用中文，否则用原文 */
    val title: String
        get() = nameZh?.takeIf { it.isNotBlank() } ?: name

    /** 副标题：英文名。重复的不显示，免得「CCTV-1 综合」下面再写一遍「CCTV-1」 */
    val subtitle: String?
        get() {
            val en = nameEn?.takeIf { it.isNotBlank() } ?: return null
            if (en.equals(title, ignoreCase = true)) return null
            if (title.contains(en, ignoreCase = true)) return null
            return en
        }

    /** 排序用的名字 */
    val sortKey: String
        get() = title
}

/** 剧集 / 分集 */
data class Episode(
    val name: String,
    val url: String
)

/** 影视条目 */
data class VodItem(
    val title: String,
    val category: String,
    val year: String? = null,
    val rating: String? = null,
    val poster: String? = null,
    val desc: String? = null,
    val episodes: List<Episode> = emptyList()
) {
    val firstUrl: String? get() = episodes.firstOrNull()?.url
}

/** 分类（一个分类下多个条目） */
data class VodCategory(
    val name: String,
    val items: List<VodItem>
)

/** 播放请求：所有播放都收敛成这个对象 */
data class PlayTarget(
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val isLive: Boolean = false
)

/** 统一的加载状态 */
sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}
