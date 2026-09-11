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
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { Column(Modifier.padding(18.dp)) { content() } }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() }
        )
    }
    Spacer(Modifier.height(12.dp))
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
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
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (credit) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
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
fun StatPill(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(tint.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}
