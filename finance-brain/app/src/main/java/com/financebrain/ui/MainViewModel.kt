package com.financebrain.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.financebrain.FinanceBrainApp
import com.financebrain.brain.BrainAnalyzer
import com.financebrain.brain.BrainAsk
import com.financebrain.brain.BrainReport
import com.financebrain.brain.BrainSettings
import com.financebrain.data.Account
import com.financebrain.data.BalanceAnchor
import com.financebrain.data.CategoryTotal
import com.financebrain.data.Direction
import com.financebrain.data.Insights
import com.financebrain.data.InvestmentLine
import com.financebrain.data.LoanInfo
import com.financebrain.data.MonthBalance
import com.financebrain.data.SalaryInfo
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
    val investedPaise: Long = 0,
    val categories: List<CategoryTotal> = emptyList(),
    val daily: LongArray = LongArray(0),
    val recurring: List<Recurring> = emptyList(),
    val months: List<MonthSummary> = emptyList(),
    val topMerchants: List<Pair<String, Long>> = emptyList(),
    val totalCount: Int = 0,
    val report: BrainReport? = null,
    val salary: SalaryInfo? = null,
    val loans: List<LoanInfo> = emptyList(),
    val investments: List<InvestmentLine> = emptyList(),
    val monthBalance: MonthBalance = MonthBalance(null, null, null),
    val anchors: List<BalanceAnchor> = emptyList(),
) {
    val totalBalancePaise: Long get() = monthBalance.nowPaise ?: accounts.sumOf { it.balancePaise ?: 0 }
    val hasTotalAnchor: Boolean get() = anchors.any { it.isTotal }
    val savedPaise: Long get() = incomePaise - expensePaise - investedPaise
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FinanceBrainApp.get(app).repository
    private val scanner = SmsInboxScanner(app, repo)
    private val updater = UpdateManager(app)
    private val gmailAccounts = FinanceBrainApp.get(app).gmailAccounts
    private val gmailSyncer = FinanceBrainApp.get(app).gmailSyncer
    val gmailAuth = GmailAuth(app)
    private val brainSettings = BrainSettings(app)
    private val brainAsk = BrainAsk(brainSettings)

    data class Exchange(val question: String, val answer: String?)
    private val _chat = MutableStateFlow<List<Exchange>>(emptyList())
    val chat: StateFlow<List<Exchange>> = _chat
    private val _hasApiKey = MutableStateFlow(brainSettings.apiKey.isNotBlank())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey

    fun setApiKey(key: String) { brainSettings.apiKey = key; _hasApiKey.value = key.isNotBlank() }

    fun ask(question: String) {
        val q = question.trim(); if (q.isBlank()) return
        val s = state.value
        _chat.value = _chat.value + Exchange(q, null)
        viewModelScope.launch {
            val ctx = brainAsk.buildContext(s.allTransactions, s.months, s.accounts, s.recurring, s.report ?: BrainAnalyzer.analyze(s.allTransactions, s.months, s.month, s.recurring, System.currentTimeMillis()), s.month)
            val a = brainAsk.ask(q, ctx)
            _chat.value = _chat.value.map { if (it.question == q && it.answer == null) it.copy(answer = a) else it }
        }
    }

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

    fun removeGmail(email: String) { gmailAccounts.remove(email); _gmail.value = gmailAccounts.list(); _gmailError.value = null }
    fun clearGmailError() { _gmailError.value = null }

    fun syncGmail() = viewModelScope.launch {
        _gmailError.value = null
        gmailSyncer.syncAll()
        repo.detectInternalTransfers()
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

    val state: StateFlow<HomeState> = combine(_month, repo.transactions, repo.accounts, repo.balanceAnchors) { m, all, accounts, anchors ->
        val end = monthEnd(m)
        val inMonth = all.filter { it.timestamp in m until end }
        val now = System.currentTimeMillis()
        val months = Insights.monthSeries(all, 12, m, anchors)
        val recurring = Insights.recurring(all, now)
        HomeState(
            month = m,
            monthTransactions = inMonth,
            allTransactions = all,
            accounts = accounts,
            incomePaise = inMonth.filter(Insights::isIncome).sumOf { it.amountPaise },
            expensePaise = inMonth.filter(Insights::isSpend).sumOf { it.amountPaise },
            investedPaise = inMonth.filter(Insights::isInvestment).sumOf { it.amountPaise },
            categories = Insights.categoryTotals(inMonth),
            daily = Insights.dailySpend(inMonth, daysInMonth(m)),
            recurring = recurring,
            months = months,
            topMerchants = Insights.topMerchants(inMonth),
            totalCount = all.size,
            report = if (all.isEmpty()) null else BrainAnalyzer.analyze(all, months, m, recurring, now),
            salary = Insights.salary(all, recurring.filter { it.direction == Direction.CREDIT }, now),
            loans = Insights.loans(all, recurring.filter { it.direction == Direction.DEBIT }, now),
            investments = Insights.investments(all, recurring.filter { it.direction == Direction.DEBIT }),
            monthBalance = Insights.monthBalance(all, anchors, m, end, now),
            anchors = anchors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(_month.value))

    fun setMonth(m: Long) { _month.value = monthStart(m) }

    fun setBalance(key: String, amountPaise: Long, at: Long) = viewModelScope.launch { repo.setBalance(key, amountPaise, at) }
    fun clearBalance(key: String) = viewModelScope.launch { repo.clearBalance(key) }
    fun shiftMonth(delta: Int) { _month.value = shiftMonth(_month.value, delta) }

    fun scanInbox(full: Boolean = false) {
        if (_scan.value?.done == false) return
        viewModelScope.launch {
            if (full) repo.purgeSms()
            _scan.value = ScanProgress(0, 0, 0, false)
            scanner.scan { _scan.value = it }
            repo.detectInternalTransfers()
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
