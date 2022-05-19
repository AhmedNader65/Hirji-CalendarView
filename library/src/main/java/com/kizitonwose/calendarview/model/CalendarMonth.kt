package com.kizitonwose.calendarview.model

import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import java.io.Serializable
import java.time.YearMonth
import java.util.*

data class CalendarMonth(
    val calendar: Calendar,
    val weekDays: List<List<CalendarDay>>,
    internal val indexInSameMonth: Int,
    internal val numberOfSameMonth: Int
) : Comparable<CalendarMonth>, Serializable {

    val year: Int = calendar.get(Calendar.YEAR)
    val month: Int = calendar.get(Calendar.MONTH) +1

    override fun hashCode(): Int {
        return 31 * calendar.hashCode() +
            weekDays.first().first().hashCode() +
            weekDays.last().last().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        (other as CalendarMonth)
        return calendar == other.calendar &&
            weekDays.first().first() == other.weekDays.first().first() &&
            weekDays.last().last() == other.weekDays.last().last()
    }

    override fun compareTo(other: CalendarMonth): Int {
        val monthResult = calendar.compareTo(other.calendar)
        if (monthResult == 0) { // Same yearMonth
            return indexInSameMonth.compareTo(other.indexInSameMonth)
        }
        return monthResult
    }

    override fun toString(): String {
        return "CalendarMonth { first = ${weekDays.first().first()}, last = ${weekDays.last().last()}} " +
            "indexInSameMonth = $indexInSameMonth, numberOfSameMonth = $numberOfSameMonth"
    }
}
