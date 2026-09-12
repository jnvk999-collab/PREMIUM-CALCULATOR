package com.financebrain.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Indian grouping: 12,34,567.89 */
fun formatRupees(paise: Long, showPaise: Boolean = false, sign: Boolean = false): String {
    val negative = paise < 0
    val abs = abs(paise)
    val rupees = abs / 100
    val p = abs % 100
    val s = rupees.toString()
    val grouped = if (s.length <= 3) s else {
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val chunks = StringBuilder()
        var r = rest
        while (r.length > 2) { chunks.insert(0, "," + r.takeLast(2)); r = r.dropLast(2) }
        r + chunks + "," + last3
    }
    val body = if (showPaise || (p != 0L && rupees < 1000)) "₹$grouped.${"%02d".format(p)}" else "₹$grouped"
    return when {
        negative -> "-$body"
        sign -> "+$body"
        else -> body
    }
}

fun compactRupees(paise: Long): String {
    val r = abs(paise) / 100.0
    val body = when {
        r >= 1_00_00_000 -> "₹%.2fCr".format(r / 1_00_00_000)
        r >= 1_00_000 -> "₹%.2fL".format(r / 1_00_000)
        r >= 1_000 -> "₹%.1fk".format(r / 1_000)
        else -> "₹%.0f".format(r)
    }
    return if (paise < 0) "-$body" else body
}

private val dayFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
private val dayYearFmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
private val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)
private val monthFmt = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
private val monthShortFmt = SimpleDateFormat("MMM", Locale.ENGLISH)

fun formatDay(ts: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        sameDay(now, c) -> "Today"
        sameDay(now.apply { add(Calendar.DAY_OF_YEAR, -1) }, c) -> "Yesterday"
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> dayFmt.format(Date(ts))
        else -> dayYearFmt.format(Date(ts))
    }
}

fun formatTime(ts: Long): String = timeFmt.format(Date(ts))
fun formatMonth(ts: Long): String = monthFmt.format(Date(ts))
fun formatMonthShort(ts: Long): String = monthShortFmt.format(Date(ts))

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/**
 * The app's "month" is the salary cycle: it starts on the salary day and ends the day before
 * the next one. With salaryDay = 1 it is the calendar month.
 */
object Cycle {
    @Volatile var salaryDay: Int = 1
}

private fun clampDay(cal: Calendar, day: Int) {
    cal.set(Calendar.DAY_OF_MONTH, minOf(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
}

/** Start of the cycle containing [ts]. */
fun monthStart(ts: Long): Long {
    val sd = Cycle.salaryDay.coerceIn(1, 28)
    val c = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    if (c.get(Calendar.DAY_OF_MONTH) < sd) c.add(Calendar.MONTH, -1)
    clampDay(c, sd)
    return c.timeInMillis
}

/** Exclusive end of the cycle containing [ts] (= start of the next cycle). */
fun monthEnd(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = monthStart(ts); add(Calendar.MONTH, 1); clampDay(this, Cycle.salaryDay.coerceIn(1, 28))
}.timeInMillis

fun shiftMonth(ts: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthStart(ts); add(Calendar.MONTH, delta); clampDay(this, Cycle.salaryDay.coerceIn(1, 28))
}.timeInMillis

/** 1-based day index within the cycle. */
fun dayOfMonth(ts: Long): Int = ((ts - monthStart(ts)) / 86_400_000L).toInt() + 1
/** Number of days in the cycle containing [ts]. */
fun daysInMonth(ts: Long): Int = ((monthEnd(ts) - monthStart(ts) + 43_200_000L) / 86_400_000L).toInt()

/** Label for a cycle: "Sep 2026" for calendar months, "5 Sep – 4 Oct" for salary cycles. */
fun formatCycle(cycleStart: Long): String {
    if (Cycle.salaryDay <= 1) return formatMonth(cycleStart)
    val end = monthEnd(cycleStart) - 86_400_000L
    return "${dayFmt.format(Date(cycleStart))} – ${dayFmt.format(Date(end))}"
}

/** Midnight at the start of the local day containing [ts]. */
fun dayStart(ts: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ts
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis
