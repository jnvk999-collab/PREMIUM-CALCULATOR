package com.financebrain

import com.financebrain.data.BalanceAnchor
import com.financebrain.data.Categories
import com.financebrain.data.Direction
import com.financebrain.data.Insights
import com.financebrain.data.Source
import com.financebrain.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceTest {
    private val day = 86_400_000L
    private val now = 1_757_000_000_000L

    private fun tx(
        amount: Long, direction: Direction, at: Long, tail: String = "1730",
        bank: String = "SBI", balance: Long? = null, kind: String = "BANK",
    ) = Transaction(
        amountPaise = amount, direction = direction, timestamp = at, bank = bank, accountTail = tail,
        counterparty = "SHOP", category = Categories.OTHER, channel = "UPI", reference = null,
        balancePaise = balance, source = Source.SMS, rawText = null, dedupKey = "$bank$tail$at$amount",
        accountKind = kind,
    )

    @Test fun bankReportedBalanceKeepsMovingAfterTheAlert() {
        // The bank quoted ₹20,000 two days ago and has quoted nothing since.
        val rows = listOf(
            tx(1_000_00, Direction.DEBIT, now - 2 * day, balance = 20_000_00),
            tx(3_000_00, Direction.DEBIT, now - day),
            tx(500_00, Direction.CREDIT, now - 3_600_000L),
        )
        assertEquals(17_500_00L, Insights.balanceAt(rows, emptyList(), now + 1))
    }

    @Test fun balanceYouEnterWinsOverAnOlderBankFigureAndRunsForward() {
        val rows = listOf(
            tx(1_000_00, Direction.DEBIT, now - 2 * day, balance = 20_000_00),
            tx(3_000_00, Direction.DEBIT, now - 3_600_000L),
        )
        val anchor = BalanceAnchor("SBI|1730", 9_000_00, now - 2 * 3_600_000L)
        assertEquals(6_000_00L, Insights.balanceAt(rows, listOf(anchor), now + 1))
    }

    @Test fun whatYouTypedBeatsABankFigureQuotedAfterwards() {
        // You checked the bank at noon; a later alert quoting a stale figure must not overwrite you.
        val rows = listOf(
            tx(2_000_00, Direction.DEBIT, now - 3_600_000L, balance = 20_000_00),
        )
        val anchor = BalanceAnchor("SBI|1730", 10_000_00, now - 2 * 3_600_000L)
        assertEquals(8_000_00L, Insights.balanceAt(rows, listOf(anchor), now + 1))
    }

    @Test fun theWalletIsTheStartingFigureLessWhatWentOut() {
        val rows = listOf(
            tx(3_000_00, Direction.DEBIT, now - 3_600_000L),
            tx(500_00, Direction.CREDIT, now - 1_800_000L),
        )
        val anchor = BalanceAnchor("ALL", 20_000_00, now - day)
        val w = Insights.wallet(rows, listOf(anchor), now + 1)!!
        assertEquals(20_000_00L, w.basePaise)
        assertEquals(3_000_00L, w.debitsPaise)
        assertEquals(500_00L, w.creditsPaise)
        assertEquals(17_500_00L, w.paise)
    }

    @Test fun cardSpendsDoNotTouchTheBankBalance() {
        val rows = listOf(
            tx(10_000_00, Direction.DEBIT, now - 2 * day, balance = 20_000_00),
            tx(2_000_00, Direction.DEBIT, now - day, tail = "46", bank = "HDFC", kind = "CARD"),
        )
        assertEquals(20_000_00L, Insights.balanceAt(rows, emptyList(), now + 1))
    }

    @Test fun accountsAddUp() {
        val rows = listOf(
            tx(100_00, Direction.DEBIT, now - day, balance = 5_000_00),
            tx(100_00, Direction.DEBIT, now - day, tail = "8675", bank = "Federal Bank", balance = 7_000_00),
            tx(1_000_00, Direction.DEBIT, now - 3_600_000L, tail = "8675", bank = "Federal Bank"),
        )
        assertEquals(11_000_00L, Insights.balanceAt(rows, emptyList(), now + 1))
    }

    @Test fun noFigureAnywhereMeansNoBalance() {
        assertNull(Insights.balanceAt(listOf(tx(100_00, Direction.DEBIT, now - day)), emptyList(), now + 1))
    }

    @Test fun aCardSpendIsMoneyGoneButPayingTheBillIsNotCountedTwice() {
        val anchor = BalanceAnchor("ALL", 20_000_00, now - day)
        val rows = listOf(
            tx(3_550_00, Direction.DEBIT, now - 3 * 3_600_000L, tail = "2338", bank = "Federal Bank", kind = "CARD"),
            tx(1_000_00, Direction.DEBIT, now - 2 * 3_600_000L).copy(category = Categories.CARD_BILL),
        )
        val w = Insights.wallet(rows, listOf(anchor), now + 1)!!
        assertEquals(3_550_00L, w.debitsPaise)
        assertEquals(16_450_00L, w.paise)
    }

    @Test fun moneyMovedBetweenYourOwnAccountsIsNotSpending() {
        val anchor = BalanceAnchor("ALL", 20_000_00, now - day)
        val rows = listOf(
            tx(5_000_00, Direction.DEBIT, now - 3 * 3_600_000L).copy(isTransfer = true),
            tx(5_000_00, Direction.CREDIT, now - 3 * 3_600_000L, tail = "8675", bank = "Federal Bank").copy(isTransfer = true),
        )
        val w = Insights.wallet(rows, listOf(anchor), now + 1)!!
        assertEquals(0L, w.debitsPaise)
        assertEquals(20_000_00L, w.paise)
    }

    @Test fun paymentsStampedAtMidnightStillComeOffAnOpeningBalance() {
        // Alerts carry a date but no clock time, so a payment can land on the same instant as
        // the opening balance you entered for that day. It still has to count.
        val openingOfToday = 1_757_000_000_000L
        val rows = listOf(tx(4_000_00, Direction.DEBIT, openingOfToday))
        val anchor = BalanceAnchor("ALL", 20_000_00, openingOfToday)
        val w = Insights.wallet(rows, listOf(anchor), openingOfToday + day)!!
        assertEquals(4_000_00L, w.debitsPaise)
        assertEquals(16_000_00L, w.paise)
    }

    @Test fun theWorkingIsAvailableForOneAccount() {
        val rows = listOf(
            tx(1_000_00, Direction.DEBIT, now - 2 * day, balance = 20_000_00),
            tx(3_000_00, Direction.DEBIT, now - day),
            tx(500_00, Direction.CREDIT, now - 3_600_000L),
        )
        val b = Insights.accountBalance(rows, null, now + 1)!!
        assertEquals(20_000_00L, b.basePaise)
        assertEquals(3_000_00L, b.debitsPaise)
        assertEquals(500_00L, b.creditsPaise)
        assertEquals(17_500_00L, b.paise)
    }
}
