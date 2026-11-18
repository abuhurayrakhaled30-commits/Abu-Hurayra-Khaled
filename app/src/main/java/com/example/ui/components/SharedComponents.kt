package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.TransactionItem
import com.example.model.formatBanglaDate
import com.example.model.formatTaka
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.util.BackupAndExportUtils
import kotlin.math.absoluteValue

fun categoryColor(category: String): Color {
    val index = category.hashCode().absoluteValue % ChartPalette.size
    return ChartPalette[index]
}

fun categoryIcon(category: String, isIncome: Boolean): ImageVector {
    return when {
        category.contains("খাবার") || category.contains("বাজার") -> Icons.Filled.Restaurant
        category.contains("ভাড়া") || category.contains("বাড়ি") -> Icons.Filled.Home
        category.contains("যাতায়াত") || category.contains("গাড়ি") -> Icons.Filled.DirectionsCar
        category.contains("বিল") || category.contains("রিচার্জ") -> Icons.Filled.Receipt
        category.contains("চিকিৎসা") || category.contains("ওষুধ") -> Icons.Filled.LocalHospital
        category.contains("শিক্ষা") || category.contains("বই") -> Icons.Filled.School
        category.contains("কেনাকাটা") || category.contains("পোশাক") -> Icons.Filled.ShoppingBag
        category.contains("পরিবার") || category.contains("উপহার") || category.contains("বোনাস") -> Icons.Filled.CardGiftcard
        category.contains("বিনোদন") -> Icons.Filled.Movie
        category.contains("বেতন") || category.contains("ফ্রিল্যান্সিং") -> Icons.Filled.Work
        category.contains("ব্যবসা") || category.contains("বিনিয়োগ") -> Icons.Filled.TrendingUp
        category.contains("ব্যাংক") -> Icons.Filled.AccountBalance
        isIncome -> Icons.Filled.AttachMoney
        else -> Icons.Filled.Category
    }
}

@Composable
fun TransactionRowCard(
    item: TransactionItem,
    currencySymbol: String,
    onEdit: () -> Unit,
    onViewReceipt: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = categoryColor(item.category)
    val amountColor = if (item.isIncome) IncomeGreen else ExpenseRed
    val signPrefix = if (item.isIncome) "+" else "-"
    val receiptBitmap = remember(item.receiptBase64) {
        BackupAndExportUtils.decodeReceiptBitmap(item.receiptBase64)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .testTag("transaction_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(item.category, item.isIncome),
                    contentDescription = item.category,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.note.isNotBlank()) {
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = formatBanglaDate(item.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.paymentMethod,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (receiptBitmap != null && item.receiptBase64 != null) {
                Image(
                    bitmap = receiptBitmap,
                    contentDescription = "রসিদের ছবি দেখুন",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewReceipt(item.receiptBase64) }
                        .testTag("receipt_thumb_${item.id}"),
                    contentScale = ContentScale.Crop
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$signPrefix${formatTaka(item.amount, currencySymbol)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .testTag("delete_tx_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "লেনদেন মুছুন",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
