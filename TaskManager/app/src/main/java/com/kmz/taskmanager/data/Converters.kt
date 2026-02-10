package com.kmz.taskmanager.data

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, formatter) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String? {
        return date?.format(formatter)
    }

    @TypeConverter fun fromTaskType(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter fun taskTypeToString(type: TaskType): String = type.name

    @TypeConverter fun fromAlarmLevel(value: String): AlarmLevel = AlarmLevel.valueOf(value)

    @TypeConverter fun alarmLevelToString(level: AlarmLevel): String = level.name

    @TypeConverter fun fromPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter fun priorityToString(priority: Priority): String = priority.name

    @TypeConverter
    fun fromRepeatUnit(value: String?): RepeatUnit? = value?.let { RepeatUnit.valueOf(it) }

    @TypeConverter fun repeatUnitToString(unit: RepeatUnit?): String? = unit?.name
}
