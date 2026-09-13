package com.financebrain.brain

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.financebrain.data.Account
import com.financebrain.data.Insights
import com.financebrain.data.MonthSummary
import com.financebrain.data.Recurring
import com.financebrain.data.Transaction
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatMonth
import com.financebrain.ui.formatCycle
import com.financebrain.ui.formatRupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores the user's own Anthropic API key on-device, encrypted. */
class BrainSettings(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "brain_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    var apiKey: String
        get() = prefs.getString("anthropic_api_key", "") ?: ""
        set(v) { prefs.edit().putString("anthropic_api_key", v.trim()).apply() }
}

/**
 * Answers free-form questions about the user's money with Claude. The phone builds a compact
 * summary of the ledger (totals, categories, recurring, recent rows) and sends only that.
 */
class BrainAsk(private val settings: BrainSettings) {

    private fun client(): AnthropicClient = AnthropicOkHttpClient.builder().apiKey(settings.apiKey).build()

    private val system = """
        You are Finance Brain, a personal finance analyst for a user in India. Amounts are in Indian rupees.
        You are given a summary of the user's actual transactions captured from bank SMS and email.
        Answer the user's question directly and specifically using only that data. Quote real figures.
        Be concrete about what to do. Do not invent transactions or assume income you cannot see.
        If the data is insufficient, say what is missing. Use short paragraphs and, when useful, short bullet lists.
        Use the Indian number style (1,23,456) and lakh/crore where natural.
    """.trimIndent()

    fun buildContext(
        all: List<Transaction>, months: List<MonthSummary>, accounts: List<Account>,
        recurring: List<Recurring>, report: BrainReport, month: Long,
    ): String {
        val sb = StringBuilder()
        sb.append("Selected month: ").append(formatCycle(month)).append('\n')
        sb.append("Health score ").append(report.score).append(" (").append(report.scoreLabel).append(")\n")
        sb.append("Accounts (last reported balances):\n")
        accounts.forEach { sb.append("- ${it.bank} ..${it.accountTail}: ${it.balancePaise?.let(::formatRupees) ?: "unknown"}\n") }
        sb.append("Monthly summary (income / spent / invested / month-end balance):\n")
        months.forEach { m ->
            sb.append("- ${formatMonth(m.monthStart)}: ${formatRupees(m.incomePaise)} / ${formatRupees(m.expensePaise)} / ${formatRupees(m.investedPaise)} / ${m.endBalancePaise?.let(::formatRupees) ?: "n/a"}\n")
        }
        val end = java.util.Calendar.getInstance().apply { timeInMillis = month; add(java.util.Calendar.MONTH, 1) }.timeInMillis
        val inMonth = all.filter { it.timestamp in month until end }
        sb.append("Spending by category this month:\n")
        Insights.categoryTotals(inMonth).forEach { sb.append("- ${it.category}: ${formatRupees(it.paise)} (${it.count} payments)\n") }
        sb.append("Top merchants this month:\n")
        Insights.topMerchants(inMonth, 8).forEach { sb.append("- ${it.first}: ${formatRupees(it.second)}\n") }
        if (recurring.isNotEmpty()) {
            sb.append("Recurring payments:\n")
            recurring.take(12).forEach { sb.append("- ${it.name}: ${formatRupees(it.amountPaise)} (${it.category}), next ${formatDay(it.nextDue)}\n") }
        }
        sb.append("Analysis sections:\n")
        report.sections.forEach { s -> sb.append("## ${s.title}\n"); s.lines.forEach { sb.append("- $it\n") } }
        sb.append("Most recent 60 transactions (date, +credit/-debit, amount, counterparty, category, bank):\n")
        all.take(60).forEach { t ->
            sb.append("- ${formatDay(t.timestamp)} ${if (t.direction == com.financebrain.data.Direction.CREDIT) "+" else "-"}${formatRupees(t.amountPaise)} ${t.counterparty} [${t.category}${if (t.isTransfer) ", transfer" else ""}] ${t.bank}\n")
        }
        return sb.toString()
    }

    suspend fun ask(question: String, context: String): String = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) return@withContext "Add your Anthropic API key in Settings → Ask Finance Brain first."
        try {
            val params = MessageCreateParams.builder()
                .model("claude-opus-5")
                .maxTokens(4000L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .system(system)
                .addUserMessage("Here is my financial data:\n\n$context\n\nMy question: $question")
                .build()
            val response = client().messages().create(params)
            if (response.stopReason().isPresent && response.stopReason().get().toString().equals("refusal", ignoreCase = true)) {
                return@withContext "Claude declined to answer this one. Try rephrasing the question."
            }
            val text = response.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("\n").trim()
            text.ifBlank { "No answer came back. Try again." }
        } catch (e: com.anthropic.errors.AnthropicServiceException) {
            when (e.statusCode()) {
                401 -> "The API key was rejected. Check it in Settings."
                429 -> "Rate limited. Wait a minute and try again."
                else -> "Claude returned an error (${e.statusCode()}). ${e.message ?: ""}".trim()
            }
        } catch (e: Exception) {
            "Could not reach Claude: ${e.message ?: "unknown error"}"
        }
    }
}
