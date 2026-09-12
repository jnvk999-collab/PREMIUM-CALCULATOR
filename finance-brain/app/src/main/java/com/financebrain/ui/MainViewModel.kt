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
import com.financebrain.gmail.GmailAccount
import com.financebrain.gmail.GmailAuth
import com.financebrain.gmail.GmailSyncProgress
import com.financebrain.update.UpdateManager
import com.financebrain.update.UpdateState
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
    private val updater = UpdateManager(app)
    private val gmailAccounts = FinanceBrainApp.get(app).gmailAccounts
    private val gmailSyncer = FinanceBrainApp.get(app).gmailSyncer
    val gmailAuth = GmailAuth(app)

    private val _gmail = MutableStateFlow(gmailAccounts.list())
    val gmail: StateFlow<List<GmailAccount>> = _gmail
    val gmailProgress: StateFlow<GmailSyncProgress?> = gmailSyncer.progress
    private val _gmailClientId = MutableStateFlow(gmailAccounts.clientId)
    val gmailClientId: StateFlow<String> = _gmailClientId
    private val _gmailError = MutableStateFlow<String?>(null)
    val gmailError: StateFlow<String?> = _gmailError

    fun setGmailClientId(id: String) { gmailAccounts.clientId = id; _gmailClientId.value = gmailAccounts.clientId }

    fun finishGmailSignIn(data: android.content.Intent?) = viewModelScope.launch {
        _gmailError.value = null
        gmailAuth.complete(data).onSuccess { state ->
            try {
                val email = com.financebrain.gmail.GmailClient(getApplication(), state) {}.profileEmail()
                gmailAccounts.save(email, state)
                _gmail.value = gmailAccounts.list()
                syncGmail()
            } catch (e: Exception) { _gmailError.value = e.message }
        }.onFailure { _gmailError.value = it.message }
    }

    fun removeGmail(email: String) { gmailAccounts.remove(email); _gmail.value = gmailAccounts.list() }

    fun syncGmail() = viewModelScope.launch {
        gmailSyncer.syncAll()
        _gmail.value = gmailAccounts.list()
    }

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    init { checkForUpdate() }

    fun checkForUpdate() {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        viewModelScope.launch { _update.value = UpdateState.Checking; _update.value = updater.check() }
    }

    fun downloadUpdate() {
        val u = (_update.value as? UpdateState.Available)?.update ?: return
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(u, 0f)
            _update.value = updater.download(u) { p -> _update.value = UpdateState.Downloading(u, p) }
        }
    }

    fun installUpdate() {
        val s = _update.value as? UpdateState.ReadyToInstall ?: return
        if (updater.canInstall()) updater.install(s.file) else updater.openInstallPermission()
    }

    fun dismissUpdate() { if (_update.value !is UpdateState.Downloading) _update.value = UpdateState.Idle }

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
            if (full) repo.purgeSms()
            _scan.value = ScanProgress(0, 0, 0, false)
            scanner.scan { _scan.value = it }
        }
    }

    fun setCategory(t: Transaction, category: String, remember: Boolean) =
        viewModelScope.launch { repo.setCategory(t, category, remember) }

    fun setNote(t: Transaction, note: String?) = viewModelScope.launch { repo.setNote(t, note) }
    fun delete(t: Transaction) = viewModelScope.launch { repo.delete(t) }
    fun markSpam(t: Transaction) = viewModelScope.launch { repo.markSpam(t) }

    fun addManual(amountPaise: Long, direction: Direction, name: String, category: String, timestamp: Long, note: String?) =
        viewModelScope.launch { repo.addManual(amountPaise, direction, name, category, timestamp, note) }
}
