package com.football.footballapp.data.model

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

fun Match.localCalendarDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
    runCatching {
        OffsetDateTime.parse(utcDate)
            .atZoneSameInstant(zoneId)
            .toLocalDate()
    }.recoverCatching {
        LocalDate.parse(utcDate.substringBefore('T'))
    }.getOrNull()

