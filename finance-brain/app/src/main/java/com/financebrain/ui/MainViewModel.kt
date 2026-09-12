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
import com.financebrain.ui.formatRupees
import com.financebrain.data.Allocation
import com.financebrain.data.CalendarEvent
import com.financebrain.data.CardStatus
import com.financebrain.data.ControlledCategory
import com.financebrain.data.CreditCard
import com.financebrain.data.DisciplineEntry
import com.financebrain.data.DisciplineStatus
import com.financebrain.data.Goal
import com.financebrain.data.Holding
import com.financebrain.data.Loan
import com.financebrain.data.LoanStatus
import com.financebrain.data.NetWorth
import com.financebrain.data.InformalLoan
import com.financebrain.data.Planning
import com.financebrain.data.Receivable
import com.financebrain.data.ZeroTolerance
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

data class PlanState(
    val cards: List<CardStatus> = emptyList(),
    val allocation: Allocation? = null,
    val calendar: List<CalendarEvent> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val receivables: List<Receivable> = emptyList(),
    val informalLoans: List<InformalLoan> = emptyList(),
    val discipline: DisciplineStatus? = null,
    val controlled: List<ControlledCategory> = emptyList(),
    val zero: List<ZeroTolerance> = emptyList(),
    val entries: List<DisciplineEntry> = emptyList(),
    val holdings: List<Holding> = emptyList(),
    val loanStatuses: List<LoanStatus> = emptyList(),
    val netWorth: NetWorth? = null,
    val wealthSections: List<com.financebrain.brain.BrainSection> = emptyList(),
    val wealthActions: List<String> = emptyList(),
    val salaryDay: Int = 1,
    val investPct: Int = 20,
    val budgetPaise: Long = 0,
    val daughterName: String = "",
)

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
            val p = plan.value
            val ctx = brainAsk.buildContext(s.allTransactions, s.months, s.accounts, s.recurring, s.report ?: BrainAnalyzer.analyze(s.allTransactions, s.months, s.month, s.recurring, System.currentTimeMillis()), s.month) +
                buildString {
                    p.netWorth?.let { append("\nNet worth: assets ${formatRupees(it.assets)}, liabilities ${formatRupees(it.liabilities)}, net ${formatRupees(it.net)}\n") }
                    if (p.holdings.isNotEmpty()) { append("Holdings (current / invested):\n"); p.holdings.forEach { append("- ${it.name} [${it.type}, ${it.account}]: ${formatRupees(it.currentPaise)} / ${formatRupees(it.investedPaise)}\n") } }
                    if (p.loanStatuses.isNotEmpty()) { append("Loans:\n"); p.loanStatuses.forEach { append("- ${it.loan.lender}: outstanding ${formatRupees(it.outstandingNowPaise)} at ${it.loan.annualRatePct}%, EMI ${formatRupees(it.loan.emiPaise)}, ${it.monthsLeft} months left, interest left ${formatRupees(it.totalInterestLeftPaise)}\n") } }
                    p.allocation?.let { append("Cycle plan: free to spend ${formatRupees(it.freeToSpendPaise)}, spent ${formatRupees(it.spentSoFarPaise)}, ${it.daysLeft} days left\n") }
                    p.wealthSections.forEach { sec -> append("## ${sec.title}\n"); sec.lines.forEach { append("- $it\n") } }
                }
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

    private val appRef = FinanceBrainApp.get(app)
    private val _ignoredBanks = MutableStateFlow(appRef.ignoredBanks())
    val ignoredBanks: StateFlow<Set<String>> = _ignoredBanks
    val knownBanks: StateFlow<List<String>> = appRef.database.transactions().banks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ignoredAccounts = MutableStateFlow(appRef.ignoredAccounts())
    val ignoredAccounts: StateFlow<Set<String>> = _ignoredAccounts
    val accountRefs: StateFlow<List<com.financebrain.data.AccountRef>> = repo.accountRefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAccountIgnored(key: String, ignored: Boolean) = viewModelScope.launch {
        val next = _ignoredAccounts.value.toMutableSet().apply { if (ignored) add(key) else remove(key) }
        appRef.setIgnoredAccounts(next); _ignoredAccounts.value = next
        if (ignored) repo.purgeAccount(key.substringBefore('|'), key.substringAfter('|'))
    }

    fun setBankIgnored(bank: String, ignored: Boolean) = viewModelScope.launch {
        val next = _ignoredBanks.value.toMutableSet().apply { if (ignored) add(bank) else remove(bank) }
        appRef.setIgnoredBanks(next); _ignoredBanks.value = next
        if (ignored) repo.purgeBank(bank)
    }

    private val _settingsTick = MutableStateFlow(0)
    val plan: StateFlow<PlanState> = combine(
        combine(state, repo.cards, repo.goals, repo.receivables, repo.informalLoans) { s, cards, goals, recv, loans -> arrayOf(s, cards, goals, recv, loans) },
        combine(repo.controlled, repo.zeroTolerance, repo.disciplineEntries, _settingsTick) { c, z, e, _ -> Triple(c, z, e) },
        combine(repo.holdings, repo.loans) { h, l -> h to l },
    ) { a, d, hl ->
        @Suppress("UNCHECKED_CAST")
        val s = a[0] as HomeState; val cards = a[1] as List<CreditCard>; val goals = a[2] as List<Goal>
        val recv = a[3] as List<Receivable>; val loans = a[4] as List<InformalLoan>
        val now = System.currentTimeMillis()
        val statuses = cards.map { Planning.cardStatus(it, s.allTransactions, now) }
        val debits = s.recurring.filter { it.direction == Direction.DEBIT }
        val loanStatuses = hl.second.map { Planning.loanStatus(it, now) }
        // Formal loans replace the detected EMI lines with the same amount.
        val detectedLoans = s.loans.filter { d -> hl.second.none { kotlin.math.abs(it.emiPaise - d.emiPaise) < 2_000_00 } }
        val nw = Planning.netWorth(s.monthBalance.nowPaise, hl.first, recv, loanStatuses, statuses, loans)
        val wealth = BrainAnalyzer.wealth(nw, loanStatuses, hl.first, s.salary?.amountPaise ?: s.incomePaise)
        PlanState(
            holdings = hl.first, loanStatuses = loanStatuses, netWorth = nw, wealthSections = wealth.first, wealthActions = wealth.second,
            cards = statuses,
            allocation = Planning.allocation(s.allTransactions, s.month, now, s.salary,
                detectedLoans + hl.second.map { com.financebrain.data.LoanInfo(it.lender, it.emiPaise, Planning.dayToTs(now, it.dueDay), 0, 0, it.type) },
                statuses, debits, appRef.investTargetPct),
            calendar = Planning.calendar(s.month, appRef.salaryDay, s.salary, s.loans, statuses, s.recurring),
            goals = goals, receivables = recv, informalLoans = loans,
            discipline = Planning.discipline(s.allTransactions, s.month, d.first, d.second, d.third, now),
            controlled = d.first, zero = d.second, entries = d.third,
            salaryDay = appRef.salaryDay, investPct = appRef.investTargetPct, budgetPaise = appRef.monthlyBudgetPaise, daughterName = appRef.daughterName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun setSalaryDay(d: Int) { appRef.salaryDay = d; _month.value = monthStart(System.currentTimeMillis()); _settingsTick.value++ }
    fun setInvestPct(p: Int) { appRef.investTargetPct = p; _settingsTick.value++ }
    fun setBudget(paise: Long) { appRef.monthlyBudgetPaise = paise; _settingsTick.value++ }
    fun setDaughterName(n: String) { appRef.daughterName = n; _settingsTick.value++ }
    fun setDailyAlert(enabled: Boolean, hour: Int) { appRef.dailyAlertEnabled = enabled; appRef.dailyAlertHour = hour; com.financebrain.alerts.DailyAlertWorker.schedule(getApplication()); _settingsTick.value++ }

    fun saveHolding(h: Holding) = viewModelScope.launch { repo.saveHolding(h) }
    fun deleteHolding(id: Long) = viewModelScope.launch { repo.deleteHolding(id) }
    fun saveLoan(l: Loan) = viewModelScope.launch { repo.saveLoan(l) }
    fun deleteLoan(id: Long) = viewModelScope.launch { repo.deleteLoan(id) }
    fun saveCard(c: CreditCard) = viewModelScope.launch { repo.saveCard(c) }
    fun deleteCard(key: String) = viewModelScope.launch { repo.deleteCard(key) }
    fun saveGoal(g: Goal) = viewModelScope.launch { repo.saveGoal(g) }
    fun deleteGoal(id: Long) = viewModelScope.launch { repo.deleteGoal(id) }
    fun saveReceivable(r: Receivable) = viewModelScope.launch { repo.saveReceivable(r) }
    fun deleteReceivable(id: Long) = viewModelScope.launch { repo.deleteReceivable(id) }
    fun saveInformalLoan(l: InformalLoan) = viewModelScope.launch { repo.saveInformalLoan(l) }
    fun deleteInformalLoan(id: Long) = viewModelScope.launch { repo.deleteInformalLoan(id) }
    fun saveControlled(c: ControlledCategory) = viewModelScope.launch { repo.saveControlled(c) }
    fun deleteControlled(category: String) = viewModelScope.launch { repo.deleteControlled(category) }
    fun saveZero(z: ZeroTolerance) = viewModelScope.launch { repo.saveZero(z) }
    fun deleteZero(category: String) = viewModelScope.launch { repo.deleteZero(category) }
    fun addDisciplineEntry(kind: String, amountPaise: Long, reason: String) = viewModelScope.launch { repo.addDisciplineEntry(DisciplineEntry(kind = kind, amountPaise = amountPaise, reason = reason)) }
    fun deleteDisciplineEntry(id: Long) = viewModelScope.launch { repo.deleteDisciplineEntry(id) }

    private val backup = com.financebrain.data.BackupManager(app, FinanceBrainApp.get(app).database, repo)
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun clearToast() { _toast.value = null }
    fun exportBackup(uri: android.net.Uri) = viewModelScope.launch { _toast.value = try { "Backup saved with ${backup.export(uri)} transactions." } catch (e: Exception) { "Backup failed: ${e.message}" } }
    fun restoreBackup(uri: android.net.Uri) = viewModelScope.launch { _toast.value = try { backup.restore(uri).also { _settingsTick.value++; _month.value = monthStart(System.currentTimeMillis()) } } catch (e: Exception) { "Restore failed: ${e.message}" } }
    fun importCsv(uri: android.net.Uri, bank: String) = viewModelScope.launch { _toast.value = try { backup.importCsv(uri, bank) } catch (e: Exception) { "Import failed: ${e.message}" } }

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
