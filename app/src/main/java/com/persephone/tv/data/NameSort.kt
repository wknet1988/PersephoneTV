package com.persephone.tv.data

import java.text.Collator
import java.util.Locale

/**
 * 频道名排序：中文按拼音（音序），英文按字母，数字段按数值。
 *
 * 中文拼音排序交给系统的 Collator —— ICU 的 zh 默认就是 pinyin collation，
 * 比自己维护一张拼音表可靠得多，也不用担心生僻字。
 *
 * 数字段单独拆出来按数值比较，否则纯字符串排会把 `CCTV-10` 排到 `CCTV-2` 前面。
 */
object NameSort : Comparator<String> {

    private val collator: Collator = Collator.getInstance(Locale.CHINA).apply {
        // PRIMARY 忽略大小写和重音差异，"CCTV-1" 和 "cctv 1" 视为同一个
        strength = Collator.PRIMARY
    }

    /** 把字符串切成「连续数字」和「连续非数字」交替的片段 */
    private val TOKEN = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        if (a == b) return 0
        // 先按首字符类型分组：拉丁字母/数字在前，汉字在后。
        // ICU 的 zh collation 会把汉字排在拉丁字母前面，那样 CCTV-1 会被挤到
        // 所有卫视后面，跟中文用户的直觉相反。
        val ra = rank(a)
        val rb = rank(b)
        if (ra != rb) return ra - rb

        val ta = tokens(a)
        val tb = tokens(b)
        val n = minOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta[i]
            val y = tb[i]
            val c = if (isNumber(x) && isNumber(y)) {
                compareNumber(x, y)
            } else {
                compareText(x, y)
            }
            if (c != 0) return c
        }
        // 前缀完全一致时，片段多的那个排后面
        return ta.size - tb.size
    }

    /** 0 = 拉丁字母或数字，1 = 汉字，2 = 其他 */
    private fun rank(s: String): Int {
        val c = s.firstOrNull() ?: return 3
        return when {
            c.isDigit() || c in 'a'..'z' || c in 'A'..'Z' -> 0
            c.code in 0x4E00..0x9FFF -> 1
            else -> 2
        }
    }

    private fun tokens(s: String): List<String> =
        TOKEN.findAll(s).map { it.value }.toList()

    private fun isNumber(s: String): Boolean = s.isNotEmpty() && s.all { it.isDigit() }

    /** 按数值比较，避免 "10" < "2" */
    private fun compareNumber(x: String, y: String): Int {
        val xs = x.trimStart('0')
        val ys = y.trimStart('0')
        if (xs.length != ys.length) return xs.length - ys.length
        return xs.compareTo(ys)
    }

    /** Collator 不是线程安全的，加锁调用 */
    private fun compareText(x: String, y: String): Int = synchronized(collator) {
        collator.compare(x, y)
    }
}
