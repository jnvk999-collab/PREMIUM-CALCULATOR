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
    val r = paise / 100.0
    return when {
        r >= 1_00_00_000 -> "₹%.2fCr".format(r / 1_00_00_000)
        r >= 1_00_000 -> "₹%.2fL".format(r / 1_00_000)
        r >= 1_000 -> "₹%.1fk".format(r / 1_000)
        else -> "₹%.0f".format(r)
    }
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

fun monthStart(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun monthEnd(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = monthStart(ts); add(Calendar.MONTH, 1)
}.timeInMillis

fun shiftMonth(ts: Long, delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthStart(ts); add(Calendar.MONTH, delta)
}.timeInMillis

fun dayOfMonth(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)
fun daysInMonth(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.getActualMaximum(Calendar.DAY_OF_MONTH)
