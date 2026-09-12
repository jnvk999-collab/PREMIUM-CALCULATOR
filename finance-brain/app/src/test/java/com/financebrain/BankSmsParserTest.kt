package com.financebrain

import com.financebrain.data.Categories
import com.financebrain.data.Direction
import com.financebrain.parser.BankSmsParser
import com.financebrain.parser.Categorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsParserTest {
    private val now = 1_757_000_000_000L

    private fun p(sender: String, body: String) = BankSmsParser.parse(sender, body, now)

    @Test fun sbiUpiDebit() {
        val t = p("VM-SBIUPI", "Dear UPI user A/C X4321 debited by 250.0 on date 12Sep26 trf to SWIGGY Refno 425512345678. If not u? call 1800111109. -SBI")
        assertNotNull(t); t!!
        assertEquals(25000L, t.amountPaise); assertEquals(Direction.DEBIT, t.direction)
        assertEquals("SBI", t.bank); assertEquals("4321", t.accountTail)
        assertEquals("SWIGGY", t.counterparty); assertEquals("UPI", t.channel)
        assertEquals("425512345678", t.reference)
    }

    @Test fun sbiCreditWithBalance() {
        val t = p("AD-SBIINB", "Your A/C XXXXX554321 Credited INR 45,000.00 on 01/09/26 -Deposit by transfer from RAMESH KUMAR. Avl Bal INR 1,20,450.55 -SBI")
        assertNotNull(t); t!!
        assertEquals(4_500_000L, t.amountPaise); assertEquals(Direction.CREDIT, t.direction)
        assertEquals("4321", t.accountTail); assertEquals(12_045_055L, t.balancePaise)
        assertEquals("RAMESH KUMAR", t.counterparty)
    }

    @Test fun hdfcUpiSent() {
        val t = p("VM-HDFCBK", "Sent Rs.1200.00 From HDFC Bank A/C *7788 To ZOMATO On 11/09/26 Ref 526012345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808")
        assertNotNull(t); t!!
        assertEquals(120_000L, t.amountPaise); assertEquals("HDFC", t.bank); assertEquals("7788", t.accountTail)
        assertEquals("ZOMATO", t.counterparty); assertEquals(Direction.DEBIT, t.direction)
    }

    @Test fun hdfcSalaryCredit() {
        val t = p("AD-HDFCBK", "Update! INR 85,000.00 deposited in HDFC Bank A/c XX7788 on 01-SEP-26 for NEFT Cr-ACME TECHNOLOGIES PVT LTD-SALARY SEP. Avl bal INR 1,05,230.00")
        assertNotNull(t); t!!
        assertEquals(8_500_000L, t.amountPaise); assertEquals(Direction.CREDIT, t.direction)
        assertEquals(10_523_000L, t.balancePaise); assertEquals("NEFT", t.channel)
        assertEquals(Categories.SALARY, Categorizer.categorize(t.counterparty, t.channel, t.direction, "NEFT Cr-ACME SALARY"))
    }

    @Test fun hdfcCardSpend() {
        val t = p("VM-HDFCBK", "Spent Rs.2499.00 On HDFC Bank Card x9012 At AMAZON PAY INDIA On 2026-09-10:14:22:31.Avl Lmt Rs.1,47,501.00.Not You? Call 18002586161")
        assertNotNull(t); t!!
        assertEquals(249_900L, t.amountPaise); assertEquals("CARD", t.accountKind); assertEquals("9012", t.accountTail)
        assertEquals("AMAZON PAY INDIA", t.counterparty)
        assertEquals(Categories.SHOPPING, Categorizer.categorize(t.counterparty, t.channel, t.direction, null))
    }

    @Test fun iciciDebit() {
        val t = p("VM-ICICIB", "ICICI Bank Acct XX123 debited for Rs 540.00 on 09-Sep-26; RAPIDO BIKE TAXI credited. UPI:524512345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.")
        assertNotNull(t); t!!
        assertEquals(54_000L, t.amountPaise); assertEquals(Direction.DEBIT, t.direction)
        assertEquals("ICICI", t.bank); assertEquals("123", t.accountTail)
        assertEquals("RAPIDO BIKE TAXI", t.counterparty)
        assertEquals(Categories.TRAVEL, Categorizer.categorize(t.counterparty, t.channel, t.direction, null))
    }

    @Test fun iciciCredit() {
        val t = p("VM-ICICIB", "Dear Customer, Acct XX123 is credited with Rs 3,000.00 on 08-Sep-26 from PRIYA S. UPI:524498765432 - ICICI Bank.")
        assertNotNull(t); t!!
        assertEquals(300_000L, t.amountPaise); assertEquals(Direction.CREDIT, t.direction); assertEquals("PRIYA S", t.counterparty)
    }

    @Test fun federalDebit() {
        val t = p("VM-FEDBNK", "Rs 899.00 debited from your A/c XX5566 on 07SEP2026 towards UPI/525512345678/NETFLIX ENTERTAINMENT. Avl Bal Rs 22,110.40 -Federal Bank")
        assertNotNull(t); t!!
        assertEquals(89_900L, t.amountPaise); assertEquals("Federal Bank", t.bank); assertEquals("5566", t.accountTail)
        assertEquals("NETFLIX ENTERTAINMENT", t.counterparty); assertEquals(2_211_040L, t.balancePaise)
        assertEquals(Categories.ENTERTAINMENT, Categorizer.categorize(t.counterparty, t.channel, t.direction, null))
    }

    @Test fun federalCredit() {
        val t = p("VM-FEDBNK", "Your A/c XX5566 is credited with Rs 12,500.00 on 05SEP2026 by NEFT from ARUN. Avl Bal Rs 23,009.40 -Federal Bank")
        assertNotNull(t); t!!
        assertEquals(1_250_000L, t.amountPaise); assertEquals(Direction.CREDIT, t.direction)
    }

    @Test fun phonePeVpaDebit() {
        val t = p("AD-HDFCBK", "Rs.150.00 debited from a/c **7788 on 06-09-26 to VPA chaiwala.9876@ybl (UPI Ref No 525287654321). Not you? Call 18002586161")
        assertNotNull(t); t!!
        assertEquals(15_000L, t.amountPaise); assertEquals("chaiwala.9876@ybl", t.counterparty); assertEquals("UPI", t.channel)
    }

    @Test fun ignoresOtpPromoAndFailed() {
        assertNull(p("VM-SBIINB", "123456 is your OTP for txn of Rs.5000.00 at AMAZON. Do not share. -SBI"))
        assertNull(p("VM-HDFCBK", "Your EMI of Rs 4,500.00 will be debited from HDFC Bank A/c XX7788 on 05-09-26."))
        assertNull(p("VM-ICICIB", "Transaction of Rs 540.00 from ICICI Bank Acct XX123 has been declined due to insufficient balance."))
        assertNull(p("JD-PROMO", "Get pre-approved loan of Rs 5,00,000 today! Apply now."))
        assertNull(p("AM-DELHVR", "Your order is out for delivery. Rs 340 will be collected."))
    }

    @Test fun rejectsPromotionalTraffic() {
        // DLT promotional header
        assertNull(p("VM-HDFCBK-P", "Rs.500 debited? No! Get Rs.500 cashback on your HDFC Bank Card x1234 when you spend Rs.5000 at Amazon. T&C apply."))
        // Phone-number sender
        assertNull(p("+919876543210", "Your A/c X1234 debited Rs.5000 for loan. Call now to apply for personal loan up to 5 lakh -SBI"))
        // Marketing words plus a link, even with an account tail
        assertNull(p("VM-ICICIB", "Dear Customer, get a pre-approved personal loan on your ICICI Bank Acct XX123. Rs 5,00,000 credited in 3 sec. Apply now: https://icici.bank/xyz T&C"))
        // No account, reference or balance at all
        assertNull(p("AD-SBIPSG", "Rs.2000 credited as cashback! Shop with SBI Card and earn rewards. Offer valid till 30 Sep."))
        // Card promo with amount and tail but sales language
        assertNull(p("VM-HDFCBK", "Spend Rs.3000 on HDFC Bank Card x9012 and get 10% off up to Rs.500 at Flipkart Big Billion Days. Offer till 30-09-26. T&C apply."))
    }

    @Test fun keepsGenuineAlertWithNotYouLink() {
        // HDFC genuine alerts carry a ref and a "Not You?" line; they must survive.
        val t = p("VM-HDFCBK", "Sent Rs.320.00 From HDFC Bank A/C *7788 To BIG BAZAAR On 10/09/26 Ref 525012345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808")
        assertNotNull(t)
    }

    @Test fun atmWithdrawal() {
        val t = p("VM-SBIINB", "Dear Customer, Rs.5000.00 withdrawn at SBI ATM S1ABC123 from A/cX4321 on 04Sep26. Avl Bal Rs.98,000.00 -SBI")
        assertNotNull(t); t!!
        assertEquals("ATM", t.channel); assertEquals(Direction.DEBIT, t.direction)
        assertEquals(Categories.CASH, Categorizer.categorize(t.counterparty, t.channel, t.direction, null))
    }

    @Test fun indianGroupingFormatter() {
        assertEquals("₹1,20,450", com.financebrain.ui.formatRupees(12_045_055L))
        assertEquals("₹250", com.financebrain.ui.formatRupees(25_000L))
        assertEquals("₹999.50", com.financebrain.ui.formatRupees(99_950L))
        assertEquals("₹12,34,56,789", com.financebrain.ui.formatRupees(123_456_789_00L))
    }
}
