package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DebtItem
import com.example.model.formatBanglaDate
import com.example.model.formatTaka
import com.example.model.toBanglaDigits
import com.example.ui.HisabDashboardData
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.PayablePurple
import com.example.ui.theme.ReceivableAmber

@Composable
fun DebtsScreen(
    data: HisabDashboardData,
    onEditDebt: (DebtItem) -> Unit,
    onRecordPayment: (DebtItem) -> Unit,
    onToggleSettled: (DebtItem) -> Unit,
    onDeleteDebt: (String) -> Unit
) {
    val context = LocalContext.current
    val sym = data.config.currencySymbol
    var filter by remember { mutableStateOf("active") } // active, gave, took, settled, all
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val totalGave = data.debts.filter { it.isReceivable && !it.settled }.sumOf { it.remainingAmount }
    val totalTook = data.debts.filter { !it.isReceivable && !it.settled }.sumOf { it.remainingAmount }

    val filtered = remember(data.debts, filter) {
        when (filter) {
            "active" -> data.debts.filter { !it.settled }
            "gave" -> data.debts.filter { it.isReceivable && !it.settled }
            "took" -> data.debts.filter { !it.isReceivable && !it.settled }
            "settled" -> data.debts.filter { it.settled }
            else -> data.debts
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "দেনা ও পাওনা",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ReceivableAmber.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("মোট পাওনা (পাবো)", style = MaterialTheme.typography.labelMedium, color = ReceivableAmber)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTaka(totalGave, sym),
                        style = MaterialTheme.typography.titleLarge,
                        color = ReceivableAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PayablePurple.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("মোট দেনা (দিবো)", style = MaterialTheme.typography.labelMedium, color = PayablePurple)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTaka(totalTook, sym),
                        style = MaterialTheme.typography.titleLarge,
                        color = PayablePurple,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == "active", onClick = { filter = "active" }, label = { Text("চলমান") })
            FilterChip(selected = filter == "gave", onClick = { filter = "gave" }, label = { Text("পাওনা (দিয়েছি)") })
            FilterChip(selected = filter == "took", onClick = { filter = "took" }, label = { Text("দেনা (নিয়েছি)") })
            FilterChip(selected = filter == "settled", onClick = { filter = "settled" }, label = { Text("পরিশোধিত") })
            FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("সব (${toBanglaDigits(data.debts.size.toString())})") })
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Handshake,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "এই তালিকায় কোনো দেনা-পাওনা নেই",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { debt ->
                    val accent = if (debt.isReceivable) ReceivableAmber else PayablePurple
                    val progress = if (debt.amount > 0) (debt.paidAmount / debt.amount).toFloat().coerceIn(0f, 1f) else 0f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onEditDebt(debt) }
                            .testTag("debt_card_${debt.id}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = debt.personName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = accent.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = if (debt.isReceivable) "পাওনা" else "দেনা",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accent,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (debt.settled) {
                                            Surface(
                                                color = IncomeGreen.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = "পরিশোধিত",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = IncomeGreen,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "তারিখ: ${formatBanglaDate(debt.date)}" +
                                            if (debt.dueDate.isNotBlank()) " • শোধ: ${formatBanglaDate(debt.dueDate)}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (debt.phone.isNotBlank()) {
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${debt.phone}"))
                                        context.startActivity(intent)
                                    }) {
                                        Icon(Icons.Filled.Call, contentDescription = "কল করুন", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { pendingDeleteId = debt.id }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "মুছে ফেলুন", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (debt.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = debt.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "মোট: ${formatTaka(debt.amount, sym)} • শোধ: ${formatTaka(debt.paidAmount, sym)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "বাকি: ${formatTaka(debt.remainingAmount, sym)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (debt.settled) IncomeGreen else accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (debt.settled) IncomeGreen else accent,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!debt.settled) {
                                    OutlinedButton(
                                        onClick = { onRecordPayment(debt) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("টাকা জমা", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onToggleSettled(debt) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (debt.settled) "পুনরায় চালু" else "পূর্ণ পরিশোধ",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("দেনা-পাওনা মুছবেন?") },
            text = { Text("এই হিসাবটি স্থায়ীভাবে মুছে ফেলা হবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDebt(pendingDeleteId!!)
                        pendingDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("মুছে ফেলুন") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("বাতিল") }
            }
        )
    }
}
