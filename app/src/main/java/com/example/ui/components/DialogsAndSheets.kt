package com.example.ui.components

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DebtItem
import com.example.model.PAYMENT_METHODS
import com.example.model.ParsedBackup
import com.example.model.TransactionItem
import com.example.model.UserConfig
import com.example.model.formatBanglaDate
import com.example.model.formatTaka
import com.example.model.toBanglaDigits
import com.example.model.todayIsoDate
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.util.BackupAndExportUtils
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditTransactionDialog(
    existing: TransactionItem?,
    initialType: String,
    config: UserConfig,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (TransactionItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var txType by remember(existing, initialType) {
        mutableStateOf(existing?.txType ?: initialType)
    }
    val categories = if (txType == "income") config.incomeCategories else config.expenseCategories

    var amountText by remember(existing) {
        mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var selectedCategory by remember(existing, txType) {
        mutableStateOf(
            existing?.category?.takeIf { it.isNotBlank() }
                ?: categories.firstOrNull().orEmpty()
        )
    }
    var dateText by remember(existing) { mutableStateOf(existing?.date ?: todayIsoDate()) }
    var paymentMethod by remember(existing) { mutableStateOf(existing?.paymentMethod ?: "নগদ") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var receiptBase64 by remember(existing) { mutableStateOf(existing?.receiptBase64) }
    var isCompressingImage by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val receiptBitmap = remember(receiptBase64) {
        BackupAndExportUtils.decodeReceiptBitmap(receiptBase64)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isCompressingImage = true
                val compressed = BackupAndExportUtils.compressReceiptImage(context, uri)
                isCompressingImage = false
                if (compressed != null) {
                    receiptBase64 = compressed
                } else {
                    validationError = "ছবিটি সংকুচিত করা যায়নি, অন্য ছবি চেষ্টা করুন"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existing == null) "নতুন লেনদেন" else "লেনদেন সম্পাদনা",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন")
                    }
                }

                // Income / Expense Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = txType == "expense",
                        onClick = {
                            txType = "expense"
                            if (!config.expenseCategories.contains(selectedCategory)) {
                                selectedCategory = config.expenseCategories.firstOrNull().orEmpty()
                            }
                        },
                        label = { Text("ব্যয় (খরচ)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_type_expense_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.16f),
                            selectedLabelColor = ExpenseRed
                        )
                    )
                    FilterChip(
                        selected = txType == "income",
                        onClick = {
                            txType = "income"
                            if (!config.incomeCategories.contains(selectedCategory)) {
                                selectedCategory = config.incomeCategories.firstOrNull().orEmpty()
                            }
                        },
                        label = { Text("আয় (জমা)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_type_income_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IncomeGreen.copy(alpha = 0.16f),
                            selectedLabelColor = IncomeGreen
                        )
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; validationError = null },
                    label = { Text("পরিমাণ (${config.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_amount_input")
                )

                Text(
                    text = "খাত নির্বাচন করুন",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat; validationError = null },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                // Date Picker Row
                OutlinedButton(
                    onClick = {
                        showDatePicker(context, dateText) { picked ->
                            dateText = picked
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_date_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("তারিখ: ${formatBanglaDate(dateText)}")
                }

                Text(
                    text = "লেনদেনের মাধ্যম",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PAYMENT_METHODS.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 300) note = it },
                    label = { Text("বিবরণ / নোট (ঐচ্ছিক)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_note_input")
                )

                // Receipt Attachment Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "রসিদের ছবি (স্বয়ংক্রিয়ভাবে ছোট করে সংরক্ষিত হবে)",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isCompressingImage,
                                modifier = Modifier.testTag("pick_receipt_button")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (receiptBitmap == null) "ছবি যোগ" else "পরিবর্তন")
                            }
                        }

                        if (isCompressingImage) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("রসিদ ছোট করা হচ্ছে...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (receiptBitmap != null) {
                            Box(modifier = Modifier.padding(top = 8.dp)) {
                                Image(
                                    bitmap = receiptBitmap,
                                    contentDescription = "সংযুক্ত রসিদ",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { receiptBase64 = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            RoundedCornerShape(50)
                                        )
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "রসিদ সরান",
                                        tint = ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("বাতিল")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isBusy && !isCompressingImage,
                        onClick = {
                            val parsed = amountText.trim().toDoubleOrNull()
                            if (parsed == null || parsed <= 0.0 || parsed > 1_000_000_000.0) {
                                validationError = "সঠিক পরিমাণ লিখুন (০ এর বেশি)"
                                return@Button
                            }
                            if (selectedCategory.isBlank()) {
                                validationError = "একটি খাত নির্বাচন করুন"
                                return@Button
                            }
                            onSave(
                                TransactionItem(
                                    id = existing?.id.orEmpty(),
                                    txType = txType,
                                    amount = parsed,
                                    category = selectedCategory,
                                    note = note.trim(),
                                    date = dateText,
                                    paymentMethod = paymentMethod,
                                    receiptBase64 = receiptBase64
                                )
                            )
                        },
                        modifier = Modifier.testTag("save_transaction_button")
                    ) {
                        Text(if (isBusy) "সংরক্ষণ হচ্ছে..." else "সংরক্ষণ করুন")
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditDebtDialog(
    existing: DebtItem?,
    currencySymbol: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (DebtItem) -> Unit
) {
    val context = LocalContext.current
    var debtType by remember(existing) { mutableStateOf(existing?.debtType ?: "gave") }
    var personName by remember(existing) { mutableStateOf(existing?.personName ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var amountText by remember(existing) {
        mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var paidText by remember(existing) {
        mutableStateOf(existing?.paidAmount?.let { if (it == 0.0) "" else if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var dateText by remember(existing) { mutableStateOf(existing?.date ?: todayIsoDate()) }
    var dueDateText by remember(existing) { mutableStateOf(existing?.dueDate ?: "") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existing == null) "নতুন দেনা / পাওনা" else "দেনা-পাওনা সম্পাদনা",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = debtType == "gave",
                        onClick = { debtType = "gave" },
                        label = { Text("পাওনা (দিয়েছি)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = debtType == "took",
                        onClick = { debtType = "took" },
                        label = { Text("দেনা (নিয়েছি)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = personName,
                    onValueChange = { if (it.length <= 80) personName = it; errorMsg = null },
                    label = { Text("ব্যক্তি বা প্রতিষ্ঠানের নাম") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_person_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 30) phone = it },
                    label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; errorMsg = null },
                        label = { Text("মোট টাকা ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("debt_amount_input")
                    )
                    OutlinedTextField(
                        value = paidText,
                        onValueChange = { paidText = it; errorMsg = null },
                        label = { Text("পরিশোধিত") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker(context, dateText) { dateText = it } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("তারিখ: ${toBanglaDigits(dateText)}", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            showDatePicker(context, dueDateText.ifBlank { todayIsoDate() }) { dueDateText = it }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (dueDateText.isBlank()) "পরিশোধের তারিখ" else "শোধ: ${toBanglaDigits(dueDateText)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 300) note = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("বাতিল") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            val cleanName = personName.trim()
                            val amt = amountText.trim().toDoubleOrNull()
                            val paid = paidText.trim().toDoubleOrNull() ?: 0.0
                            if (cleanName.isEmpty()) {
                                errorMsg = "ব্যক্তির নাম লিখুন"
                                return@Button
                            }
                            if (amt == null || amt <= 0.0 || amt > 1_000_000_000.0) {
                                errorMsg = "সঠিক টাকার পরিমাণ দিন"
                                return@Button
                            }
                            if (paid < 0.0 || paid > amt) {
                                errorMsg = "পরিশোধিত টাকা মোট টাকার চেয়ে বেশি হতে পারবে না"
                                return@Button
                            }
                            onSave(
                                DebtItem(
                                    id = existing?.id.orEmpty(),
                                    personName = cleanName,
                                    phone = phone.trim(),
                                    debtType = debtType,
                                    amount = amt,
                                    paidAmount = paid,
                                    note = note.trim(),
                                    date = dateText,
                                    dueDate = dueDateText,
                                    settled = paid >= amt
                                )
                            )
                        },
                        modifier = Modifier.testTag("save_debt_button")
                    ) {
                        Text(if (isBusy) "সংরক্ষণ হচ্ছে..." else "সংরক্ষণ করুন")
                    }
                }
            }
        }
    }
}

@Composable
fun DebtPaymentDialog(
    debt: DebtItem,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirmPayment: (Double) -> Unit
) {
    var payText by remember {
        mutableStateOf(
            debt.remainingAmount.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
        )
    }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("আংশিক / পূর্ণ পরিশোধ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${debt.personName} — বাকি: ${formatTaka(debt.remainingAmount, currencySymbol)}")
                OutlinedTextField(
                    value = payText,
                    onValueChange = { payText = it; err = null },
                    label = { Text("জমা বা পরিশোধের পরিমাণ ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                if (err != null) {
                    Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val valNum = payText.trim().toDoubleOrNull()
                if (valNum == null || valNum <= 0.0 || valNum > debt.remainingAmount + 0.01) {
                    err = "সঠিক পরিমাণ লিখুন (সর্বোচ্চ ${formatTaka(debt.remainingAmount, currencySymbol)})"
                } else {
                    onConfirmPayment(valNum.coerceAtMost(debt.remainingAmount))
                }
            }) {
                Text("জমা দিন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun ReceiptPreviewDialog(
    receiptBase64: String,
    onDismiss: () -> Unit
) {
    val bmp = remember(receiptBase64) { BackupAndExportUtils.decodeReceiptBitmap(receiptBase64) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("রসিদের ছবি", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "বন্ধ করুন")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "রসিদ",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("ছবিটি প্রদর্শন করা যায়নি", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BackupImportPreviewDialog(
    backup: ParsedBackup,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmImport: (replaceExisting: Boolean) -> Unit
) {
    var replaceMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ব্যাকআপ ইমপোর্ট নিশ্চিত করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ফাইল সংস্করণ: v${toBanglaDigits(backup.version.toString())}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("• লেনদেন পাওয়া গেছে: ${toBanglaDigits(backup.transactions.size.toString())}টি")
                Text("• দেনা-পাওনা পাওয়া গেছে: ${toBanglaDigits(backup.debts.size.toString())}টি")
                Text("• সেটিংস ও খাত: ${if (backup.config != null) "হ্যাঁ" else "নেই"}")
                Spacer(modifier = Modifier.height(6.dp))
                Text("কিভাবে ইমপোর্ট করতে চান?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !replaceMode,
                        onClick = { replaceMode = false },
                        label = { Text("যুক্ত করুন (Merge)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = replaceMode,
                        onClick = { replaceMode = true },
                        label = { Text("প্রতিস্থাপন (Replace)") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ExpenseRed.copy(alpha = 0.14f),
                            selectedLabelColor = ExpenseRed
                        )
                    )
                }
                if (replaceMode) {
                    Text(
                        "সতর্কতা: প্রতিস্থাপন নির্বাচন করলে বর্তমান ক্লাউডের আগের লেনদেন ও দেনা-পাওনা মুছে এই ব্যাকআপটি বসবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = { onConfirmImport(replaceMode) },
                colors = if (replaceMode) {
                    ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("confirm_backup_import_button")
            ) {
                Text(if (isBusy) "ইমপোর্ট হচ্ছে..." else "ইমপোর্ট শুরু করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

fun showDatePicker(
    context: android.content.Context,
    currentIso: String,
    onPicked: (String) -> Unit
) {
    val parts = currentIso.split("-")
    val cal = Calendar.getInstance()
    val year = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
    val month = (parts.getOrNull(1)?.toIntOrNull()?.minus(1)) ?: cal.get(Calendar.MONTH)
    val day = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, y, m, d ->
            onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        },
        year,
        month,
        day
    ).show()
}
