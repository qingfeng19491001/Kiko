package com.kuikly.stockchat.domain.util

/**
 * 轻量日期工具（commonMain 无 java.time），用于把行情接口的
 * "yyyy-MM-dd" / "yyyy-MM-dd HH:mm" 字符串转成毫秒时间戳。
 */
object DateUtil {

    private const val DAY_MS = 86_400_000L

    /** 解析 "yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm[:ss]"，按东八区计算；失败返回 null */
    fun parseToEpochMillis(text: String, offsetHours: Int = 8): Long? {
        val trimmed = text.trim()
        if (trimmed.length < 10) return null
        val year = trimmed.substring(0, 4).toIntOrNull() ?: return null
        val month = trimmed.substring(5, 7).toIntOrNull() ?: return null
        val day = trimmed.substring(8, 10).toIntOrNull() ?: return null
        var hour = 0
        var minute = 0
        if (trimmed.length >= 16) {
            hour = trimmed.substring(11, 13).toIntOrNull() ?: 0
            minute = trimmed.substring(14, 16).toIntOrNull() ?: 0
        }
        val days = daysFromCivil(year, month, day)
        val localMs = days * DAY_MS + (hour * 60L + minute) * 60_000L
        return localMs - offsetHours * 3_600_000L
    }

    /** Howard Hinnant 的 civil → days 算法（自 1970-01-01 起的天数） */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
