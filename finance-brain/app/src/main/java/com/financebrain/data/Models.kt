package com.financebrain.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Direction { DEBIT, CREDIT }

enum class Source { SMS, MANUAL, EMAIL, STATEMENT }

/** One money movement. Amounts are stored in paise to avoid floating point drift. */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupKey"], unique = true), Index(value = ["timestamp"])]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: Direction,
    val timestamp: Long,
    val bank: String,
    val accountTail: String?,
    val counterparty: String,
    val category: String,
    val channel: String,          // UPI, CARD, NEFT, ATM, CASH, IMPS, OTHER
    val reference: String?,
    val balancePaise: Long?,
    val note: String? = null,
    val source: Source,
    val rawText: String?,
    val dedupKey: String,
    val isTransfer: Boolean = false,
    val userEdited: Boolean = false,
    val accountKind: String = "BANK",   // BANK, CARD, INVEST
)

/** A bank account learned from alerts. Balance is the last one a message reported. */
@Entity(tableName = "accounts", primaryKeys = ["bank", "accountTail"])
data class Account(
    val bank: String,
    val accountTail: String,
    val balancePaise: Long?,
    val balanceAt: Long?,
    val kind: String = "BANK",   // BANK, CARD, WALLET
)

/** A (bank, last digits) pair seen in transactions. */
data class AccountRef(val bank: String, val accountTail: String, val accountKind: String) {
    val key get() = "$bank|$accountTail"
    val label get() = (if (accountKind == "CARD") "$bank card" else bank) + " ··$accountTail"
}

/** Remembered category for a merchant after the user corrects it once. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val category: String,
)

/** Tracks which SMS rows were already processed. */
@Entity(tableName = "processed_sms")
data class ProcessedSms(
    @PrimaryKey val smsId: Long,
    val parsed: Boolean,
)

/**
 * A balance the user typed in. From this point the app keeps the balance running:
 * credits add, debits subtract. key is "ALL" for the combined balance, or "bank|tail".
 */
@Entity(tableName = "balance_anchors")
data class BalanceAnchor(
    @PrimaryKey val key: String,
    val amountPaise: Long,
    val at: Long,
) {
    val isTotal get() = key == "ALL"
    val bank get() = key.substringBefore('|')
    val tail get() = key.substringAfter('|', "")
}

/** Tracks which Gmail messages were already processed. */
@Entity(tableName = "processed_email")
data class ProcessedEmail(
    @PrimaryKey val messageId: String,
    val parsed: Boolean,
)

object Categories {
    const val FOOD = "Food & Dining"
    const val GROCERIES = "Groceries"
    const val SHOPPING = "Shopping"
    const val TRAVEL = "Travel"
    const val FUEL = "Fuel"
    const val BILLS = "Bills & Recharge"
    const val UTILITIES = "Utilities"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTH = "Health"
    const val EMI = "EMI & Loans"
    const val INVESTMENT = "Investments"
    const val RENT = "Rent"
    const val EDUCATION = "Education"
    const val CASH = "Cash Withdrawal"
    const val CARD_BILL = "Card Bill Payment"
    const val TRANSFER = "Transfers"
    const val SALARY = "Salary"
    const val INCOME = "Other Income"
    const val REFUND = "Refunds"
    const val OTHER = "Other"

    val expense = listOf(
        FOOD, GROCERIES, SHOPPING, TRAVEL, FUEL, BILLS, UTILITIES, ENTERTAINMENT,
        HEALTH, EMI, INVESTMENT, RENT, EDUCATION, CASH, CARD_BILL, TRANSFER, OTHER
    )
    val income = listOf(SALARY, INCOME, REFUND, TRANSFER, OTHER)
    val all = (expense + income).distinct()
}


/** A credit card, auto-detected from card alerts or added by hand. key = "bank|tail". */
@Entity(tableName = "credit_cards")
data class CreditCard(
    @PrimaryKey val key: String,
    val name: String,
    val bank: String,
    val tail: String,
    val limitPaise: Long?,
    val billingDay: Int,        // statement generation day (1-28)
    val dueDaysAfter: Int = 20, // payment due this many days after billing
    val color: Int = 0,
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "🎯",
    val targetPaise: Long,
    val savedPaise: Long = 0,
    val deadline: Long?,        // epoch ms or null
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Money someone owes you. */
@Entity(tableName = "receivables")
data class Receivable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val from: String,
    val amountPaise: Long,
    val dueDate: Long?,
    val notes: String? = null,
    val received: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Informal loan you took (friend, family, employer advance). */
@Entity(tableName = "informal_loans")
data class InformalLoan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lender: String,
    val amountPaise: Long,
    val takenDate: Long,
    val dueDate: Long?,
    val notes: String? = null,
    val repaid: Boolean = false,
)

/** A category you want to keep under a monthly limit. */
@Entity(tableName = "controlled_categories")
data class ControlledCategory(
    @PrimaryKey val category: String,
    val monthlyLimitPaise: Long,
    val note: String? = null,
)

/** A category you want to spend nothing on. Any spend in a cycle is a breach. */
@Entity(tableName = "zero_tolerance")
data class ZeroTolerance(
    @PrimaryKey val category: String,
    val note: String? = null,
    val since: Long = System.currentTimeMillis(),
)

/** Self-fine jar entries and regret log entries. kind = FINE or REGRET. */
@Entity(tableName = "discipline_entries")
data class DisciplineEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amountPaise: Long,
    val reason: String,
    val at: Long = System.currentTimeMillis(),
)
