package com.financebrain.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financebrain.brain.BrainReport
import com.financebrain.brain.Tone
import com.financebrain.ui.MainViewModel
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.formatMonth
import com.financebrain.ui.formatCycle
import com.financebrain.ui.theme.Amber
import com.financebrain.ui.theme.Coral
import com.financebrain.ui.theme.Leaf
import com.financebrain.ui.theme.Mint
import com.financebrain.ui.theme.Teal
import com.financebrain.ui.theme.TealDark

@Composable
fun BrainScreen(
    report: BrainReport?,
    month: Long,
    chat: List<MainViewModel.Exchange>,
    hasApiKey: Boolean,
    padding: PaddingValues,
    onAsk: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Brain", style = MaterialTheme.typography.headlineSmall)
            Text("Analysis for ${formatCycle(month)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (report == null) {
            item { SectionCard { EmptyHint("Once transactions are in, the analysis appears here.") } }
        } else {
            item { ScoreCard(report) }
            item {
                SectionCard {
                    SectionTitle("What to do")
                    report.actions.forEachIndexed { i, a ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(a, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            report.sections.forEach { s ->
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(toneColor(s.tone), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        s.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Ask Finance Brain")
                if (!hasApiKey) {
                    Text("Ask anything in your own words: \"Where did my money go last month?\", \"Can I afford a ₹40,000 phone?\", \"Which subscriptions should I cancel?\". This uses Claude and needs your own API key.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Button(onClick = onOpenSettings) { Text("Add API key in Settings") }
                } else {
                    chat.forEach { ex ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(ex.question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            if (ex.answer == null) Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                                Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else Text(ex.answer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ask about your money…") }, shape = RoundedCornerShape(16.dp), maxLines = 3,
                        trailingIcon = {
                            IconButton(onClick = { if (question.isNotBlank()) { onAsk(question); question = "" } }) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Ask", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(r: BrainReport) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Teal),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Teal, TealDark))).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).background(Color.White.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.score}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("/100", style = MaterialTheme.typography.labelSmall, color = Mint)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(r.scoreLabel, style = MaterialTheme.typography.titleMedium, color = Mint)
                Spacer(Modifier.height(4.dp))
                Text(r.headline, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}

private fun toneColor(t: Tone) = when (t) {
    Tone.GOOD -> Leaf
    Tone.WARN -> Amber
    Tone.BAD -> Coral
    Tone.NEUTRAL -> Color(0xFF8A9A94)
}
