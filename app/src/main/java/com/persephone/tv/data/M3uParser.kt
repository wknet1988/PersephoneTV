package com.persephone.tv.data

/**
 * 极简 M3U / M3U8 播放列表解析器。
 *
 * 支持 #EXTINF 行中的 tvg-id、tvg-name、tvg-logo、group-title 属性；其余属性忽略。
 * 解析时会顺手调 [ChannelNames] 补上中英文名，让列表能中英并列显示。
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val result = ArrayList<Channel>()
        var name: String? = null
        var group: String? = null
        var logo: String? = null
        var tvgId: String? = null
        var tvgName: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = ATTR_REGEX.findAll(line)
                    .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                val comma = line.lastIndexOf(',')
                name = if (comma >= 0) line.substring(comma + 1).trim() else null
                continue
            }

            if (line.startsWith("#")) continue

            // 不是 http 开头的基本是无效行（注释或残留内容）
            if (!line.startsWith("http", ignoreCase = true)) {
                name = null
                continue
            }

            val display = (name ?: tvgName ?: line.substringAfterLast('/')).ifBlank { "未命名频道" }
            val names = ChannelNames.resolve(display, tvgName, tvgId)
            result += Channel(
                name = display,
                group = group ?: "未分组",
                url = line,
                logo = logo,
                nameZh = names?.zh,
                nameEn = names?.en
            )
            name = null
            tvgId = null
            tvgName = null
        }
        return result
    }

    /**
     * 按 group 分组，组名与组内频道都按音序排。
     *
     * 排序规则见 [NameSort]：中文按拼音、英文按字母、数字段按数值。
     * 所以 CCTV-2 会排在 CCTV-10 前面，安徽卫视会排在湖北卫视前面。
     */
    fun group(channels: List<Channel>): List<Pair<String, List<Channel>>> {
        val buckets = LinkedHashMap<String, MutableList<Channel>>()
        for (c in channels) {
            buckets.getOrPut(c.group) { ArrayList() }.add(c)
        }
        val entries = buckets.entries.sortedWith(
            Comparator<Map.Entry<String, MutableList<Channel>>> { a, b ->
                NameSort.compare(a.key, b.key)
            }
        )
        return entries.map { e ->
            e.key to e.value.sortedWith(
                Comparator<Channel> { a, b -> NameSort.compare(a.sortKey, b.sortKey) }
            )
        }
    }

    /** 只要一个排好序的扁平列表（找源页用） */
    fun sorted(channels: List<Channel>): List<Channel> =
        channels.sortedWith(Comparator { a, b -> NameSort.compare(a.sortKey, b.sortKey) })
}
