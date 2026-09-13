package com.financebrain.data

import android.content.Context
import android.net.Uri
import com.financebrain.FinanceBrainApp
import com.financebrain.parser.BankSmsParser
import com.financebrain.parser.ParsedTransaction
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Full backup to a JSON file the user chooses, restore from it, and CSV statement import. */
class BackupManager(private val context: Context, private val db: AppDatabase, private val repo: TransactionRepository) {

    private fun Transaction.toJson() = JSONObject().apply {
        put("amountPaise", amountPaise); put("direction", direction.name); put("timestamp", timestamp); put("bank", bank)
        put("accountTail", accountTail); put("counterparty", counterparty); put("category", category); put("channel", channel)
        put("reference", reference); put("balancePaise", balancePaise); put("note", note); put("source", source.name)
        put("rawText", rawText); put("dedupKey", dedupKey); put("isTransfer", isTransfer); put("userEdited", userEdited); put("accountKind", accountKind)
    }
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k)) null else optLong(k)
    private fun JSONObject.optStr(k: String): String? = if (isNull(k)) null else optString(k)

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val app = FinanceBrainApp.get(context)
        val root = JSONObject()
        root.put("app", "finance-brain"); root.put("version", 1); root.put("exportedAt", System.currentTimeMillis())
        val tx = db.transactions().allNow(); root.put("transactions", JSONArray().apply { tx.forEach { put(it.toJson()) } })
        root.put("accounts", JSONArray().apply { db.accounts().list().forEach { put(JSONObject().apply { put("bank", it.bank); put("accountTail", it.accountTail); put("balancePaise", it.balancePaise); put("balanceAt", it.balanceAt); put("kind", it.kind) }) } })
        root.put("merchantRules", JSONArray().apply { db.merchantRules().list().forEach { put(JSONObject().apply { put("merchantKey", it.merchantKey); put("category", it.category) }) } })
        root.put("anchors", JSONArray().apply { db.balanceAnchors().list().forEach { put(JSONObject().apply { put("key", it.key); put("amountPaise", it.amountPaise); put("at", it.at) }) } })
        root.put("cards", JSONArray().apply { db.cards().list().forEach { put(JSONObject().apply { put("key", it.key); put("name", it.name); put("bank", it.bank); put("tail", it.tail); put("limitPaise", it.limitPaise); put("billingDay", it.billingDay); put("dueDaysAfter", it.dueDaysAfter) }) } })
        root.put("goals", JSONArray().apply { db.goals().list().forEach { put(JSONObject().apply { put("name", it.name); put("icon", it.icon); put("targetPaise", it.targetPaise); put("savedPaise", it.savedPaise); put("deadline", it.deadline); put("notes", it.notes); put("createdAt", it.createdAt) }) } })
        root.put("receivables", JSONArray().apply { db.receivables().list().forEach { put(JSONObject().apply { put("from", it.from); put("amountPaise", it.amountPaise); put("dueDate", it.dueDate); put("notes", it.notes); put("received", it.received); put("createdAt", it.createdAt) }) } })
        root.put("informalLoans", JSONArray().apply { db.informalLoans().list().forEach { put(JSONObject().apply { put("lender", it.lender); put("amountPaise", it.amountPaise); put("takenDate", it.takenDate); put("dueDate", it.dueDate); put("notes", it.notes); put("repaid", it.repaid) }) } })
        root.put("controlled", JSONArray().apply { db.discipline().listControlled().forEach { put(JSONObject().apply { put("category", it.category); put("monthlyLimitPaise", it.monthlyLimitPaise); put("note", it.note) }) } })
        root.put("zero", JSONArray().apply { db.discipline().listZero().forEach { put(JSONObject().apply { put("category", it.category); put("note", it.note); put("since", it.since) }) } })
        root.put("disciplineEntries", JSONArray().apply { db.discipline().listEntries().forEach { put(JSONObject().apply { put("kind", it.kind); put("amountPaise", it.amountPaise); put("reason", it.reason); put("at", it.at) }) } })
        root.put("holdings", JSONArray().apply { db.holdings().list().forEach { put(JSONObject().apply { put("name", it.name); put("type", it.type); put("account", it.account); put("units", it.units); put("investedPaise", it.investedPaise); put("currentPaise", it.currentPaise); put("updatedAt", it.updatedAt); put("notes", it.notes) }) } })
        root.put("loans", JSONArray().apply { db.loans().list().forEach { put(JSONObject().apply { put("lender", it.lender); put("type", it.type); put("outstandingPaise", it.outstandingPaise); put("asOf", it.asOf); put("annualRatePct", it.annualRatePct); put("emiPaise", it.emiPaise); put("dueDay", it.dueDay); put("notes", it.notes) }) } })
        root.put("settings", JSONObject().apply { put("salaryDay", app.salaryDay); put("investPct", app.investTargetPct); put("budget", app.monthlyBudgetPaise); put("daughterName", app.daughterName); put("ignoredBanks", JSONArray(app.ignoredBanks().toList())) })
        context.contentResolver.openOutputStream(uri)!!.use { it.write(root.toString(2).toByteArray()) }
        tx.size
    }

    suspend fun restore(uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
        val root = JSONObject(text)
        if (root.optString("app") != "finance-brain") return@withContext "Not a Finance Brain backup file."
        val app = FinanceBrainApp.get(context)
        var added = 0
        root.optJSONArray("transactions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val t = Transaction(
                    amountPaise = j.getLong("amountPaise"), direction = Direction.valueOf(j.getString("direction")), timestamp = j.getLong("timestamp"),
                    bank = j.getString("bank"), accountTail = j.optStr("accountTail"), counterparty = j.getString("counterparty"), category = j.getString("category"),
                    channel = j.getString("channel"), reference = j.optStr("reference"), balancePaise = j.optLongOrNull("balancePaise"), note = j.optStr("note"),
                    source = Source.valueOf(j.getString("source")), rawText = j.optStr("rawText"), dedupKey = j.getString("dedupKey"),
                    isTransfer = j.optBoolean("isTransfer"), userEdited = j.optBoolean("userEdited"), accountKind = j.optString("accountKind", "BANK"),
                )
                if (db.transactions().insert(t) != -1L) added++
            }
        }
        root.optJSONArray("accounts")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.accounts().upsert(Account(j.getString("bank"), j.getString("accountTail"), j.optLongOrNull("balancePaise"), j.optLongOrNull("balanceAt"), j.optString("kind", "BANK"))) } }
        root.optJSONArray("merchantRules")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.merchantRules().upsert(MerchantRule(j.getString("merchantKey"), j.getString("category"))) } }
        root.optJSONArray("anchors")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.balanceAnchors().upsert(BalanceAnchor(j.getString("key"), j.getLong("amountPaise"), j.getLong("at"))) } }
        root.optJSONArray("cards")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.cards().upsert(CreditCard(j.getString("key"), j.getString("name"), j.getString("bank"), j.getString("tail"), j.optLongOrNull("limitPaise"), j.optInt("billingDay", 1), j.optInt("dueDaysAfter", 20))) } }
        root.optJSONArray("goals")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.goals().upsert(Goal(0, j.getString("name"), j.optString("icon", "🎯"), j.getLong("targetPaise"), j.optLong("savedPaise"), j.optLongOrNull("deadline"), j.optStr("notes"), j.optLong("createdAt", System.currentTimeMillis()))) } }
        root.optJSONArray("receivables")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.receivables().upsert(Receivable(0, j.getString("from"), j.getLong("amountPaise"), j.optLongOrNull("dueDate"), j.optStr("notes"), j.optBoolean("received"), j.optLong("createdAt", System.currentTimeMillis()))) } }
        root.optJSONArray("informalLoans")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.informalLoans().upsert(InformalLoan(0, j.getString("lender"), j.getLong("amountPaise"), j.getLong("takenDate"), j.optLongOrNull("dueDate"), j.optStr("notes"), j.optBoolean("repaid"))) } }
        root.optJSONArray("controlled")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.discipline().upsertControlled(ControlledCategory(j.getString("category"), j.getLong("monthlyLimitPaise"), j.optStr("note"))) } }
        root.optJSONArray("zero")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.discipline().upsertZero(ZeroTolerance(j.getString("category"), j.optStr("note"), j.optLong("since", System.currentTimeMillis()))) } }
        root.optJSONArray("disciplineEntries")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.discipline().insertEntry(DisciplineEntry(0, j.getString("kind"), j.getLong("amountPaise"), j.optString("reason"), j.optLong("at", System.currentTimeMillis()))) } }
        root.optJSONArray("holdings")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.holdings().upsert(Holding(0, j.getString("name"), j.optString("type", "OTHER"), j.optString("account", "Other"), if (j.isNull("units")) null else j.optDouble("units"), j.optLong("investedPaise"), j.getLong("currentPaise"), j.optLong("updatedAt", System.currentTimeMillis()), j.optStr("notes"))) } }
        root.optJSONArray("loans")?.let { arr -> for (i in 0 until arr.length()) { val j = arr.getJSONObject(i); db.loans().upsert(Loan(0, j.getString("lender"), j.optString("type", "OTHER"), j.getLong("outstandingPaise"), j.optLong("asOf", System.currentTimeMillis()), j.optDouble("annualRatePct"), j.getLong("emiPaise"), j.optInt("dueDay", 5), j.optStr("notes"))) } }
        root.optJSONObject("settings")?.let { s ->
            app.salaryDay = s.optInt("salaryDay", 1); app.investTargetPct = s.optInt("investPct", 20); app.monthlyBudgetPaise = s.optLong("budget"); app.daughterName = s.optString("daughterName")
            s.optJSONArray("ignoredBanks")?.let { ib -> app.setIgnoredBanks((0 until ib.length()).map { ib.getString(it) }.toSet()) }
        }
        "Restored: $added new transactions plus cards, goals, rules and settings."
    }

    /**
     * Bank statement CSV import. Finds date / description / amount (or debit + credit) columns by
     * header name, whatever order the bank uses.
     */
    suspend fun importCsv(uri: Uri, bankName: String): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return@withContext "The file has no rows."
        var headerIdx = lines.indexOfFirst { l -> val h = l.lowercase(); h.contains("date") && (h.contains("amount") || h.contains("debit") || h.contains("withdrawal") || h.contains("credit")) }
        if (headerIdx < 0) headerIdx = 0
        val header = splitCsv(lines[headerIdx]).map { it.trim().lowercase() }
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h.contains(it) } }
        val iDate = col("txn date", "transaction date", "value date", "date")
        val iDesc = col("narration", "description", "particulars", "details", "remarks", "transaction remarks")
        val iDebit = col("withdrawal", "debit", "dr amount", "dr")
        val iCredit = col("deposit", "credit", "cr amount", "cr")
        val iAmount = header.indexOfFirst { it == "amount" || it.contains("amount") && !it.contains("dr") && !it.contains("cr") }
        val iType = col("type", "dr/cr", "cr/dr")
        val iBal = col("balance", "closing")
        if (iDate < 0 || (iAmount < 0 && iDebit < 0 && iCredit < 0)) return@withContext "Could not find date and amount columns. Headers seen: ${header.joinToString()}"
        val fmts = listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy", "yyyy-MM-dd", "dd MMM yyyy", "dd-MMM-yyyy", "dd-MMM-yy", "MM/dd/yyyy").map { SimpleDateFormat(it, Locale.ENGLISH).apply { isLenient = false } }
        var added = 0; var skipped = 0
        for (line in lines.drop(headerIdx + 1)) {
            val c = splitCsv(line)
            if (c.size <= iDate) { skipped++; continue }
            val ts = fmts.firstNotNullOfOrNull { f -> try { f.parse(c[iDate].trim())?.time } catch (_: Exception) { null } }
            if (ts == null) { skipped++; continue }
            fun num(i: Int) = if (i in c.indices) BankSmsParser.toPaise(c[i].replace("₹", "").replace("\"", "").trim().ifBlank { "0" }) ?: 0L else 0L
            var amount: Long; var dir: Direction
            if (iDebit >= 0 || iCredit >= 0) {
                val d = num(iDebit); val cr = num(iCredit)
                if (d > 0) { amount = d; dir = Direction.DEBIT } else if (cr > 0) { amount = cr; dir = Direction.CREDIT } else { skipped++; continue }
            } else {
                amount = num(iAmount); if (amount == 0L) { skipped++; continue }
                val typ = if (iType >= 0 && iType < c.size) c[iType].uppercase() else ""
                dir = when { typ.startsWith("CR") -> Direction.CREDIT; typ.startsWith("DR") -> Direction.DEBIT; amount < 0 -> Direction.CREDIT; else -> Direction.DEBIT }
                amount = kotlin.math.abs(amount)
            }
            val desc = if (iDesc in c.indices) c[iDesc].trim().trim('"') else "Statement entry"
            val counterparty = desc.split('/', '-', '|').map { it.trim() }.filter { it.length > 2 && !it.all(Char::isDigit) && !it.equals("UPI", true) && !it.equals("NEFT", true) && !it.equals("IMPS", true) }.maxByOrNull { it.length } ?: desc
            val p = ParsedTransaction(amount, dir, bankName, null, counterparty.take(60), if (desc.contains("UPI", true)) "UPI" else if (desc.contains("NEFT", true)) "NEFT" else if (desc.contains("IMPS", true)) "IMPS" else "OTHER",
                "csv-" + (ts / 1000) + "-" + amount, if (iBal >= 0) num(iBal).takeIf { it != 0L } else null, ts)
            if (repo.ingest(p, desc, Source.STATEMENT)) added++ else skipped++
        }
        repo.detectInternalTransfers()
        "Imported $added rows, skipped $skipped (duplicates or unreadable)."
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false
        for (ch in line) {
            when {
                ch == '"' -> q = !q
                ch == ',' && !q -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out += sb.toString(); return out
    }
}
