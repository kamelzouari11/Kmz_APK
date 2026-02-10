package com.kmz.taskmanager.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

object SmartParser {
        fun parse(input: String): Pair<String, LocalDateTime?> {
                val today = LocalDate.now()
                var text = input.trim()
                var dateTime: LocalDateTime? = null
                var cleanLabel = text

                // Optimization: if there's a ", ", use the second part as the potential date/time
                val parts = text.split(", ", limit = 2)
                val stringToParse =
                        if (parts.size > 1) {
                                cleanLabel = parts[0]
                                parts[1]
                        } else {
                                text
                        }

                // Regular expressions for different formats
                val timeRegex = """(?:à\s*)?(\d{1,2})[hH](\d{0,2})""".toRegex()
                val dayOfWeekRegex =
                        """(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)""".toRegex(
                                RegexOption.IGNORE_CASE
                        )
                val dateRegex =
                        """(\d{1,2})\s*(jan|fév|mar|avr|mai|juin|juil|aoû|sep|oct|nov|déc)[^\s\d]*(?:\s+(\d{4}))?""".toRegex(
                                RegexOption.IGNORE_CASE
                        )
                val relativeDateRegex =
                        """((?:la\s+)?semaine\s+prochaine|(?:l['’]|aujourd['’])?hui|demain|après\s*demain|ce\s*soir|cet\s*après\s*midi|midi)""".toRegex(
                                RegexOption.IGNORE_CASE
                        )

                val timeMatch = timeRegex.find(stringToParse)
                val dayMatch = dayOfWeekRegex.find(stringToParse)
                val dateMatch = dateRegex.find(stringToParse)
                val relativeMatch = relativeDateRegex.find(stringToParse)

                // Default time logic: if "midi" or "soir" is mentioned, adjust hour
                var hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 12
                val minute =
                        timeMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
                                ?: 0

                if (timeMatch == null && relativeMatch != null) {
                        val relWord = relativeMatch.groupValues[1].lowercase()
                        if (relWord.contains("soir")) hour = 18
                        if (relWord.contains("midi")) hour = 12
                        if (relWord.contains("après midi")) hour = 15
                }

                val time = LocalTime.of(hour, minute)

                if (dateMatch != null) {
                        // Existing dateMatch logic...
                        val day = dateMatch.groupValues[1].toInt()
                        val monthStr = dateMatch.groupValues[2].lowercase()
                        val yearStr = dateMatch.groupValues[3]
                        val month =
                                when (monthStr) {
                                        "jan" -> 1
                                        "fév" -> 2
                                        "mar" -> 3
                                        "avr" -> 4
                                        "mai" -> 5
                                        "juin" -> 6
                                        "juil" -> 7
                                        "aoû" -> 8
                                        "sep" -> 9
                                        "oct" -> 10
                                        "nov" -> 11
                                        "déc" -> 12
                                        else -> 1
                                }
                        val year =
                                if (yearStr.isEmpty()) today.year
                                else if (yearStr.length == 2) 2000 + yearStr.toInt()
                                else yearStr.toInt()
                        dateTime = LocalDateTime.of(LocalDate.of(year, month, day), time)
                } else if (dayMatch != null) {
                        val dayName = dayMatch.groupValues[1].lowercase()
                        val dayOfWeek =
                                when (dayName) {
                                        "lundi" -> java.time.DayOfWeek.MONDAY
                                        "mardi" -> java.time.DayOfWeek.TUESDAY
                                        "mercredi" -> java.time.DayOfWeek.WEDNESDAY
                                        "jeudi" -> java.time.DayOfWeek.THURSDAY
                                        "vendredi" -> java.time.DayOfWeek.FRIDAY
                                        "samedi" -> java.time.DayOfWeek.SATURDAY
                                        "dimanche" -> java.time.DayOfWeek.SUNDAY
                                        else -> java.time.DayOfWeek.MONDAY
                                }
                        var targetDate = today.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                        var targetDateTime = LocalDateTime.of(targetDate, time)

                        // If the date/time is already passed today, jump to next week
                        if (targetDateTime.isBefore(LocalDateTime.now())) {
                                targetDate = today.with(TemporalAdjusters.next(dayOfWeek))
                                targetDateTime = LocalDateTime.of(targetDate, time)
                        }
                        dateTime = targetDateTime
                } else if (relativeMatch != null) {
                        val word =
                                relativeMatch.groupValues[1]
                                        .lowercase()
                                        .replace(Regex("""\s+"""), " ")
                        val targetDate =
                                when {
                                        word.contains("après demain") -> today.plusDays(2)
                                        word.contains("demain") -> today.plusDays(1)
                                        word.contains("semaine prochaine") -> today.plusWeeks(1)
                                        word.contains("hui") ||
                                                word.contains("soir") ||
                                                word.contains("midi") ||
                                                word.contains("après midi") -> today
                                        else -> today
                                }
                        dateTime = LocalDateTime.of(targetDate, time)
                } else if (timeMatch != null) {
                        dateTime = LocalDateTime.of(today, time)
                }

                // If no comma was used, we clean up the text as before
                if (parts.size == 1) {
                        cleanLabel =
                                text.replace(timeRegex, "")
                                        .replace(dayOfWeekRegex, "")
                                        .replace(dateRegex, "")
                                        .replace(relativeDateRegex, "")
                                        .trim()
                                        .replace(Regex("""\s+"""), " ")
                        if (cleanLabel.isEmpty()) cleanLabel = input
                }

                return Pair(cleanLabel, dateTime)
        }
}
