package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.TransactionItem
import com.example.model.formatBanglaMonth
import com.example.model.formatTaka
import com.example.model.toBanglaDigits
import com.example.ui.HisabDashboardData
import com.example.ui.components.TransactionRowCard
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

@Composable
fun TransactionsScreen(
    data: HisabDashboardData,
    onEditTransaction: (TransactionItem) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    val sym = data.config.currencySymbol
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("all") } // all, income, expense
    var selectedMonth by remember { mutableStateOf("all") } // all or YYYY-MM
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val availableMonths = remember(data.transactions) {
        data.transactions
            .mapNotNull { it.date.takeIf { d -> d.length >= 7 }?.substring(0, 7) }
            .distinct()
            .sortedDescending()
    }

    val filtered = remember(data.transactions, searchQuery, typeFilter, selectedMonth) {
        val q = searchQuery.trim().lowercase()
        data.transactions.filter { tx ->
            val matchType = typeFilter == "all" || tx.txType == typeFilter
            val matchMonth = selectedMonth == "all" || tx.date.startsWith(selectedMonth)
            val matchQuery = q.isEmpty() ||
                tx.category.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                tx.paymentMethod.lowercase().contains(q)
            matchType && matchMonth && matchQuery
        }
    }

    val filteredIncome = filtered.filter { it.isIncome }.sumOf { it.amount }
    val filteredExpense = filtered.filter { !it.isIncome }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "সকল লেনদেন",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("খাত, নোট বা মাধ্যম দিয়ে খুঁজুন...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "পরিষ্কার করুন")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == "all",
                onClick = { typeFilter = "all" },
                label = { Text("সব (${toBanglaDigits(data.transactions.size.toString())})") }
            )
            FilterChip(
                selected = typeFilter == "income",
                onClick = { typeFilter = "income" },
                label = { Text("আয়") }
            )
            FilterChip(
                selected = typeFilter == "expense",
                onClick = { typeFilter = "expense" },
                label = { Text("ব্যয়") }
            )
            FilterChip(
                selected = selectedMonth == "all",
                onClick = { selectedMonth = "all" },
                label = { Text("সব মাস") }
            )
            availableMonths.forEach { ym ->
                FilterChip(
                    selected = selectedMonth == ym,
                    onClick = { selectedMonth = if (selectedMonth == ym) "all" else ym },
                    label = { Text(formatBanglaMonth(ym)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "আয়: ${formatTaka(filteredIncome, sym)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = IncomeGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ব্যয়: ${formatTaka(filteredExpense, sym)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ExpenseRed,
                    fontWeight = FontWeight.Bold
                )
            }
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
                    Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.height(52.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "কোনো লেনদেন পাওয়া যায়নি",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    TransactionRowCard(
                        item = tx,
                        currencySymbol = sym,
                        onEdit = { onEditTransaction(tx) },
                        onViewReceipt = onViewReceipt,
                        onDelete = { pendingDeleteId = tx.id }
                    )
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("লেনদেন মুছবেন?") },
            text = { Text("এই লেনদেনটি স্থায়ীভাবে মুছে ফেলা হবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction(pendingDeleteId!!)
                        pendingDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("বাতিল") }
            }
        )
    }
}
