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

    data class LocalClock(
        val date: String,
        val hhmm: String,
        val hour: Int,
        val minute: Int,
    )

    /** 把 epoch 毫秒格式化到指定时区的日历日和 HH:mm。 */
    fun toLocalClock(epochMs: Long, offsetHours: Int): LocalClock? {
        val localMs = epochMs + offsetHours * 3_600_000L
        if (localMs < 0) return null
        val days = localMs / DAY_MS
        val msInDay = localMs % DAY_MS
        val hour = (msInDay / 3_600_000L).toInt()
        val minute = ((msInDay % 3_600_000L) / 60_000L).toInt()
        val (year, month, day) = civilFromDays(days)
        val date = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        val hhmm = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        return LocalClock(date, hhmm, hour, minute)
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

    /** Howard Hinnant 的 days → civil 逆运算。 */
    private fun civilFromDays(z: Long): Triple<Int, Int, Int> {
        val zz = z + 719468L
        val era = (if (zz >= 0) zz else zz - 146096) / 146097
        val doe = zz - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        var y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + if (mp < 10) 3 else -9
        if (m <= 2) y += 1
        return Triple(y.toInt(), m.toInt(), d.toInt())
    }
}
