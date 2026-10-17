package com.persephone.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 随包附带的频道清单的加密存放 + 解密链路。
 *
 * 三件事要钉死：
 * 1. base64 解码是自己写的（不依赖 `android.util.Base64`，那个在 JVM 单测里是桩会直接抛异常），
 *    所以拿 **JDK 自带的 base64 编码器**当参照做往返验证，不能「看起来能跑」就信。
 * 2. 仓库里必须只有 `.enc`、密文里不能出现明文地址，否则混淆这件事就白做。
 * 3. 解密出来的东西要能原样走完解析 → 分组 → 认中文名整条链路。
 */
class EncodedSourceTest {

    private val ASSETS = "src/main/assets"
    private val SHIFT = 13

    /** 和 scripts/extract-cia-sources.py 的 encrypt() 完全一致的编码，用 JDK 的编码器当参照 */
    private fun encode(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val shifted = ByteArray(bytes.size) { (((bytes[it].toInt() and 0xFF) + SHIFT) % 256).toByte() }
        return "# PERS-ENC1\n" + java.util.Base64.getEncoder().encodeToString(shifted)
    }

    @Test
    fun `往返 - 空串与各种长度`() {
        // base64 按 3 字节分组，长度 0~3 的余数各自不同，都要覆盖
        for (s in listOf("", "f", "fo", "foo", "foob", "fooba", "foobar", "foobarbaz")) {
            assertEquals(s, EncodedSource.decrypt(encode(s)))
        }
    }

    @Test
    fun `往返 - UTF-8 多字节与换行`() {
        for (s in listOf("中", "中文频道名", "第一行\n第二行\n\n# 注释样式的行", "àççént")) {
            assertEquals(s, EncodedSource.decrypt(encode(s)))
        }
    }

    @Test
    fun `往返 - base64 拆成多行也能解`() {
        val text = "CCTV-1|https://example.com/live/cctv1.m3u8|央视|中国||"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val shifted = ByteArray(bytes.size) { (((bytes[it].toInt() and 0xFF) + SHIFT) % 256).toByte() }
        val chunked = java.util.Base64.getEncoder().encodeToString(shifted).chunked(10).joinToString("\n")
        assertEquals(text, EncodedSource.decrypt("# PERS-ENC1\n$chunked"))
    }

    @Test
    fun `格式不对要报错`() {
        val ex = org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            EncodedSource.decrypt("# 这是什么格式\nZm9v")
        }
        assertTrue("报错信息应该说清楚原因", ex.message?.contains("PERS-ENC1") == true)
    }

    // ---------- 加密存放的频道清单本身 ----------

    @Test
    fun `仓库里只有加密版频道清单`() {
        assertFalse(
            "assets 里不该有明文 builtin_cn.m3u，否则混淆白做",
            File("$ASSETS/builtin_cn.m3u").exists()
        )
        assertTrue("缺少加密版频道清单", File("$ASSETS/builtin_cn.m3u.enc").exists())
    }

    @Test
    fun `密文里没有明文播放地址`() {
        val text = File("$ASSETS/builtin_cn.m3u.enc").readText()
        assertTrue("缺格式头", text.trimStart().startsWith("# PERS-ENC1"))
        assertFalse("密文里不该出现 .m3u8", text.contains(".m3u8"))
        assertFalse("密文里不该出现 https:", text.contains("https:"))
        assertFalse("密文里不该出现 CCTV", text.contains("CCTV"))
    }

    @Test
    fun `解密后是完整可播的 M3U`() {
        val plain = EncodedSource.decrypt(File("$ASSETS/builtin_cn.m3u.enc").readText())
        assertTrue("应以 #EXTM3U 开头", plain.startsWith("#EXTM3U"))
        assertEquals(
            90,
            plain.split("\n").count { it.startsWith("#EXTINF") }
        )
    }

    @Test
    fun `解密结果能完整解析成频道并排好组`() {
        val groups = M3uParser.group(
            M3uParser.parse(
                EncodedSource.decrypt(File("$ASSETS/builtin_cn.m3u.enc").readText())
            )
        )
        val list = groups.flatMap { it.second }
        assertTrue("解析出 ${list.size} 条", list.size == 90)
        assertTrue("不该有未分组", list.none { it.group == "未分组" })
        assertTrue("地址必须唯一", list.map { it.url }.toSet().size == list.size)
        assertTrue("至少要有一条带防盗链请求头", list.any { it.needsHeaders })
        // 央视必须排在最前面，地方台垫不住它
        assertEquals("央视与 CGTN", groups.first().first)
        assertEquals("其他", groups.last().first)
    }

    @Test
    fun `中文名认得出来`() {
        val list = M3uParser.parse(
            EncodedSource.decrypt(File("$ASSETS/builtin_cn.m3u.enc").readText())
        )
        val cctv1 = list.first { it.name == "CCTV-1" }
        assertEquals("CCTV-1 综合", cctv1.title)
        assertNull(cctv1.subtitle) // 标题里已含 CCTV-1，不重复显示
        assertEquals("青岛 QTV-1", list.first { it.name == "QTV-1" }.title)
        assertEquals("哈尔滨新闻综合", list.first { it.name.startsWith("Harbin Comprehensive") }.title)
    }
}
