package com.persephone.tv.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 影视目录 JSON 解析器。
 *
 * 期望结构：
 * {
 *   "categories": [
 *     { "name": "电影",
 *       "items": [
 *         { "title": "...", "year": "2024", "rating": "8.6",
 *           "poster": "https://...", "desc": "...",
 *           "episodes": [ { "name": "正片", "url": "https://..." } ] }
 *       ] }
 *   ]
 * }
 *
 * 兼容写法：条目没有 episodes 时，读取顶层 "url" 作为单集。
 */
object CatalogParser {

    fun parse(json: String): List<VodCategory> {
        val root = JSONObject(json)
        val categories = root.optJSONArray("categories") ?: JSONArray()
        val out = ArrayList<VodCategory>()

        for (i in 0 until categories.length()) {
            val cat = categories.optJSONObject(i) ?: continue
            val catName = cat.optString("name").ifBlank { "未分类" }
            val itemsArr = cat.optJSONArray("items") ?: continue
            val items = ArrayList<VodItem>()

            for (j in 0 until itemsArr.length()) {
                val o = itemsArr.optJSONObject(j) ?: continue
                val title = o.optString("title").ifBlank { "未命名" }

                val episodes = ArrayList<Episode>()
                val epsArr = o.optJSONArray("episodes")
                if (epsArr != null) {
                    for (k in 0 until epsArr.length()) {
                        val e = epsArr.optJSONObject(k) ?: continue
                        val url = e.optString("url")
                        if (url.isNotBlank()) {
                            episodes += Episode(
                                name = e.optString("name").ifBlank { "第 ${k + 1} 集" },
                                url = url
                            )
                        }
                    }
                } else {
                    val single = o.optString("url")
                    if (single.isNotBlank()) episodes += Episode("正片", single)
                }

                items += VodItem(
                    title = title,
                    category = catName,
                    year = o.stringOrNull("year"),
                    rating = o.stringOrNull("rating"),
                    poster = o.stringOrNull("poster"),
                    desc = o.stringOrNull("desc"),
                    episodes = episodes
                )
            }

            if (items.isNotEmpty()) out += VodCategory(catName, items)
        }
        return out
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
