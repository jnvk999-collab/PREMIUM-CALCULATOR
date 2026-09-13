package com.financebrain

import com.financebrain.data.Categories
import com.financebrain.data.Direction
import com.financebrain.data.Insights
import com.financebrain.data.Source
import com.financebrain.data.Transaction
import com.financebrain.ui.dayStart
import com.financebrain.ui.monthStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DailyReportTest {
    private val hour = 3_600_000L
    private val day = 86_400_000L
    // A Wednesday at 15:00 local time, so "this week" has Monday and Tuesday behind it.
    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 15, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun tx(amount: Long, at: Long, category: String = Categories.FOOD, name: String = "SHOP") = Transaction(
        amountPaise = amount, direction = Direction.DEBIT, timestamp = at, bank = "SBI", accountTail = "1730",
        counterparty = name, category = category, channel = "UPI", reference = null, balancePaise = null,
        source = Source.SMS, rawText = null, dedupKey = "$name$at$amount",
    )

    @Test fun todayAndThisWeekAreSummedAgainstTheLimits() {
        val today0 = dayStart(now)
        val rows = listOf(
            tx(700_00, today0 + 9 * hour),
            tx(300_00, today0 + 12 * hour, Categories.HEALTH),
            tx(2_000_00, today0 - day + 10 * hour),          // Tuesday
            tx(1_000_00, today0 - 2 * day + 10 * hour),      // Monday
            tx(5_000_00, today0 - 3 * day + 10 * hour),      // Sunday: last week, must not count
        )
        val r = Insights.dailyReport(rows, rows, monthStart(now), now, dailyLimit = 800_00, weeklyLimit = 5_000_00)
        assertEquals(1_000_00L, r.todayPaise)
        assertEquals(2, r.todayCount)
        assertEquals(4_000_00L, r.weekPaise)
        assertEquals(4, r.daysLeftInWeek)
        assertEquals(-200_00L, r.dailyLeftPaise)
        assertEquals(1_000_00L, r.weeklyLeftPaise)
        assertTrue(r.lines.any { it.startsWith("Over today's limit by ₹200") })
        assertTrue(r.lines.any { it.startsWith("₹1,000 left for the week") })
        assertEquals(14, r.last14.size)
        assertEquals(1_000_00L, r.last14.last().paise)
    }

    @Test fun aCategoryRunningAboveItsUsualIsNamed() {
        val cycle = monthStart(now)
        val today0 = dayStart(now)
        val history = ArrayList<Transaction>()
        // Three previous cycles of steady ₹200 a day on food.
        for (d in 1..90) history += tx(200_00, cycle - d * day + 10 * hour, Categories.FOOD, "canteen")
        val thisCycle = listOf(tx(9_000_00, today0 + 10 * hour, Categories.FOOD, "restaurant"))
        val r = Insights.dailyReport(history + thisCycle, thisCycle, cycle, now, 0, 0)
        val food = r.categoryDelta.first { it.category == Categories.FOOD }
        assertTrue(food.usualPaise > 0)
        assertTrue(food.deltaPaise > 0)
        assertTrue(r.lines.any { it.startsWith("${Categories.FOOD} is running") })
    }
}
