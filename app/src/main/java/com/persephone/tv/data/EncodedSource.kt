package com.persephone.tv.data

import java.io.IOException

/**
 * 随包附带的频道清单的解混淆。
 *
 * 仓库里不放明文 M3U，只放 `assets/builtin_cn.m3u.enc`：原文按字节做凯撒位移，再 base64。
 * **这是混淆，不是加密** —— 目的是让爬代码仓库的脚本不能直接 grep 出一堆播放地址；
 * 认真想还原的人三行代码就还原了。别把它当安全边界，也别往里放任何敏感数据。
 *
 * 位移量与格式头必须和 `scripts/extract-cia-sources.py` 里的 `SHIFT` / `MAGIC` 一致。
 */
object EncodedSource {

    private const val SHIFT = 13
    private const val MAGIC = "# PERS-ENC1"

    /** 标准 base64 字母表（RFC 4648）。自己解码，不依赖 android.util.Base64，单测里也能跑 */
    private val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val REV = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { i, c -> table[c.code] = i }
    }

    /** 读一份包内资源；`encoded=true` 时先解混淆再返回 */
    fun readAsset(context: android.content.Context, name: String, encoded: Boolean): String {
        val raw = context.assets.open(name).bufferedReader().use { it.readText() }
        if (!encoded) return raw
        return decrypt(raw)
    }

    /**
     * `# PERS-ENC1` 头 + base64 文本 → 原始 M3U。
     * 忽略空白和 `#` 开头的行，所以 base64 拆成多少行都行。
     */
    fun decrypt(cipher: String): String {
        // 没有格式头就当格式不对，宁可直接报错也别把一堆乱码喂给解析器
        if (!cipher.trimStart().startsWith(MAGIC)) {
            throw IOException("包内清单格式不对，缺少「$MAGIC」头")
        }
        val sb = StringBuilder(cipher.length)
        for (line in cipher.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            sb.append(t)
        }
        val bytes = base64Decode(sb.toString())
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            // 编码时是 (b + SHIFT) % 256，这里减回来，补 256 防负数
            out[i] = (((bytes[i].toInt() and 0xFF) - SHIFT + 256) % 256).toByte()
        }
        return String(out, Charsets.UTF_8)
    }

    /** 标准 base64 → 字节。遇到 '=' 视为填充结束，空白直接跳过。 */
    private fun base64Decode(text: String): ByteArray {
        val out = ArrayList<Byte>(text.length / 4 * 3 + 3)
        var group = 0
        var bits = 0
        for (ch in text) {
            if (ch.isWhitespace()) continue
            if (ch == '=') break
            val v = REV.getOrNull(ch.code) ?: throw IOException("非法的 base64 字符 '$ch'")
            group = (group shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out += ((group shr bits) and 0xFF).toByte()
                // 只留下还没凑满 8 位的那几位，group 才不会越滚越大
                group = group and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}
