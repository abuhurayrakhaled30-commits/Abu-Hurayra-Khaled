package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.currentYearMonth
import com.example.model.formatBanglaMonth
import com.example.model.formatTaka
import com.example.model.toBanglaDigits
import com.example.ui.HisabDashboardData
import com.example.ui.components.categoryColor
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class ReportPeriod(val label: String) {
    THIS_MONTH("এই মাস"),
    LAST_MONTH("গত মাস"),
    THIS_YEAR("এই বছর"),
    ALL_TIME("সব সময়")
}

@Composable
fun ReportsScreen(
    data: HisabDashboardData
) {
    val sym = data.config.currencySymbol
    var period by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    var breakdownType by remember { mutableStateOf("expense") } // expense or income

    val thisMonth = remember { currentYearMonth() }
    val lastMonth = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }
    val thisYear = remember { thisMonth.substring(0, 4) }

    val periodTransactions = remember(data.transactions, period) {
        when (period) {
            ReportPeriod.THIS_MONTH -> data.transactions.filter { it.date.startsWith(thisMonth) }
            ReportPeriod.LAST_MONTH -> data.transactions.filter { it.date.startsWith(lastMonth) }
            ReportPeriod.THIS_YEAR -> data.transactions.filter { it.date.startsWith(thisYear) }
            ReportPeriod.ALL_TIME -> data.transactions
        }
    }

    val totalIncome = periodTransactions.filter { it.isIncome }.sumOf { it.amount }
    val totalExpense = periodTransactions.filter { !it.isIncome }.sumOf { it.amount }
    val net = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0.0) (((totalIncome - totalExpense) / totalIncome) * 100.0).toInt().coerceIn(-100, 100) else 0

    // Monthly 6-month trend
    val monthlyStats = remember(data.transactions) {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        val months = (5 downTo 0).map { offset ->
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -offset)
            sdf.format(c.time)
        }
        months.map { ym ->
            val inc = data.transactions.filter { it.isIncome && it.date.startsWith(ym) }.sumOf { it.amount }
            val exp = data.transactions.filter { !it.isIncome && it.date.startsWith(ym) }.sumOf { it.amount }
            Triple(ym, inc, exp)
        }
    }

    val categoryBreakdown = remember(periodTransactions, breakdownType) {
        val filtered = periodTransactions.filter { it.txType == breakdownType }
        val total = filtered.sumOf { it.amount }.coerceAtLeast(1.0)
        filtered.groupBy { it.category }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                Triple(cat, sum, (sum / total).toFloat())
            }
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "আর্থিক রিপোর্ট ও বিশ্লেষণ",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(p.label) }
                    )
                }
            }
        }

        // Summary Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox(
                    title = "মোট আয়",
                    value = formatTaka(totalIncome, sym),
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "মোট ব্যয়",
                    value = formatTaka(totalExpense, sym),
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox(
                    title = "নিট ব্যালেন্স",
                    value = formatTaka(net, sym),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "সঞ্চয় হার",
                    value = "${toBanglaDigits(savingsRate.toString())}%",
                    color = if (savingsRate >= 0) IncomeGreen else ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6-Month Bar Chart
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("গত ৬ মাসের আয় ও ব্যয়", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LegendDot("আয়", IncomeGreen)
                            LegendDot("ব্যয়", ExpenseRed)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val maxVal = monthlyStats.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1.0) ?: 1.0
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        val groupCount = monthlyStats.size
                        val groupWidth = size.width / groupCount
                        val barWidth = (groupWidth * 0.28f).coerceAtMost(28.dp.toPx())
                        val gap = 4.dp.toPx()

                        monthlyStats.forEachIndexed { idx, (_, inc, exp) ->
                            val centerX = groupWidth * idx + groupWidth / 2f
                            val incHeight = ((inc / maxVal) * (size.height - 12.dp.toPx())).toFloat().coerceAtLeast(if (inc > 0) 6f else 2f)
                            val expHeight = ((exp / maxVal) * (size.height - 12.dp.toPx())).toFloat().coerceAtLeast(if (exp > 0) 6f else 2f)

                            drawRoundRect(
                                color = IncomeGreen,
                                topLeft = Offset(centerX - barWidth - gap / 2f, size.height - incHeight),
                                size = Size(barWidth, incHeight),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            drawRoundRect(
                                color = ExpenseRed,
                                topLeft = Offset(centerX + gap / 2f, size.height - expHeight),
                                size = Size(barWidth, expHeight),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        monthlyStats.forEach { (ym, _, _) ->
                            Text(
                                text = formatBanglaMonth(ym).split(" ").firstOrNull()?.take(4).orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Category Donut Chart & Breakdown
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("খাতভিত্তিক হিসাব", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = breakdownType == "expense",
                                onClick = { breakdownType = "expense" },
                                label = { Text("ব্যয়") }
                            )
                            FilterChip(
                                selected = breakdownType == "income",
                                onClick = { breakdownType = "income" },
                                label = { Text("আয়") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (categoryBreakdown.isEmpty()) {
                        Text(
                            text = "নির্বাচিত সময়ে কোনো তথ্য পাওয়া যায়নি।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(156.dp)) {
                                val strokeWidth = 26.dp.toPx()
                                var startAngle = -90f
                                categoryBreakdown.forEach { (cat, _, fraction) ->
                                    val sweep = (fraction * 360f).coerceAtLeast(3f)
                                    drawArc(
                                        color = categoryColor(cat),
                                        startAngle = startAngle,
                                        sweepAngle = sweep - 1.5f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    )
                                    startAngle += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (breakdownType == "expense") "মোট ব্যয়" else "মোট আয়",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTaka(if (breakdownType == "expense") totalExpense else totalIncome, sym),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        categoryBreakdown.forEach { (cat, sum, fraction) ->
                            val pct = (fraction * 100).toInt()
                            val color = categoryColor(cat)
                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cat, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text(
                                        text = "${formatTaka(sum, sym)} (${toBanglaDigits(pct.toString())}%)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = color,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
