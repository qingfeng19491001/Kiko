package com.kuikly.stockchat.domain.model

/**
 * 内置标的目录：Demo 支持识别的个股与指数。
 * 真实项目中可替换为搜索接口，这里用静态表 + 别名匹配实现意图识别中的“实体抽取”。
 */
object StockCatalog {

    val tencent = Instrument(
        code = "00700", name = "腾讯控股", market = Market.HK,
        aliases = listOf("腾讯", "tencent", "700", "0700"),
        sector = "互联网 / 游戏 / 社交", logoColor = 0xFF2B6CF6,
    )
    val alibaba = Instrument(
        code = "09988", name = "阿里巴巴", market = Market.HK,
        aliases = listOf("阿里", "阿里巴巴-W", "baba", "9988"),
        sector = "互联网 / 电商 / 云计算", logoColor = 0xFFFF6A00,
    )
    val meituan = Instrument(
        code = "03690", name = "美团", market = Market.HK,
        aliases = listOf("美团-W", "meituan", "3690"),
        sector = "本地生活 / 外卖", logoColor = 0xFFFFC300,
    )
    val xiaomi = Instrument(
        code = "01810", name = "小米集团", market = Market.HK,
        aliases = listOf("小米", "小米集团-W", "xiaomi", "1810"),
        sector = "智能硬件 / 新能源汽车", logoColor = 0xFFFF6900,
    )
    val byd = Instrument(
        code = "01211", name = "比亚迪股份", market = Market.HK,
        aliases = listOf("比亚迪", "byd", "1211"),
        sector = "新能源汽车", logoColor = 0xFFE60012,
    )
    val moutai = Instrument(
        code = "600519", name = "贵州茅台", market = Market.SH,
        aliases = listOf("茅台", "moutai"),
        sector = "白酒 / 消费", logoColor = 0xFFC8102E,
    )
    val catl = Instrument(
        code = "300750", name = "宁德时代", market = Market.SZ,
        aliases = listOf("宁德", "catl"),
        sector = "锂电池 / 新能源", logoColor = 0xFF1B8A5A,
    )
    val pingAn = Instrument(
        code = "601318", name = "中国平安", market = Market.SH,
        aliases = listOf("平安", "中国平安保险"),
        sector = "金融 / 保险", logoColor = 0xFFE8541E,
    )
    val apple = Instrument(
        code = "AAPL", tencentCodeOverride = "AAPL.OQ", name = "苹果", market = Market.US,
        aliases = listOf("apple", "aapl", "苹果公司"),
        sector = "消费电子 / 科技", logoColor = 0xFF1D1D1F,
    )
    val tesla = Instrument(
        code = "TSLA", tencentCodeOverride = "TSLA.OQ", name = "特斯拉", market = Market.US,
        aliases = listOf("tesla", "tsla"),
        sector = "电动车 / 科技", logoColor = 0xFFCC0000,
    )
    val nvidia = Instrument(
        code = "NVDA", tencentCodeOverride = "NVDA.OQ", name = "英伟达", market = Market.US,
        aliases = listOf("nvidia", "nvda", "英伟达公司"),
        sector = "半导体 / AI 算力", logoColor = 0xFF76B900,
    )

    val hsi = Instrument(
        code = "HSI", name = "恒生指数", market = Market.HK, type = InstrumentType.INDEX,
        aliases = listOf("恒指", "恒生", "港股大盘", "港股"), logoText = "恒", logoColor = 0xFF2B6CF6,
    )
    val hstech = Instrument(
        code = "HSTECH", name = "恒生科技指数", market = Market.HK, type = InstrumentType.INDEX,
        aliases = listOf("恒生科技", "恒科", "港股科技板块", "港股科技"), logoText = "科", logoColor = 0xFF7C3AED,
    )
    val sse = Instrument(
        code = "000001", name = "上证指数", market = Market.SH, type = InstrumentType.INDEX,
        aliases = listOf("上证", "沪指", "大盘", "A股大盘", "a股"), logoText = "沪", logoColor = 0xFFE8541E,
    )
    val szse = Instrument(
        code = "399001", name = "深证成指", market = Market.SZ, type = InstrumentType.INDEX,
        aliases = listOf("深成指", "深证"), logoText = "深", logoColor = 0xFF1B8A5A,
    )
    val chinext = Instrument(
        code = "399006", name = "创业板指", market = Market.SZ, type = InstrumentType.INDEX,
        aliases = listOf("创业板", "创指"), logoText = "创", logoColor = 0xFF0EA5E9,
    )

    val stocks: List<Instrument> = listOf(
        tencent, alibaba, meituan, xiaomi, byd, moutai, catl, pingAn, apple, tesla, nvidia,
    )
    val indices: List<Instrument> = listOf(hsi, hstech, sse, szse, chinext)
    val all: List<Instrument> = stocks + indices

    /** 热门标的（用于欢迎页推荐 / 兜底） */
    val hot: List<Instrument> = listOf(tencent, alibaba, meituan, xiaomi, byd, moutai)

    /** 首页市场概览展示的指数 */
    val overviewIndices: List<Instrument> = listOf(hsi, hstech, sse)

    fun findByKey(key: String): Instrument? = all.firstOrNull { it.key == key }

    fun findByDisplayCode(displayCode: String): Instrument? =
        all.firstOrNull { it.displayCode.equals(displayCode, ignoreCase = true) }

    /** 按名称 / 代码 / 别名解析标的（意图识别回包用）。 */
    fun resolveName(name: String): Instrument? {
        val key = name.trim()
        if (key.isEmpty()) return null
        val lower = key.lowercase()
        return all.firstOrNull { it.name.equals(key, ignoreCase = true) }
            ?: all.firstOrNull { it.displayCode.equals(key, ignoreCase = true) }
            ?: all.firstOrNull { it.code.equals(key, ignoreCase = true) }
            ?: all.firstOrNull { instrument -> instrument.aliases.any { it.equals(key, ignoreCase = true) } }
            ?: extract(key).firstOrNull()
            ?: all.firstOrNull { it.name.lowercase().contains(lower) || lower.contains(it.name.lowercase()) }
    }

    /**
     * 在文本中抽取标的，按出现顺序返回（去重）。
     * 匹配优先级：全名 > 代码 > 别名，避免“腾讯控股”同时命中“腾讯”。
     */
    fun extract(text: String): List<Instrument> {
        val lower = text.lowercase()
        data class Hit(val start: Int, val end: Int, val instrument: Instrument)

        val hits = mutableListOf<Hit>()
        all.forEach { instrument ->
            val keywords = buildList {
                add(instrument.name.lowercase())
                add(instrument.code.lowercase())
                add(instrument.displayCode.lowercase())
                addAll(instrument.aliases.map { it.lowercase() })
            }
            keywords.forEach { kw ->
                if (kw.isEmpty()) return@forEach
                var from = 0
                while (true) {
                    val idx = lower.indexOf(kw, from)
                    if (idx < 0) break
                    from = idx + 1
                    // 纯数字 / 字母代码需要边界保护，避免“700”命中“1700”、“ma”命中“max”
                    val isCodeLike = kw.all { it.isDigit() || it in 'a'..'z' || it == '.' }
                    if (isCodeLike) {
                        val before = lower.getOrNull(idx - 1)
                        val after = lower.getOrNull(idx + kw.length)
                        if ((before != null && before.isAsciiAlnum()) || (after != null && after.isAsciiAlnum())) continue
                    }
                    hits += Hit(idx, idx + kw.length, instrument)
                }
            }
        }
        // 重叠区间只保留最长匹配（如“港股科技” 优先于 “港股”）
        val resolved = mutableListOf<Hit>()
        hits.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.end - it.start }).forEach { hit ->
            val overlapped = resolved.any { it.start < hit.end && hit.start < it.end }
            if (!overlapped) resolved += hit
        }
        return resolved.sortedBy { it.start }.map { it.instrument }.distinctBy { it.key }
    }

    private fun Char.isAsciiAlnum(): Boolean = this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'
}
