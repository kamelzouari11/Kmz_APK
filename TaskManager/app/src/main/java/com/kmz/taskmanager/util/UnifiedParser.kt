package com.kmz.taskmanager.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Unified date parser used for both the "échéance" input field and the quick-postpone shortcut.
 *
 * Two syntaxes, same rules:
 *
 *   Compact  [p|+][n][h|j|s|m]  → add n units to [base]   (base = now when null)
 *   Compact  [m|-][n][h|j|s|m]  → subtract n units from [base]
 *   Natural language (French)   → absolute date; [base] is ignored
 *
 * Returns null for unrecognised input or a result that would be in the past.
 */
object UnifiedParser {

    // matches e.g. "p2s", "+3j", "m1h", "-1m"  (case-insensitive)
    private val compactRegex =
        Regex("""^\s*([p+m-])(\d+)([hjsm])\s*$""", RegexOption.IGNORE_CASE)

    fun parse(
        input: String,
        base: LocalDateTime? = null,
        defaultTime: LocalTime = LocalTime.of(9, 0)
    ): LocalDateTime? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // --- compact shortcut ---
        val cm = compactRegex.find(trimmed)
        if (cm != null) {
            val sign = cm.groupValues[1]
            val forward = sign == "+" || sign.equals("p", ignoreCase = true)
            val direction = if (forward) 1L else -1L
            val amount = cm.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val from = base ?: LocalDateTime.now().withSecond(0).withNano(0)
            val candidate = when (cm.groupValues[3].lowercase()) {
                "h" -> from.plusHours(direction * amount)
                "j" -> from.plusDays(direction * amount)
                "s" -> from.plusWeeks(direction * amount)
                "m" -> from.plusMonths(direction * amount)
                else -> return null
            }
            return candidate.takeIf { !it.isBefore(LocalDateTime.now()) }
        }

        // --- natural language (French) ---
        return parseNatural(trimmed, defaultTime)
    }

    private fun parseNatural(text: String, defaultTime: LocalTime): LocalDateTime? {
        val today = LocalDate.now()

        val timeRegex =
            """(?:à\s*)?\b(\d{1,2})[hH](\d{2})?\b""".toRegex()
        val dayOfWeekRegex =
            """(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)""".toRegex(RegexOption.IGNORE_CASE)
        val dateRegex =
            """(\d{1,2})\s*(janv|févr|fevr|mars|avr|mai(?=\s|$)|juin|juil|août|aout|sept|oct|nov|déc|dec)[^\s\d]*(?:\s+(\d{4}))?""".toRegex(RegexOption.IGNORE_CASE)
        val relativeDateRegex =
            """((?:la\s+)?semaine\s+prochaine|(?:l['']|aujourd[''])?hui|après\s*demain|demain|ce\s*matin(?=\s|$)|ce\s*soir|cet\s*après\s*midi|matin(?=\s|$)|midi)""".toRegex(RegexOption.IGNORE_CASE)
        val durationRegex =
            """\bdans\s+(\d+)\s*(jours?|j|semaines?|s|mois|m)\b""".toRegex(RegexOption.IGNORE_CASE)

        val timeMatch = timeRegex.find(text)
        val dayMatch = dayOfWeekRegex.find(text)
        val dateMatch = dateRegex.find(text)
        val relativeMatch = relativeDateRegex.find(text)
        val durationMatch = durationRegex.find(text)

        var hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: defaultTime.hour
        val minute =
            timeMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                ?: defaultTime.minute

        if (timeMatch == null && relativeMatch != null) {
            val w = relativeMatch.groupValues[1].lowercase()
            if (w.contains("matin")) hour = 8
            if (w.contains("soir")) hour = 18
            if (w.contains("midi")) hour = 12
            if (w.contains("après midi")) hour = 15
        }

        val time = LocalTime.of(hour, minute)

        // absolute date
        if (dateMatch != null) {
            val day = dateMatch.groupValues[1].toInt()
            val monthStr = dateMatch.groupValues[2].lowercase()
            val yearStr = dateMatch.groupValues[3]
            val month = when (monthStr) {
                "janv" -> 1
                "févr", "fevr" -> 2
                "mars" -> 3
                "avr" -> 4
                "mai" -> 5
                "juin" -> 6
                "juil" -> 7
                "août", "aout" -> 8
                "sept" -> 9
                "oct" -> 10
                "nov" -> 11
                "déc", "dec" -> 12
                else -> 1
            }
            var year = if (yearStr.isEmpty()) today.year else yearStr.toInt()
            val dt = runCatching {
                if (yearStr.isEmpty() && LocalDate.of(year, month, day).isBefore(today)) year += 1
                LocalDateTime.of(LocalDate.of(year, month, day), time)
            }.getOrNull() ?: return null
            return dt.takeIf { !it.isBefore(LocalDateTime.now()) }
        }

        // duration  "dans 3 jours"
        if (durationMatch != null) {
            val n = durationMatch.groupValues[1].toIntOrNull() ?: 0
            val u = durationMatch.groupValues[2].lowercase()
            val target = when {
                u == "j" || u.startsWith("jour") -> today.plusDays(n.toLong())
                u == "s" || u.startsWith("semaine") -> today.plusWeeks(n.toLong())
                u == "m" || u == "mois" -> today.plusMonths(n.toLong())
                else -> today
            }
            return LocalDateTime.of(target, time).takeIf { !it.isBefore(LocalDateTime.now()) }
        }

        // day of week  "lundi", "vendredi"
        if (dayMatch != null) {
            val dow = when (dayMatch.groupValues[1].lowercase()) {
                "lundi" -> java.time.DayOfWeek.MONDAY
                "mardi" -> java.time.DayOfWeek.TUESDAY
                "mercredi" -> java.time.DayOfWeek.WEDNESDAY
                "jeudi" -> java.time.DayOfWeek.THURSDAY
                "vendredi" -> java.time.DayOfWeek.FRIDAY
                "samedi" -> java.time.DayOfWeek.SATURDAY
                "dimanche" -> java.time.DayOfWeek.SUNDAY
                else -> java.time.DayOfWeek.MONDAY
            }
            var target = today.with(TemporalAdjusters.nextOrSame(dow))
            var dt = LocalDateTime.of(target, time)
            if (dt.isBefore(LocalDateTime.now())) {
                target = today.with(TemporalAdjusters.next(dow))
                dt = LocalDateTime.of(target, time)
            }
            return dt.takeIf { !it.isBefore(LocalDateTime.now()) }
        }

        // relative date  "demain", "après-demain", "semaine prochaine", "ce soir"…
        if (relativeMatch != null) {
            val word = relativeMatch.groupValues[1].lowercase().replace(Regex("""\s+"""), " ")
            val target = when {
                word.contains("après demain") -> today.plusDays(2)
                word.contains("demain") -> today.plusDays(1)
                word.contains("semaine prochaine") -> today.plusWeeks(1)
                else -> today
            }
            var dt = LocalDateTime.of(target, time)
            if (dt.isBefore(LocalDateTime.now())) dt = dt.plusDays(1)
            return dt.takeIf { !it.isBefore(LocalDateTime.now()) }
        }

        // time only  "14h30"
        if (timeMatch != null) {
            var dt = LocalDateTime.of(today, time)
            if (dt.isBefore(LocalDateTime.now())) dt = dt.plusDays(1)
            return dt.takeIf { !it.isBefore(LocalDateTime.now()) }
        }

        return null
    }
}
