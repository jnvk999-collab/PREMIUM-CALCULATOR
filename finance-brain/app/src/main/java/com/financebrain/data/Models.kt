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
    const val TRANSFER = "Transfers"
    const val SALARY = "Salary"
    const val INCOME = "Other Income"
    const val REFUND = "Refunds"
    const val OTHER = "Other"

    val expense = listOf(
        FOOD, GROCERIES, SHOPPING, TRAVEL, FUEL, BILLS, UTILITIES, ENTERTAINMENT,
        HEALTH, EMI, INVESTMENT, RENT, EDUCATION, CASH, TRANSFER, OTHER
    )
    val income = listOf(SALARY, INCOME, REFUND, TRANSFER, OTHER)
    val all = (expense + income).distinct()
}
