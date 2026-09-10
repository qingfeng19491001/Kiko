package com.kuikly.stockchat.domain.util

/**
 * 轻量日期工具（commonMain 无 java.time），用于把行情接口的
 * "yyyy-MM-dd" / "yyyy-MM-dd HH:mm" 字符串转成毫秒时间戳，
 * 以及从毫秒时间戳分解出年月日。
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

    /**
     * 把毫秒时间戳分解为 (year, month, day)，按本地时区（东八区）计算。
     * 使用 Howard Hinnant 的 days → civil 逆运算。
     */
    fun civilFromEpochMillis(epochMillis: Long, offsetHours: Int = 8): Triple<Int, Int, Int> {
        val localMs = epochMillis + offsetHours * 3_600_000L
        val z = (localMs / DAY_MS).toInt()
        return civilFromDays(z)
    }

    /** yyyy-MM-dd 加减自然日；失败返回 null */
    fun shiftYmd(ymd: String, days: Int): String? {
        if (ymd.length < 10) return null
        val year = ymd.substring(0, 4).toIntOrNull() ?: return null
        val month = ymd.substring(5, 7).toIntOrNull() ?: return null
        val day = ymd.substring(8, 10).toIntOrNull() ?: return null
        val (ny, nm, nd) = civilFromDays((daysFromCivil(year, month, day) + days).toInt())
        fun pad(value: Int): String = value.toString().padStart(2, '0')
        return "$ny-${pad(nm)}-${pad(nd)}"
    }

    /** 0=周日 … 6=周六；解析失败返回 null */
    fun dayOfWeek(ymd: String): Int? {
        if (ymd.length < 10) return null
        val year = ymd.substring(0, 4).toIntOrNull() ?: return null
        val month = ymd.substring(5, 7).toIntOrNull() ?: return null
        val day = ymd.substring(8, 10).toIntOrNull() ?: return null
        val days = daysFromCivil(year, month, day)
        return ((days + 4) % 7).toInt().let { if (it < 0) it + 7 else it }
    }

    fun isWeekend(ymd: String): Boolean {
        val dow = dayOfWeek(ymd) ?: return false
        return dow == 0 || dow == 6
    }

    /** 从次日起取 count 个工作日（跳过周末，不处理节假日） */
    fun nextWeekdays(fromYmd: String, count: Int): List<String> {
        val out = mutableListOf<String>()
        var cursor = fromYmd
        var guard = 0
        while (out.size < count && guard < count * 4 + 14) {
            cursor = shiftYmd(cursor, 1) ?: break
            guard += 1
            if (!isWeekend(cursor)) out += cursor
        }
        return out
    }

    /** 毫秒时间戳 → "yyyy-MM-dd HH:mm"（东八区），供分钟级 K 线使用 */
    fun formatEpochMinutes(epochMillis: Long, offsetHours: Int = 8): String {
        val localMs = epochMillis + offsetHours * 3_600_000L
        val days = (localMs / DAY_MS).toInt()
        val (year, month, day) = civilFromDays(days)
        val minuteOfDay = ((localMs % DAY_MS) / 60_000L).toInt()
        fun pad(value: Int): String = value.toString().padStart(2, '0')
        return "$year-${pad(month)}-${pad(day)} ${pad(minuteOfDay / 60)}:${pad(minuteOfDay % 60)}"
    }

    /** Howard Hinnant 的 days → civil 算法（自 1970-01-01 起的天数 → 年月日） */
    private fun civilFromDays(z: Int): Triple<Int, Int, Int> {
        val zz = z + 719468
        val era = if (zz >= 0) zz / 146097 else (zz - 146096) / 146097
        val doe = zz - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple(y + if (m <= 2) 1 else 0, m, d)
    }

    /** Howard Hinnant 的 civil → days 算法（自 1970-01-01 起的天数） */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
