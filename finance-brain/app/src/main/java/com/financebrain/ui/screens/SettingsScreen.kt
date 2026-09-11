package com.financebrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financebrain.sms.ScanProgress
import com.financebrain.ui.HomeState
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    state: HomeState,
    scan: ScanProgress?,
    smsGranted: Boolean,
    padding: PaddingValues,
    onRequestSms: () -> Unit,
    onRescan: (full: Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item {
            SectionCard {
                SectionTitle("Automatic tracking")
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Bank SMS alerts", style = MaterialTheme.typography.bodyLarge)
                        Text(if (smsGranted) "On · new alerts are added instantly" else "Permission needed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!smsGranted) Button(onClick = onRequestSms) { Text("Allow") }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Gmail sync", style = MaterialTheme.typography.bodyLarge)
                        Text("Coming in the next milestone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("SMS history")
                Text("${state.totalCount} transactions · ${state.accounts.size} accounts", style = MaterialTheme.typography.bodyMedium)
                if (scan != null && !scan.done) Text("Scanning ${scan.scanned}/${scan.total}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onRescan(false) }, enabled = smsGranted && scan?.done != false) { Text("Scan new messages") }
                    OutlinedButton(onClick = { onRescan(true) }, enabled = smsGranted && scan?.done != false) { Text("Full rescan") }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Supported banks")
                Text("SBI · HDFC · ICICI · Federal Bank · PhonePe · Axis · Kotak · Paytm", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("Raw messages never leave this phone. Everything is stored in a local database.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
