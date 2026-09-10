package com.kuikly.stockchat.domain.model

/**
 * 交易市场。
 *
 * @property label 中文名称
 * @property suffix 展示代码后缀，如 00700.HK
 * @property currency 计价货币
 * @property tencentPrefix 腾讯行情接口的市场前缀，如 hk00700 / sh600519
 */
enum class Market(
    val label: String,
    val suffix: String,
    val currency: String,
    val tencentPrefix: String,
) {
    HK("港股", "HK", "HKD", "hk"),
    SH("沪市", "SH", "CNY", "sh"),
    SZ("深市", "SZ", "CNY", "sz"),
    US("美股", "US", "USD", "us"),
}

enum class InstrumentType {
    STOCK,
    INDEX,
}

/**
 * 标的（个股 / 指数）静态信息。
 */
data class Instrument(
    val code: String,
    val name: String,
    val market: Market,
    val type: InstrumentType = InstrumentType.STOCK,
    val aliases: List<String> = emptyList(),
    val sector: String = "",
    /** 头像文字（取名称首字） */
    val logoText: String = name.take(1),
    /** 头像底色 ARGB */
    val logoColor: Long = 0xFF2B6CF6,
    /** 腾讯行情接口使用的代码（指数与个股规则不同时可覆盖） */
    private val tencentCodeOverride: String? = null,
) {
    fun tencentCodeOverrideOrNull(): String? = tencentCodeOverride
    /** 展示代码，如 00700.HK / 600519.SH / HSI.HK */
    val displayCode: String
        get() = "$code.${market.suffix}"

    /** 腾讯行情接口的完整代码，如 hk00700 / hkHSI / sh000001 */
    val tencentSymbol: String
        get() = market.tencentPrefix + (tencentCodeOverride ?: code)

    val isIndex: Boolean
        get() = type == InstrumentType.INDEX

    /** 唯一 key，用于自选、缓存等 */
    val key: String
        get() = tencentSymbol
}
