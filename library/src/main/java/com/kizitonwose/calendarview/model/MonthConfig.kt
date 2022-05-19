package com.kizitonwose.calendarview.model

import android.util.Log
import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import kotlinx.coroutines.Job
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.*

internal data class MonthConfig(
    internal val outDateStyle: OutDateStyle,
    internal val inDateStyle: InDateStyle,
    internal val maxRowCount: Int,
    internal val calendar: Calendar,
    internal val firstDayOfWeek: DayOfWeek,
    internal val hasBoundaries: Boolean,
    internal val job: Job
) {

    internal val months: List<CalendarMonth> = run {
        return@run if (hasBoundaries) {
            generateBoundedMonths(calendar, firstDayOfWeek, maxRowCount, inDateStyle, outDateStyle, job)
        } else {
            generateUnboundedMonths(calendar, firstDayOfWeek, maxRowCount, inDateStyle, outDateStyle, job)
        }
    }

    internal companion object {

        private val uninterruptedJob = Job()

        /**
         * A [YearMonth] will have multiple [CalendarMonth] instances if the [maxRowCount] is
         * less than 6. Each [CalendarMonth] will hold just enough [CalendarDay] instances(weekDays)
         * to fit in the [maxRowCount].
         */
        fun generateBoundedMonths(
            calendar: Calendar,
            firstDayOfWeek: DayOfWeek,
            maxRowCount: Int,
            inDateStyle: InDateStyle,
            outDateStyle: OutDateStyle,
            job: Job = uninterruptedJob
        ): List<CalendarMonth> {
            val months = mutableListOf<CalendarMonth>()
            val generateInDates = when (inDateStyle) {
                InDateStyle.ALL_MONTHS -> true
                InDateStyle.FIRST_MONTH -> true
                InDateStyle.NONE -> false
            }

            val weekDaysGroup =
                generateWeekDays(calendar, firstDayOfWeek, generateInDates, outDateStyle)

            // Group rows by maxRowCount into CalendarMonth classes.
            val calendarMonths = mutableListOf<CalendarMonth>()
            val numberOfSameMonth = weekDaysGroup.size roundDiv maxRowCount
            var indexInSameMonth = 0
            calendarMonths.addAll(weekDaysGroup.chunked(maxRowCount) { monthDays ->
                // Use monthDays.toList() to create a copy of the ephemeral list.
                CalendarMonth(calendar, monthDays.toList(), indexInSameMonth++, numberOfSameMonth)
            })

            months.addAll(calendarMonths)
            return months
        }

        internal fun generateUnboundedMonths(
            calendar: Calendar,
            firstDayOfWeek: DayOfWeek,
            maxRowCount: Int,
            inDateStyle: InDateStyle,
            outDateStyle: OutDateStyle,
            job: Job = uninterruptedJob
        ): List<CalendarMonth> {

            // Generate a flat list of all days in the given month range
            val allDays = mutableListOf<CalendarDay>()

            // If inDates are enabled with boundaries disabled,
            // we show them on the first month only.
            val generateInDates = when (inDateStyle) {
                InDateStyle.FIRST_MONTH, InDateStyle.ALL_MONTHS -> true
                InDateStyle.NONE -> false
            }

            allDays.addAll(
                // We don't generate outDates for any month, they are added manually down below.
                // This is because if outDates are enabled with boundaries disabled, we show them
                // on the last month only.
                generateWeekDays(calendar, firstDayOfWeek, generateInDates, OutDateStyle.NONE).flatten()
            )


            // Regroup data into 7 days. Use toList() to create a copy of the ephemeral list.
            val allDaysGroup = allDays.chunked(7).toList()

            val calendarMonths = mutableListOf<CalendarMonth>()
            val calMonthsCount = allDaysGroup.size roundDiv maxRowCount
            allDaysGroup.chunked(maxRowCount) { ephemeralMonthWeeks ->
                val monthWeeks = ephemeralMonthWeeks.toMutableList()

                // Add the outDates for the last row if needed.
                if (monthWeeks.last().size < 7 && outDateStyle == OutDateStyle.END_OF_ROW || outDateStyle == OutDateStyle.END_OF_GRID) {
                    val lastWeek = monthWeeks.last()
                    val lastDay = lastWeek.last()
                    val outDates = (1..7 - lastWeek.size).map {
                        CalendarDay(lastDay.date.plusDays(it.toLong()), DayOwner.NEXT_MONTH)
                    }
                    monthWeeks[monthWeeks.lastIndex] = lastWeek + outDates
                }

                // Add the outDates needed to make the number of rows in this index match the desired maxRowCount.
                while (monthWeeks.size < maxRowCount && outDateStyle == OutDateStyle.END_OF_GRID ||
                    // This will be true when we add the first inDates and the last week row in the CalendarMonth is not filled up.
                    monthWeeks.size == maxRowCount && monthWeeks.last().size < 7 && outDateStyle == OutDateStyle.END_OF_GRID
                ) {
                    // Since boundaries are disabled hence months will overflow, if we have maxRowCount
                    // set to 6 and the last index has only one row left with some missing dates in it,
                    // e.g the last row has only one day in it, if we attempt to fill the grid(up to maxRowCount)
                    // with outDates and the next month does not provide enough dates to fill the grid,
                    // we get more outDates from the following month.

                    /*  MON   TUE   WED   THU   FRI   SAT   SUN

                        30    31    01    02    03    04    05  => First outDates start here (month + 1)

                        06    07    08    09    10    11    12

                        13    14    15    16    17    18    19

                        20    21    22    23    24    25    26

                        27    28    29    30    01    02    03  => Second outDates start here (month + 2)

                        04    05    06    07    08    09    10  */

                    val lastDay = monthWeeks.last().last()

                    val nextRowDates = (1..7).map {
                        CalendarDay(lastDay.date.plusDays(it.toLong()), DayOwner.NEXT_MONTH)
                    }

                    if (monthWeeks.last().size < 7) {
                        // Update the last week to 7 days instead of adding a new row.
                        // Handles the case when we've added all the first inDates and the
                        // last week row in the CalendarMonth is not filled up to 7 days.
                        monthWeeks[monthWeeks.lastIndex] = (monthWeeks.last() + nextRowDates).take(7)
                    } else {
                        monthWeeks.add(nextRowDates)
                    }
                }

                calendarMonths.add(
                    // numberOfSameMonth is the total number of all months and
                    // indexInSameMonth is basically this item's index in the entire month list.
                    CalendarMonth(calendar, monthWeeks, calendarMonths.size, calMonthsCount)
                )
            }

            return calendarMonths
        }

        /**
         * Generates the necessary number of weeks for a [YearMonth].
         */
        internal fun generateWeekDays(
            calendar: Calendar,
            firstDayOfWeek: DayOfWeek,
            generateInDates: Boolean,
            outDateStyle: OutDateStyle
        ): List<List<CalendarDay>> {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            var thisMonthDays = if (calendar is UmmalquraCalendar) {
                (1..calendar.lengthOfMonth()).map {
                    val cal = UmmalquraCalendar()
                    cal.set(UmmalquraCalendar.DAY_OF_MONTH, it)
                    Log.e("day ${ cal.get(UmmalquraCalendar.DAY_OF_MONTH)}", cal.get(UmmalquraCalendar.WEEK_OF_YEAR).toString())
                    CalendarDay(
                        LocalDate.of(year, month, it),
                        DayOwner.THIS_MONTH,
                        cal.get(UmmalquraCalendar.WEEK_OF_YEAR)
                    )
                }
            } else {
                (1..calendar.getActualMaximum(Calendar.DAY_OF_MONTH)).map {
                    CalendarDay(LocalDate.of(year, month, it), DayOwner.THIS_MONTH)
                }
            }
            val weekDaysGroup = if (generateInDates) {
                var groupByWeekOfMonth = mutableListOf<List<CalendarDay>>()
                if (calendar is UmmalquraCalendar) {
                    val map = thisMonthDays.groupBy {
                        it.weekOfYear
                    }
                    groupByWeekOfMonth = map.values.toMutableList()
                    println(map)

                } else {

                    // Group days by week of month so we can add the in dates if necessary.
                    val weekOfMonthField = WeekFields.of(firstDayOfWeek, 1).weekOfMonth()
                    Log.e("weekOfMonthField", weekOfMonthField.getDisplayName(Locale.getDefault()))
                    Log.e("weekOfMonthField", weekOfMonthField.toString())
                    Log.e("date.get", thisMonthDays[0].date.get(weekOfMonthField).toString())
                    val map = thisMonthDays.groupBy { it.date.get(weekOfMonthField) }
                    groupByWeekOfMonth = map.values.toMutableList()
                    println(map)
                    // Add in-dates if necessary
                }
                val firstWeek = groupByWeekOfMonth.first()
                if (firstWeek.size < 7) {
                    val inDates = if (calendar is UmmalquraCalendar) {
                        val lastMonthCalender = UmmalquraCalendar()
                        lastMonthCalender.set(UmmalquraCalendar.MONTH, calendar.get(UmmalquraCalendar.MONTH) - 1)
                        Log.e("last month length", lastMonthCalender.lengthOfMonth().toString())
                        (1..lastMonthCalender.lengthOfMonth()).toList()
                            .takeLast(7 - firstWeek.size).map {
                                CalendarDay(
                                    LocalDate.of(calendar.get(UmmalquraCalendar.YEAR), calendar.get(UmmalquraCalendar.MONTH)+1 , it),
                                    DayOwner.PREVIOUS_MONTH
                                )
                            }
                    } else {
                        val lastMonthCalender = Calendar.getInstance()
                        lastMonthCalender.set(Calendar.MONTH, calendar.get(Calendar.MONTH) - 1)
                        (1..lastMonthCalender.getActualMaximum(Calendar.DAY_OF_MONTH)).toList()
                            .takeLast(7 - firstWeek.size).map {
                                CalendarDay(
                                    LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH)+1 , it),
                                    DayOwner.PREVIOUS_MONTH
                                )
                            }
                    }

                    groupByWeekOfMonth[0] = inDates + firstWeek
                }
                groupByWeekOfMonth
            } else {
                // Group days by 7, first day shown on the month will be day 1.
                // Use toMutableList() to create a copy of the ephemeral list.
                thisMonthDays.chunked(7).toMutableList()
            }

            if (outDateStyle == OutDateStyle.END_OF_ROW || outDateStyle == OutDateStyle.END_OF_GRID) {
                // Add out-dates for the last row.
                if (weekDaysGroup.last().size < 7) {
                    val lastWeek = weekDaysGroup.last()
                    val lastDay = lastWeek.last()
                    val outDates = (1..7 - lastWeek.size).map {
                        CalendarDay(LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH)+2 , it), DayOwner.NEXT_MONTH)
                    }
                    weekDaysGroup[weekDaysGroup.lastIndex] = lastWeek + outDates
                }

                // Add more rows to form a 6 x 7 grid
                if (outDateStyle == OutDateStyle.END_OF_GRID) {
                    while (weekDaysGroup.size < 6) {
                        val lastDay = weekDaysGroup.last().last()
                        val nextRowDates = (1..7).map {
                            CalendarDay(lastDay.date.plusDays(it.toLong()), DayOwner.NEXT_MONTH)
                        }
                        weekDaysGroup.add(nextRowDates)
                    }
                }
            }

            return weekDaysGroup
        }
    }
}

/**
 * We want the remainder to be added as the division result.
 * E.g: 5/2 should be 3.
 */
private infix fun Int.roundDiv(other: Int): Int {
    val div = this / other
    val rem = this % other
    // Add the last value dropped from div if rem is not zero
    return if (rem == 0) div else div + 1
}
