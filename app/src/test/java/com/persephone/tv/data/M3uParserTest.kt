package com.persephone.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3U 解析的回归测试。
 *
 * 重点是 #EXTVLCOPT 这类附属行：它们只对紧跟着的下一个频道生效，
 * 漏掉 reset 就会把上一个频道的防盗链请求头串到下一个频道上，
 * 表现出来就是「前面几个台能播，后面全黑屏」，很难查。
 */
class M3uParserTest {

    @Test
    fun `基础解析 - 属性与分组`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="CCTV1.cn" tvg-name="CCTV-1" tvg-logo="http://a/b.png" group-title="央视",CCTV-1
            http://example.com/cctv1.m3u8
        """.trimIndent()

        val list = M3uParser.parse(m3u)
        assertEquals(1, list.size)
        val ch = list[0]
        assertEquals("CCTV-1", ch.name)
        assertEquals("央视", ch.group)
        assertEquals("http://a/b.png", ch.logo)
        assertEquals("CCTV-1 综合", ch.nameZh)
        assertTrue(ch.headers.isEmpty())
    }

    @Test
    fun `EXTVLCOPT - Referer 与 User-Agent 都要认`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="测试",有防盗链的台
            #EXTVLCOPT:http-referrer=https://www.example.com/
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Test)
            http://example.com/a.m3u8
        """.trimIndent()

        val ch = M3uParser.parse(m3u).single()
        assertEquals("https://www.example.com/", ch.referrer)
        assertEquals("Mozilla/5.0 (Test)", ch.userAgent)
        assertEquals("https://www.example.com/", ch.headers["Referer"])
        assertEquals("Mozilla/5.0 (Test)", ch.headers["User-Agent"])
        assertTrue(ch.needsHeaders)
    }

    @Test
    fun `EXTVLCOPT - referer 少写一个 r 也认`() {
        val m3u = """
            #EXTINF:-1,某台
            #EXTVLCOPT:http-referer=https://ref.example.com/
            http://example.com/a.m3u8
        """.trimIndent()
        assertEquals("https://ref.example.com/", M3uParser.parse(m3u).single().referrer)
    }

    @Test
    fun `EXTVLCOPT - 只对下一个频道生效`() {
        val m3u = """
            #EXTINF:-1,台一
            #EXTVLCOPT:http-user-agent=UA-ONE
            http://example.com/1.m3u8
            #EXTINF:-1,台二
            http://example.com/2.m3u8
        """.trimIndent()

        val list = M3uParser.parse(m3u)
        assertEquals(2, list.size)
        assertEquals("UA-ONE", list[0].userAgent)
        assertNull(list[1].userAgent)
        assertTrue(list[1].headers.isEmpty())
    }

    @Test
    fun `EXTGRP - 没有 group-title 时用它补分组`() {
        val m3u = """
            #EXTINF:-1,某台
            #EXTGRP:地方台
            http://example.com/a.m3u8
        """.trimIndent()
        assertEquals("地方台", M3uParser.parse(m3u).single().group)
    }

    @Test
    fun `没有分组的频道落到未分组`() {
        val m3u = "#EXTINF:-1,裸频道\nhttp://example.com/a.m3u8"
        assertEquals("未分组", M3uParser.parse(m3u).single().group)
    }

    @Test
    fun `非 http 行要跳过 - 注释和空行不算频道`() {
        val m3u = """
            #EXTM3U
            # 这只是一行注释
            #EXTINF:-1,台一
            http://example.com/1.m3u8
            #PLAYLIST:http://not-a-channel
            #EXTINF:-1,台二
            http://example.com/2.m3u8
        """.trimIndent()

        val list = M3uParser.parse(m3u)
        assertEquals(2, list.size)
        assertEquals(listOf("台一", "台二"), list.map { it.name })
    }

    @Test
    fun `分组顺序 - 央视卫视地方台在前，其他垫底`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="其他",某台
            http://x/0
            #EXTINF:-1 group-title="地方台 · 浙江",浙江新闻
            http://x/1
            #EXTINF:-1 group-title="卫视",浙江卫视
            http://x/2
            #EXTINF:-1 group-title="央视与 CGTN",CCTV-1
            http://x/3
            #EXTINF:-1 group-title="地方台 · 安徽",安徽新闻
            http://x/4
        """.trimIndent()

        val order = M3uParser.group(M3uParser.parse(m3u)).map { it.first }
        assertEquals(
            listOf("央视与 CGTN", "卫视", "地方台 · 安徽", "地方台 · 浙江", "其他"),
            order
        )
    }

    @Test
    fun `随包附带的频道清单解密后能跑完整条解析`() {
        // 混淆 / 解混淆本身在 EncodedSourceTest 里测，这里只看解析器面对真实数据的行为
        val plain = EncodedSource.decrypt(
            java.io.File("src/main/assets/builtin_cn.m3u.enc").readText()
        )
        val list = M3uParser.parse(plain)
        assertTrue("频道清单解析出 ${list.size} 条", list.size == 90)
        assertTrue("不该有未分组", list.none { it.group == "未分组" })
        assertTrue("不该有未命名频道", list.none { it.name == "未命名频道" })
        assertTrue("地址必须唯一", list.map { it.url }.toSet().size == list.size)
        // 至少要有一条带防盗链请求头的，证明 EXTVLCOPT 那段没白写
        assertTrue("没解析到任何请求头", list.any { it.needsHeaders })
    }
}
