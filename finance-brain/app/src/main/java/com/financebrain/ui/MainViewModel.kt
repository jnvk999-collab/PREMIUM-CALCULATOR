package com.financebrain.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.financebrain.FinanceBrainApp
import com.financebrain.data.Account
import com.financebrain.data.CategoryTotal
import com.financebrain.data.Direction
import com.financebrain.data.Insights
import com.financebrain.data.MonthSummary
import com.financebrain.data.Recurring
import com.financebrain.data.Transaction
import com.financebrain.sms.ScanProgress
import com.financebrain.sms.SmsInboxScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val month: Long,
    val monthTransactions: List<Transaction> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val incomePaise: Long = 0,
    val expensePaise: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val daily: LongArray = LongArray(0),
    val recurring: List<Recurring> = emptyList(),
    val months: List<MonthSummary> = emptyList(),
    val topMerchants: List<Pair<String, Long>> = emptyList(),
    val totalCount: Int = 0,
) {
    val totalBalancePaise: Long get() = accounts.sumOf { it.balancePaise ?: 0 }
    val savedPaise: Long get() = incomePaise - expensePaise
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FinanceBrainApp.get(app).repository
    private val scanner = SmsInboxScanner(app, repo)

    private val _month = MutableStateFlow(monthStart(System.currentTimeMillis()))
    val month: StateFlow<Long> = _month

    private val _scan = MutableStateFlow<ScanProgress?>(null)
    val scan: StateFlow<ScanProgress?> = _scan

    val state: StateFlow<HomeState> = combine(_month, repo.transactions, repo.accounts) { m, all, accounts ->
        val end = monthEnd(m)
        val inMonth = all.filter { it.timestamp in m until end }
        val now = System.currentTimeMillis()
        HomeState(
            month = m,
            monthTransactions = inMonth,
            allTransactions = all,
            accounts = accounts,
            incomePaise = inMonth.filter(Insights::isIncome).sumOf { it.amountPaise },
            expensePaise = inMonth.filter(Insights::isSpend).sumOf { it.amountPaise },
            categories = Insights.categoryTotals(inMonth),
            daily = Insights.dailySpend(inMonth, daysInMonth(m)),
            recurring = Insights.recurring(all, now),
            months = Insights.monthSeries(all, 6, m),
            topMerchants = Insights.topMerchants(inMonth),
            totalCount = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(_month.value))

    fun setMonth(m: Long) { _month.value = monthStart(m) }
    fun shiftMonth(delta: Int) { _month.value = shiftMonth(_month.value, delta) }

    fun scanInbox(full: Boolean = false) {
        if (_scan.value?.done == false) return
        viewModelScope.launch {
            if (full) repo.resetSmsProgress()
            _scan.value = ScanProgress(0, 0, 0, false)
            scanner.scan { _scan.value = it }
        }
    }

    fun setCategory(t: Transaction, category: String, remember: Boolean) =
        viewModelScope.launch { repo.setCategory(t, category, remember) }

    fun setNote(t: Transaction, note: String?) = viewModelScope.launch { repo.setNote(t, note) }
    fun delete(t: Transaction) = viewModelScope.launch { repo.delete(t) }

    fun addManual(amountPaise: Long, direction: Direction, name: String, category: String, timestamp: Long, note: String?) =
        viewModelScope.launch { repo.addManual(amountPaise, direction, name, category, timestamp, note) }
}
