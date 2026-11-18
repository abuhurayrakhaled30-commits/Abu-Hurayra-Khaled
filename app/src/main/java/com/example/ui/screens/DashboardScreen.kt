package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.TransactionItem
import com.example.model.currentYearMonth
import com.example.model.formatBanglaMonth
import com.example.model.formatTaka
import com.example.model.toBanglaDigits
import com.example.ui.HisabDashboardData
import com.example.ui.components.TransactionRowCard
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.PayablePurple
import com.example.ui.theme.ReceivableAmber

@Composable
fun DashboardScreen(
    data: HisabDashboardData,
    userPhotoUrl: String?,
    onToggleDarkMode: () -> Unit,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onAddDebt: () -> Unit,
    onImportBackup: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    val sym = data.config.currencySymbol
    val thisMonth = currentYearMonth()
    val monthlyTx = data.transactions.filter { it.date.startsWith(thisMonth) }

    val totalIncome = data.transactions.filter { it.isIncome }.sumOf { it.amount }
    val totalExpense = data.transactions.filter { !it.isIncome }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    val monthIncome = monthlyTx.filter { it.isIncome }.sumOf { it.amount }
    val monthExpense = monthlyTx.filter { !it.isIncome }.sumOf { it.amount }

    val totalReceivable = data.debts.filter { it.isReceivable && !it.settled }.sumOf { it.remainingAmount }
    val totalPayable = data.debts.filter { !it.isReceivable && !it.settled }.sumOf { it.remainingAmount }

    val recentTx = data.transactions.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!userPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userPhotoUrl,
                            contentDescription = "প্রোফাইল ছবি",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "আমার হিসাব",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = data.config.userName.ifBlank { "ক্লাউড সিঙ্ক চালু আছে" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier.testTag("dashboard_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (data.config.darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (data.config.darkMode) "হালকা মোড" else "ডার্ক মোড",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Hero Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0C6F5B),
                                    Color(0xFF0A4F41),
                                    Color(0xFF08392F)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "মোট অবশিষ্ট ব্যালেন্স",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = formatBanglaMonth(thisMonth),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = formatTaka(netBalance, sym),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34D399).copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.ArrowDownward,
                                            contentDescription = null,
                                            tint = Color(0xFF6EE7B7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "এই মাসের আয়",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = formatTaka(monthIncome, sym),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF6EE7B7),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF87171).copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color(0xFFFCA5A5),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "এই মাসের ব্যয়",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = formatTaka(monthExpense, sym),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFFFCA5A5),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (data.config.monthlyBudget > 0.0) {
                            val ratio = (monthExpense / data.config.monthlyBudget).toFloat().coerceIn(0f, 1f)
                            val pct = ((monthExpense / data.config.monthlyBudget) * 100).toInt()
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "মাসিক বাজেট (${formatTaka(data.config.monthlyBudget, sym)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "${toBanglaDigits(pct.toString())}% ব্যবহৃত",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (pct > 90) Color(0xFFFCA5A5) else Color(0xFF6EE7B7)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (pct > 90) Color(0xFFF87171) else Color(0xFF34D399),
                                trackColor = Color.White.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    label = "আয় যোগ",
                    icon = Icons.Filled.Add,
                    tint = IncomeGreen,
                    onClick = onAddIncome,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_income")
                )
                QuickActionCard(
                    label = "ব্যয় যোগ",
                    icon = Icons.Filled.Remove,
                    tint = ExpenseRed,
                    onClick = onAddExpense,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_expense")
                )
                QuickActionCard(
                    label = "দেনা-পাওনা",
                    icon = Icons.Filled.Handshake,
                    tint = ReceivableAmber,
                    onClick = onAddDebt,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_debt")
                )
            }
        }

        // Debt Summary Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "মোট পাওনা (পাবো)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTaka(totalReceivable, sym),
                            style = MaterialTheme.typography.titleLarge,
                            color = ReceivableAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "মোট দেনা (দিবো)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTaka(totalPayable, sym),
                            style = MaterialTheme.typography.titleLarge,
                            color = PayablePurple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Empty-state prompt for old JSON backup import if no transactions yet
        if (data.transactions.isEmpty() && data.debts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.UploadFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "আগের 'আমার হিসাব' ব্যাকআপ আছে?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "আপনার পুরোনো amar_hisab JSON ব্যাকআপ ফাইল এক ক্লিকে ইমপোর্ট করে সব হিসাব এখানে নিয়ে আসুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onImportBackup,
                            modifier = Modifier.testTag("dashboard_import_backup_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ব্যাকআপ ফাইল ইমপোর্ট করুন")
                        }
                    }
                }
            }
        }

        // Recent Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "সাম্প্রতিক লেনদেন",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (data.transactions.isNotEmpty()) {
                    TextButton(onClick = onSeeAllTransactions) {
                        Text("সব দেখুন (${toBanglaDigits(data.transactions.size.toString())})")
                    }
                }
            }
        }

        if (recentTx.isEmpty()) {
            item {
                Text(
                    text = "এখনও কোনো লেনদেন যোগ করা হয়নি। নিচের '+' বোতামে চাপ দিয়ে শুরু করুন।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(recentTx, key = { it.id }) { tx ->
                TransactionRowCard(
                    item = tx,
                    currencySymbol = sym,
                    onEdit = { onEditTransaction(tx) },
                    onViewReceipt = onViewReceipt
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
