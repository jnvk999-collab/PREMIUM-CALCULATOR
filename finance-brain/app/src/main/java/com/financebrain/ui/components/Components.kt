package com.financebrain.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Categories
import com.financebrain.data.Direction
import com.financebrain.data.Transaction
import com.financebrain.ui.formatRupees
import com.financebrain.ui.formatTime
import com.financebrain.ui.theme.CategoryPalette

fun categoryIcon(category: String): ImageVector = when (category) {
    Categories.FOOD -> Icons.Default.Restaurant
    Categories.GROCERIES -> Icons.Default.ShoppingCart
    Categories.SHOPPING -> Icons.Default.ShoppingBag
    Categories.TRAVEL -> Icons.Default.DirectionsCar
    Categories.FUEL -> Icons.Default.LocalGasStation
    Categories.BILLS -> Icons.Default.PhoneAndroid
    Categories.UTILITIES -> Icons.Default.Bolt
    Categories.ENTERTAINMENT -> Icons.Default.Movie
    Categories.HEALTH -> Icons.Default.LocalHospital
    Categories.EMI -> Icons.Default.CreditCard
    Categories.INVESTMENT -> Icons.Default.TrendingUp
    Categories.RENT -> Icons.Default.Home
    Categories.EDUCATION -> Icons.Default.School
    Categories.CASH -> Icons.Default.Money
    Categories.TRANSFER -> Icons.Default.SwapHoriz
    Categories.SALARY -> Icons.Default.Work
    Categories.INCOME -> Icons.Default.AttachMoney
    Categories.REFUND -> Icons.Default.Undo
    else -> Icons.Outlined.Category
}

fun categoryColor(category: String): Color {
    val idx = Categories.all.indexOf(category).let { if (it < 0) Categories.all.size else it }
    return CategoryPalette[idx % CategoryPalette.size]
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, tint: Color? = null, content: @Composable () -> Unit) {
    val p = com.financebrain.ui.theme.P
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint?.copy(alpha = 0.08f)?.compositeOver(p.s2) ?: p.s2),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint?.copy(alpha = 0.35f) ?: p.bd),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

private fun Color.compositeOver(bg: Color): Color = androidx.compose.ui.graphics.Color(
    red = red * alpha + bg.red * (1 - alpha), green = green * alpha + bg.green * (1 - alpha), blue = blue * alpha + bg.blue * (1 - alpha), alpha = 1f
)

/** FinanceOS-style section label: small caps, muted, optional action on the right. */
@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = com.financebrain.ui.theme.P
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = p.t2, modifier = Modifier.weight(1f), letterSpacing = 1.sp)
        if (action != null) Text(
            action,
            style = MaterialTheme.typography.labelMedium,
            color = p.gold,
            modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() }
        )
    }
    Spacer(Modifier.height(10.dp))
}

/** A labelled horizontal bar with the amount on the right, as in FinanceOS's cash-flow card. */
@Composable
fun BarRow(label: String, sub: String?, valueText: String, fraction: Float, color: Color) {
    val p = com.financebrain.ui.theme.P
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(96.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = p.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = p.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(4.dp).background(p.bd, CircleShape)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(8.dp))
        Text(valueText, style = MaterialTheme.typography.labelLarge, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.width(88.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
    }
}

@Composable
fun CategoryDot(category: String, size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(categoryColor(category).copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(categoryIcon(category), contentDescription = category, tint = categoryColor(category), modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun TransactionRow(t: Transaction, showTime: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryDot(t.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.counterparty, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(t.category)
                append(" · ")
                append(t.bank)
                if (t.accountTail != null) append(" ··").append(t.accountTail)
                if (showTime) append(" · ").append(formatTime(t.timestamp))
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        val credit = t.direction == Direction.CREDIT
        Text(
            formatRupees(t.amountPaise, sign = credit).let { if (credit) it else "-$it" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (credit) com.financebrain.ui.theme.P.green else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One horizontal bar split by category share. */
@Composable
fun ShareBar(parts: List<Pair<Color, Float>>, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(1f, animationSpec = tween(700), label = "share")
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val gap = 3.dp.toPx()
        val r = 6.dp.toPx()
        var x = 0f
        val total = size.width - gap * (parts.size - 1).coerceAtLeast(0)
        parts.forEach { (c, f) ->
            val w = total * f * progress
            drawRoundRect(c, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            x += w + gap
        }
    }
}

/** Simple bar chart with today's bar highlighted. */
@Composable
fun BarChart(values: LongArray, highlightIndex: Int, color: Color, modifier: Modifier = Modifier, labels: List<String> = emptyList()) {
    val progress by animateFloatAsState(1f, animationSpec = tween(600), label = "bars")
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val faded = color.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val bw = (slot * 0.62f).coerceAtLeast(2f)
            values.forEachIndexed { i, v ->
                val h = (v / max) * size.height * progress
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(
                    if (i == highlightIndex) color else faded,
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h.coerceAtLeast(3f)),
                    size = androidx.compose.ui.geometry.Size(bw, h.coerceAtLeast(3f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2, bw / 2)
                )
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor) }
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, tint: Color, modifier: Modifier = Modifier, sub: String? = null) {
    val p = com.financebrain.ui.theme.P
    Column(
        modifier.background(p.s2, RoundedCornerShape(12.dp)).border(1.dp, p.bd, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = p.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = p.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun UpdateCard(state: com.financebrain.update.UpdateState, onDownload: () -> Unit, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (state) {
        is com.financebrain.update.UpdateState.Available -> "Update available · v${state.update.versionName}" to "A newer build is ready. Tap to download."
        is com.financebrain.update.UpdateState.Downloading -> "Downloading v${state.update.versionName}" to "${(state.progress * 100).toInt()}%"
        is com.financebrain.update.UpdateState.ReadyToInstall -> "Ready to install · v${state.update.versionName}" to "Android will ask you to confirm."
        else -> return
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                if (state is com.financebrain.update.UpdateState.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(8.dp))
            when (state) {
                is com.financebrain.update.UpdateState.Available -> androidx.compose.material3.Button(onClick = onDownload) { Text("Update") }
                is com.financebrain.update.UpdateState.ReadyToInstall -> androidx.compose.material3.Button(onClick = onInstall) { Text("Install") }
                else -> {}
            }
            if (state !is com.financebrain.update.UpdateState.Downloading) {
                androidx.compose.material3.IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Later", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}
