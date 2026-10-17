package com.persephone.tv.data

/**
 * 频道中英名互译。
 *
 * 公开 M3U 索引（如 iptv-org）里的中文频道多半只给英文名，对中文用户很不友好。
 * 这里按三层解析，能认出来就返回「中文名 + 英文名」，认不出来就老实返回 null，
 * 绝不硬猜——猜错比显示英文更糟。
 *
 *   1. 别名表：港澳台、特色频道这类没规律的名字
 *   2. 规则：CCTV-x / XX Satellite TV / XX TV 这类有规律的批量生成
 *   3. 去噪重试：剥掉 HD / FHD / 4K / 超清 等画质后缀再试一遍
 */
object ChannelNames {

    /** 一个频道的中英文名 */
    data class Names(val zh: String, val en: String)

    private class Entry(
        val zh: String,
        val en: String,
        val aliases: List<String> = emptyList()
    )

    // ==================== 规则 1：CCTV ====================

    /** CCTV 各频道的定位名，用于拼出「CCTV-1 综合」这种规范中文名 */
    private val CCTV_SLOT = mapOf(
        1 to "综合", 2 to "财经", 3 to "综艺", 4 to "中文国际",
        5 to "体育", 6 to "电影", 7 to "国防军事", 8 to "电视剧",
        9 to "纪录", 10 to "科教", 11 to "戏曲", 12 to "社会与法",
        13 to "新闻", 14 to "少儿", 15 to "音乐", 16 to "奥林匹克",
        17 to "农业农村"
    )

    private val CCTV_NUM = Regex("^cctv(\\d{1,2})(.*)$")

    // ==================== 规则 2：XX Satellite TV / XX TV ====================

    /** 省级行政区：都有上星卫视，裸 TV 也算卫视 */
    private val PROVINCES: Map<String, Pair<String, String>> = mapOf(
        "beijing" to ("北京" to "Beijing"),
        "tianjin" to ("天津" to "Tianjin"),
        "hebei" to ("河北" to "Hebei"),
        "shanxi" to ("山西" to "Shanxi"),
        "innermongolia" to ("内蒙古" to "Inner Mongolia"),
        "liaoning" to ("辽宁" to "Liaoning"),
        "jilin" to ("吉林" to "Jilin"),
        "heilongjiang" to ("黑龙江" to "Heilongjiang"),
        "shanghai" to ("上海" to "Shanghai"),
        "jiangsu" to ("江苏" to "Jiangsu"),
        "zhejiang" to ("浙江" to "Zhejiang"),
        "anhui" to ("安徽" to "Anhui"),
        "fujian" to ("福建" to "Fujian"),
        "jiangxi" to ("江西" to "Jiangxi"),
        "shandong" to ("山东" to "Shandong"),
        "henan" to ("河南" to "Henan"),
        "hubei" to ("湖北" to "Hubei"),
        "hunan" to ("湖南" to "Hunan"),
        "guangdong" to ("广东" to "Guangdong"),
        "guangxi" to ("广西" to "Guangxi"),
        "hainan" to ("海南" to "Hainan"),
        "chongqing" to ("重庆" to "Chongqing"),
        "sichuan" to ("四川" to "Sichuan"),
        "guizhou" to ("贵州" to "Guizhou"),
        "yunnan" to ("云南" to "Yunnan"),
        "tibet" to ("西藏" to "Tibet"),
        "shaanxi" to ("陕西" to "Shaanxi"),
        "gansu" to ("甘肃" to "Gansu"),
        "qinghai" to ("青海" to "Qinghai"),
        "ningxia" to ("宁夏" to "Ningxia"),
        "xinjiang" to ("新疆" to "Xinjiang")
    )

    /** 城市台：默认是地面频道，名字里带 Satellite 才当卫视 */
    private val CITIES: Map<String, Pair<String, String>> = mapOf(
        "shenzhen" to ("深圳" to "Shenzhen"),
        "xiamen" to ("厦门" to "Xiamen"),
        "sansha" to ("三沙" to "Sansha"),
        "yanbian" to ("延边" to "Yanbian"),
        "bingtuan" to ("兵团" to "Bingtuan"),
        "dalian" to ("大连" to "Dalian"),
        "qingdao" to ("青岛" to "Qingdao"),
        "ningbo" to ("宁波" to "Ningbo"),
        "suzhou" to ("苏州" to "Suzhou"),
        "wuxi" to ("无锡" to "Wuxi"),
        "guangzhou" to ("广州" to "Guangzhou"),
        "hangzhou" to ("杭州" to "Hangzhou"),
        "nanjing" to ("南京" to "Nanjing"),
        "wuhan" to ("武汉" to "Wuhan"),
        "chengdu" to ("成都" to "Chengdu"),
        "xian" to ("西安" to "Xi'an"),
        "changsha" to ("长沙" to "Changsha"),
        "shenyang" to ("沈阳" to "Shenyang"),
        "zhengzhou" to ("郑州" to "Zhengzhou"),
        "jinan" to ("济南" to "Jinan"),
        "hefei" to ("合肥" to "Hefei"),
        "kunming" to ("昆明" to "Kunming"),
        "nanchang" to ("南昌" to "Nanchang"),
        "fuzhou" to ("福州" to "Fuzhou"),
        "shijiazhuang" to ("石家庄" to "Shijiazhuang"),
        "taiyuan" to ("太原" to "Taiyuan"),
        "changchun" to ("长春" to "Changchun"),
        "harbin" to ("哈尔滨" to "Harbin"),
        "lanzhou" to ("兰州" to "Lanzhou"),
        "xining" to ("西宁" to "Xining"),
        "yinchuan" to ("银川" to "Yinchuan"),
        "urumqi" to ("乌鲁木齐" to "Urumqi"),
        "guiyang" to ("贵阳" to "Guiyang"),
        "nanning" to ("南宁" to "Nanning"),
        "haikou" to ("海口" to "Haikou")
    )

    private val REGION_TV = Regex("^(.+?)(satellite)?tv$")

    // ==================== 别名表 ====================

    private val ENTRIES = listOf(
        // —— 卫视里名字不含地名的 ——
        Entry("东方卫视", "Dragon TV", listOf("DragonTV", "Shanghai Dragon TV")),
        Entry("东南卫视", "Southeast TV", listOf("SoutheastTV", "Fujian Southeast TV")),
        Entry("大湾区卫视", "Greater Bay Area TV", listOf("Greater Bay Area Satellite TV", "GBA TV")),
        Entry("农林卫视", "Nonglin TV", listOf("Agriculture and Forestry TV")),
        Entry("康巴卫视", "Kangba TV", emptyList()),
        Entry("海峡卫视", "Strait TV", emptyList()),

        // —— 教育台 ——
        Entry("中国教育电视台一套", "CETV-1", listOf("CETV 1", "China Education TV 1")),
        Entry("中国教育电视台二套", "CETV-2", listOf("CETV 2", "China Education TV 2")),
        Entry("中国教育电视台三套", "CETV-3", listOf("CETV 3", "China Education TV 3")),
        Entry("中国教育电视台四套", "CETV-4", listOf("CETV 4", "China Education TV 4")),

        // —— 港澳 ——
        Entry("凤凰卫视中文台", "Phoenix Chinese Channel", listOf("Phoenix TV", "Phoenix Chinese")),
        Entry("凤凰卫视资讯台", "Phoenix InfoNews Channel", listOf("Phoenix InfoNews", "Phoenix Info News Channel")),
        Entry("凤凰卫视香港台", "Phoenix Hong Kong Channel", listOf("Phoenix HK Channel")),
        Entry("翡翠台", "TVB Jade", emptyList()),
        Entry("明珠台", "TVB Pearl", emptyList()),
        Entry("无线新闻台", "TVB News", listOf("TVB News Channel")),
        Entry("无线财经资讯台", "TVB Finance & Information", listOf("TVB Finance and Information", "TVB Finance Info")),
        Entry("J2", "TVB J2", emptyList()),
        Entry("ViuTV", "ViuTV", emptyList()),
        Entry("ViuTVsix", "ViuTVsix", emptyList()),
        Entry("港台电视31", "RTHK TV 31", listOf("RTHK 31")),
        Entry("港台电视32", "RTHK TV 32", listOf("RTHK 32")),
        Entry("HOY TV", "HOY TV", emptyList()),
        Entry("澳视澳门", "TDM Ou Mun", listOf("TDM Macau", "TDM Ou Mun Channel")),
        Entry("澳门莲花卫视", "Lotus TV Macau", listOf("Lotus Macau")),

        // —— 台湾 ——
        Entry("中视", "CTV", listOf("China Television")),
        Entry("台视", "TTV", listOf("Taiwan Television")),
        Entry("华视", "CTS", listOf("Chinese Television System")),
        Entry("民视", "FTV", listOf("Formosa TV")),
        Entry("公视", "PTS", listOf("Public Television Service")),
        Entry("客家电视台", "Hakka TV", emptyList()),
        Entry("中天新闻", "CTi News", emptyList()),
        Entry("中天综合", "CTi Variety", emptyList()),
        Entry("东森新闻", "EBC News", listOf("ETTV News")),
        Entry("东森财经新闻", "EBC Financial News", emptyList()),
        Entry("三立新闻", "SET News", emptyList()),
        Entry("三立台湾台", "SET Taiwan", emptyList()),
        Entry("TVBS新闻", "TVBS News", emptyList()),
        Entry("TVBS欢乐台", "TVBS Entertainment", emptyList()),
        Entry("年代新闻", "Era News", emptyList()),
        Entry("非凡新闻", "USTV News", emptyList()),
        Entry("寰宇新闻", "Global News", emptyList()),

        // —— 卡通 / 少儿 ——
        Entry("金鹰卡通", "Aniworld TV", listOf("Aniworld Satellite TV")),
        Entry("卡酷少儿", "Kaku Children", listOf("BTV Kaku", "Kaku Kids")),
        Entry("优漫卡通", "Youman Cartoon", emptyList()),
        Entry("嘉佳卡通", "Jiajia Cartoon", emptyList()),
        Entry("炫动卡通", "Toonmax TV", listOf("Toonmax")),
        Entry("哈哈炫动", "HaHa Toonmax", emptyList()),
        Entry("优优宝贝", "YoYo Baby", emptyList()),

        // —— 体育 ——
        Entry("五星体育", "Great Sports", listOf("Five Star Sports")),
        Entry("广东体育", "Guangdong Sports", emptyList()),
        Entry("北京体育", "Beijing Sports", emptyList()),
        Entry("天津体育", "Tianjin Sports", emptyList()),

        // —— 影视 ——
        Entry("东方影视", "Oriental Movie", listOf("Oriental Film")),
        Entry("潇湘电影", "Xiaoxiang Movie", listOf("Xiangxiao Movie")),
        Entry("CHC家庭影院", "CHC Home Cinema", emptyList()),
        Entry("CHC动作电影", "CHC Action Movie", emptyList()),
        Entry("CHC高清电影", "CHC HD Movie", emptyList()),

        // —— 新闻 / 都市 ——
        Entry("上海新闻综合", "Shanghai News and General", emptyList()),
        Entry("看看新闻", "Kankan News", emptyList()),
        Entry("深圳都市", "Shenzhen Metro", emptyList()),

        // —— 地方台：公开索引里只写英文名的（含拼写变体，比如 Chanel）——
        Entry("白城电视台", "Baicheng TV", emptyList()),
        Entry(
            "赤峰新闻综合", "Chifeng Comprehensive News Channel",
            listOf("Chifeng Comprehensive News", "Chifeng Comprehensive News Chanel")
        ),
        Entry(
            "哈尔滨新闻综合", "Harbin Comprehensive News Channel",
            listOf("Harbin Comprehensive News")
        ),
        Entry("哈尔滨影视", "Harbin Movie Channel", emptyList()),
        Entry(
            "兰州新闻综合", "Lanzhou Comprehensive News Channel",
            listOf("Lanzhou Comprehensive News")
        ),
        Entry("四平电视台", "Siping TV", emptyList()),
        Entry("新疆电视台二套", "Xinjiang TV 2", emptyList()),
        Entry("浙江国际", "Zhejiang International Channel", emptyList()),
        Entry("东方卫视国际", "Dragon TV International", emptyList()),
        Entry("江苏公共新闻", "Jiangsu Public & News Channel", emptyList()),

        // 青岛台：源里只有 QTV-n 代号，补上城市名，用户才知道是哪儿
        Entry("青岛 QTV-1", "QTV-1", emptyList()),
        Entry("青岛 QTV-2", "QTV-2", emptyList()),
        Entry("青岛 QTV-3", "QTV-3", emptyList()),
        Entry("青岛 QTV-4", "QTV-4", emptyList()),
        Entry("青岛 QTV-5", "QTV-5", emptyList()),
        Entry("青岛 QTV-6", "QTV-6", emptyList())
    )

    // ==================== 索引 ====================

    private val index: Map<String, Names> = HashMap<String, Names>().also { map ->
        // 1) 别名表
        for (e in ENTRIES) {
            val n = Names(e.zh, e.en)
            addKey(map, e.zh, n)
            addKey(map, e.en, n)
            for (a in e.aliases) addKey(map, a, n)
        }

        // 2) 把规则能生成的名字也提前展开进索引。
        //    否则「湖南卫视高清」剥掉后缀后虽然得到「湖南卫视」，
        //    但那是规则算出来的、表里没有，回查会失败。
        for ((n, slot) in CCTV_SLOT) {
            val names = Names("CCTV-$n $slot", "CCTV-$n")
            addKey(map, "CCTV-$n $slot", names)
            addKey(map, "CCTV-$n", names)
        }
        addKey(map, "CCTV-5+ 体育赛事", Names("CCTV-5+ 体育赛事", "CCTV-5+"))
        addKey(map, "CCTV-5+", Names("CCTV-5+ 体育赛事", "CCTV-5+"))
        addKey(map, "CCTV-4K 超高清", Names("CCTV-4K 超高清", "CCTV-4K"))
        addKey(map, "CCTV-4K", Names("CCTV-4K 超高清", "CCTV-4K"))
        addKey(map, "CCTV-8K 超高清", Names("CCTV-8K 超高清", "CCTV-8K"))
        addKey(map, "CCTV-8K", Names("CCTV-8K 超高清", "CCTV-8K"))

        for ((zh, en) in PROVINCES.values) {
            addKey(map, "${zh}卫视", Names("${zh}卫视", "$en Satellite TV"))
        }
        for ((zh, en) in CITIES.values) {
            addKey(map, "${zh}电视台", Names("${zh}电视台", "$en TV"))
            addKey(map, "${zh}卫视", Names("${zh}卫视", "$en Satellite TV"))
        }
    }

    private fun addKey(map: HashMap<String, Names>, raw: String, names: Names) {
        val k = normalize(raw)
        if (k.isNotEmpty()) map.putIfAbsent(k, names)
    }

    // ==================== 对外接口 ====================

    /**
     * 尽量认出频道的中英文名。
     *
     * @param raw     M3U 里 `#EXTINF` 逗号后面的显示名
     * @param tvgName `tvg-name` 属性（可选，通常和显示名一致，偶尔更规范）
     * @param tvgId   `tvg-id` 属性（可选，形如 `CCTV1.cn`，去掉后缀常能反推台号）
     * @return 认出来返回中英双名，认不出来返回 null（调用方保持显示原文）
     */
    fun resolve(raw: String, tvgName: String? = null, tvgId: String? = null): Names? {
        val candidates = ArrayList<String>(3)
        for (c in arrayOf(raw, tvgName, tvgId?.substringBefore('.'))) {
            val t = c?.trim().orEmpty()
            if (t.isNotEmpty() && !candidates.contains(t)) candidates += t
        }
        // 1) 原样查表
        for (c in candidates) index[normalize(c)]?.let { return it }
        // 2) 原样套规则
        for (c in candidates) rule(normalize(c))?.let { return it }
        // 3) 剥掉画质后缀再来一遍
        for (c in candidates) {
            val s = stripNoise(c)
            if (s == c) continue
            index[normalize(s)]?.let { return it }
            rule(normalize(s))?.let { return it }
        }
        return null
    }

    // ==================== 规则实现 ====================

    private fun rule(key: String): Names? {
        if (key.isEmpty()) return null
        if (key.startsWith("cctv")) return cctv(key)

        val m = REGION_TV.matchEntire(key) ?: return null
        val region = m.groupValues[1]
        val satellite = m.groupValues[2].isNotEmpty()

        PROVINCES[region]?.let { (zh, en) ->
            return Names("${zh}卫视", if (satellite) "$en Satellite TV" else "$en TV")
        }
        CITIES[region]?.let { (zh, en) ->
            return Names(
                if (satellite) "${zh}卫视" else "${zh}电视台",
                if (satellite) "$en Satellite TV" else "$en TV"
            )
        }
        return null
    }

    private fun cctv(key: String): Names? {
        // 先处理 4K / 8K，否则 "cctv4k" 会被下面的数字规则误判成 CCTV-4
        if (key.contains("4k")) return Names("CCTV-4K 超高清", "CCTV-4K")
        if (key.contains("8k")) return Names("CCTV-8K 超高清", "CCTV-8K")

        val m = CCTV_NUM.matchEntire(key) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val rest = m.groupValues[2]
        // "cctv123" 这种不是台号，别猜
        if (rest.isNotEmpty() && rest.all { it.isDigit() }) return null

        val slot = CCTV_SLOT[n] ?: return null
        if (n == 5 && (rest.startsWith("+") || rest.startsWith("plus"))) {
            return Names("CCTV-5+ 体育赛事", "CCTV-5+")
        }
        return Names("CCTV-$n $slot", "CCTV-$n")
    }

    // ==================== 文本归一化 ====================

    /** 统一成小写、无空格标点的形式，方便比对（保留 + 以区分 CCTV-5+） */
    private fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            // 全角转半角
            val c = if (ch in '\uFF01'..'\uFF5E') (ch.code - 0xFEE0).toChar() else ch
            if (c.isLetterOrDigit() || c == '+') sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    private val NOISE = Regex(
        "(?i)[\\s\\-_·]*(\\[(hd|fhd|uhd|sd|hq|4k|8k|ip|hevc)\\]" +
            "|\\((hd|fhd|uhd|4k|8k)\\)" +
            "|hd|fhd|uhd|sd|hq|4k|8k|超高清|超清|高清|标清|蓝光|原画|流畅)$"
    )

    /** 反复剥掉结尾的画质词，如 "湖南卫视高清" -> "湖南卫视" */
    private fun stripNoise(raw: String): String {
        var cur = raw.trim()
        while (true) {
            val next = NOISE.replace(cur, "").trim()
            if (next == cur || next.isEmpty()) return cur
            cur = next
        }
    }
}
