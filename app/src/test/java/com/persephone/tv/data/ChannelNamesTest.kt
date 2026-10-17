package com.persephone.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 频道名解析的回归测试。
 *
 * 这张对照表是整个「中英并列」功能的地基，改错了用户会看到乱七八糟的名字，
 * 所以这里把典型写法都钉死：带不带空格、带不带 -、带不带画质后缀、走 tvg-id 兜底。
 */
class ChannelNamesTest {

    private fun zh(raw: String, tvgName: String? = null, tvgId: String? = null): String? =
        ChannelNames.resolve(raw, tvgName, tvgId)?.zh

    private fun en(raw: String, tvgName: String? = null, tvgId: String? = null): String? =
        ChannelNames.resolve(raw, tvgName, tvgId)?.en

    // ---------- CCTV：各种写法都要归到同一个规范名 ----------

    @Test
    fun `CCTV 数字的各种写法`() {
        val expected = "CCTV-1 综合"
        assertEquals(expected, zh("CCTV-1"))
        assertEquals(expected, zh("CCTV 1"))
        assertEquals(expected, zh("CCTV1"))
        assertEquals(expected, zh("cctv-1"))
        assertEquals(expected, zh("CCTV-1 综合"))
        assertEquals(expected, zh("CCTV 1 HD"))
        assertEquals(expected, zh("CCTV-1高清"))
    }

    @Test
    fun `CCTV 英文名统一成 CCTV-n`() {
        assertEquals("CCTV-1", en("CCTV 1"))
        assertEquals("CCTV-13", en("CCTV-13"))
    }

    @Test
    fun `CCTV-5+ 不能被当成 CCTV-5`() {
        assertEquals("CCTV-5+ 体育赛事", zh("CCTV-5+"))
        assertEquals("CCTV-5+ 体育赛事", zh("CCTV 5 Plus"))
        assertEquals("CCTV-5+ 体育赛事", zh("CCTV5+"))
        assertEquals("CCTV-5 体育", zh("CCTV-5"))
    }

    @Test
    fun `CCTV 4K 不能被当成 CCTV-4`() {
        assertEquals("CCTV-4K 超高清", zh("CCTV-4K"))
        assertEquals("CCTV-4K 超高清", zh("CCTV 4K"))
        assertEquals("CCTV-4K 超高清", zh("CCTV4K超高清"))
        assertEquals("CCTV-4 中文国际", zh("CCTV-4"))
        assertEquals("CCTV-4 中文国际", zh("CCTV-4 中文国际"))
    }

    @Test
    fun `tvg-id 能兜底认出频道`() {
        // 显示名被改花了，但 tvg-id 还留着台号
        assertEquals("CCTV-1 综合", zh("CCTV1.cn", tvgId = "CCTV1.cn"))
        assertEquals("CCTV-1 综合", zh("高清综合", tvgId = "CCTV1.cn"))
    }

    // ---------- 卫视：规则批量生成 ----------

    @Test
    fun `XX Satellite TV 生成卫视名`() {
        assertEquals("湖南卫视", zh("Hunan Satellite TV"))
        assertEquals("浙江卫视", zh("Zhejiang Satellite TV"))
        assertEquals("内蒙古卫视", zh("Inner Mongolia Satellite TV"))
        assertEquals("黑龙江卫视", zh("Heilongjiang Satellite TV"))
    }

    @Test
    fun `裸 TV 也认作卫视`() {
        assertEquals("湖南卫视", zh("Hunan TV"))
        assertEquals("江苏卫视", zh("Jiangsu TV"))
    }

    @Test
    fun `英文名保留原写法`() {
        assertEquals("Hunan Satellite TV", en("Hunan Satellite TV"))
        assertEquals("Hunan TV", en("Hunan TV"))
    }

    @Test
    fun `城市台不硬算成卫视`() {
        assertEquals("大连电视台", zh("Dalian TV"))
        assertEquals("西安电视台", zh("Xi'an TV"))
        // 但带 Satellite 的确实是卫视
        assertEquals("深圳卫视", zh("Shenzhen Satellite TV"))
    }

    // ---------- 别名表 ----------

    @Test
    fun `名字里没有地名的卫视靠别名表`() {
        assertEquals("东方卫视", zh("Dragon TV"))
        assertEquals("东方卫视", zh("DragonTV"))
        assertEquals("东南卫视", zh("Southeast TV"))
        assertEquals("大湾区卫视", zh("Greater Bay Area Satellite TV"))
    }

    @Test
    fun `港澳台频道`() {
        assertEquals("翡翠台", zh("TVB Jade"))
        assertEquals("明珠台", zh("TVB Pearl"))
        assertEquals("凤凰卫视资讯台", zh("Phoenix InfoNews Channel"))
        assertEquals("凤凰卫视中文台", zh("Phoenix TV"))
        assertEquals("中天新闻", zh("CTi News"))
        assertEquals("东森新闻", zh("EBC News"))
    }

    // ---------- 画质后缀 ----------

    @Test
    fun `剥掉画质后缀还能认出`() {
        assertEquals("湖南卫视", zh("湖南卫视高清"))
        assertEquals("湖南卫视", zh("湖南卫视 HD"))
        assertEquals("翡翠台", zh("TVB Jade [HD]"))
    }

    // ---------- 认不出来要老实返回 null ----------

    @Test
    fun `不认识的频道不乱猜`() {
        assertNull(ChannelNames.resolve("Some Random Channel"))
        assertNull(ChannelNames.resolve("CCTV-99"))
        assertNull(ChannelNames.resolve(""))
    }

    // ---------- Channel 上的展示字段 ----------

    @Test
    fun `Channel 主副标题`() {
        val known = Channel(
            name = "CCTV 1",
            group = "央视",
            url = "http://x/1.m3u8",
            nameZh = "CCTV-1 综合",
            nameEn = "CCTV-1"
        )
        assertEquals("CCTV-1 综合", known.title)
        // 英文名已经包含在中文名里，不再重复显示
        assertNull(known.subtitle)

        // 中英完全不同才显示第二行
        val satellite = Channel("Hunan TV", "卫视", "http://x/2", nameZh = "湖南卫视", nameEn = "Hunan Satellite TV")
        assertEquals("湖南卫视", satellite.title)
        assertEquals("Hunan Satellite TV", satellite.subtitle)

        // 中英同名（比如 CNN）不该重复显示两行
        val same = Channel("CNN", "国际", "http://x/3", nameZh = "CNN", nameEn = "CNN")
        assertEquals("CNN", same.title)
        assertNull(same.subtitle)

        // 没认出来的保持原文，单行
        val unknown = Channel("Some Channel", "其他", "http://x/4")
        assertEquals("Some Channel", unknown.title)
        assertNull(unknown.subtitle)
    }

    // ---------- 排序 ----------

    @Test
    fun `数字按数值排而不是按字符排`() {
        val list = listOf("CCTV-10 科教", "CCTV-2 财经", "CCTV-1 综合", "CCTV-3 综艺")
        val sorted = M3uParser.sorted(
            list.map { Channel(it, "央视", "http://x/$it") }
        ).map { it.title }
        assertEquals(
            listOf("CCTV-1 综合", "CCTV-2 财经", "CCTV-3 综艺", "CCTV-10 科教"),
            sorted
        )
    }

    @Test
    fun `分组内频道按音序排`() {
        // 走完整解析链路：M3U -> Channel -> 分组排序
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="卫视",Hunan TV
            http://x/1
            #EXTINF:-1 group-title="卫视",Anhui TV
            http://x/2
            #EXTINF:-1 group-title="卫视",Zhejiang TV
            http://x/3
        """.trimIndent()
        val groups = M3uParser.group(M3uParser.parse(m3u))
        assertEquals(1, groups.size)
        // 中文拼音序：安徽(anhui) < 湖南(hunan) < 浙江(zhejiang)
        assertEquals(
            listOf("安徽卫视", "湖南卫视", "浙江卫视"),
            groups.first().second.map { it.title }
        )
    }

    @Test
    fun `英文名排在中文名前面且数字按数值`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="全部",Hunan TV
            http://x/1
            #EXTINF:-1 group-title="全部",CCTV 2
            http://x/2
            #EXTINF:-1 group-title="全部",Anhui TV
            http://x/3
            #EXTINF:-1 group-title="全部",CCTV 13
            http://x/4
            #EXTINF:-1 group-title="全部",CCTV 1
            http://x/5
        """.trimIndent()
        val titles = M3uParser.group(M3uParser.parse(m3u)).first().second.map { it.title }
        assertEquals(
            listOf("CCTV-1 综合", "CCTV-2 财经", "CCTV-13 新闻", "安徽卫视", "湖南卫视"),
            titles
        )
    }

    @Test
    fun `解析 M3U 时自动补上中英名`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="CCTV1.cn" tvg-logo="http://x/logo.png" group-title="央视",CCTV 1 HD
            http://x/cctv1.m3u8
            #EXTINF:-1 group-title="其他",Some Random Channel
            http://x/other.m3u8
        """.trimIndent()
        val list = M3uParser.parse(m3u)

        assertEquals(2, list.size)
        assertEquals("CCTV-1 综合", list[0].title)
        // "CCTV-1" 已被中文名包含，不重复显示
        assertNull(list[0].subtitle)
        assertEquals("央视", list[0].group)
        assertEquals("http://x/logo.png", list[0].logo)

        // 认不出来的保持原文，且不显示空的第二行
        assertEquals("Some Random Channel", list[1].title)
        assertNull(list[1].subtitle)
    }
}
