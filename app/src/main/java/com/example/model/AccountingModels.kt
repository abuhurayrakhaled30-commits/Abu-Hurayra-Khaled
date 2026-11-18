package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val DEFAULT_INCOME_CATEGORIES = listOf(
    "বেতন",
    "ব্যবসা",
    "ফ্রিল্যান্সিং",
    "বোনাস ও উপহার",
    "ভাড়া আয়",
    "বিনিয়োগ লাভ",
    "অন্যান্য আয়"
)

val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "খাবার ও বাজার",
    "বাড়ি ভাড়া",
    "যাতায়াত",
    "বিল ও রিচার্জ",
    "চিকিৎসা",
    "শিক্ষা",
    "কেনাকাটা",
    "পরিবার ও উপহার",
    "বিনোদন",
    "অন্যান্য ব্যয়"
)

val PAYMENT_METHODS = listOf(
    "নগদ",
    "বিকাশ",
    "নগদ (অ্যাপ)",
    "রকেট",
    "ব্যাংক",
    "কার্ড"
)

@Keep
@IgnoreExtraProperties
data class UserConfig(
    @DocumentId val id: String = "config",
    val userId: String = "",
    val userName: String = "",
    val darkMode: Boolean = false,
    val currencySymbol: String = "৳",
    val monthlyBudget: Double = 0.0,
    val incomeCategories: List<String> = DEFAULT_INCOME_CATEGORIES,
    val expenseCategories: List<String> = DEFAULT_EXPENSE_CATEGORIES,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TransactionItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = "expense", // "income" or "expense"
    val amount: Double = 0.0,
    val category: String = "অন্যান্য ব্যয়",
    val note: String = "",
    val date: String = todayIsoDate(),
    val paymentMethod: String = "নগদ",
    val receiptBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isIncome: Boolean get() = txType == "income"
}

@Keep
@IgnoreExtraProperties
data class DebtItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personName: String = "",
    val phone: String = "",
    val debtType: String = "gave", // "gave" (পাওনা) or "took" (দেনা)
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val note: String = "",
    val date: String = todayIsoDate(),
    val dueDate: String = "",
    val settled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val remainingAmount: Double get() = (amount - paidAmount).coerceAtLeast(0.0)
    val isReceivable: Boolean get() = debtType == "gave"
}

data class ParsedBackup(
    val version: Int,
    val transactions: List<TransactionItem>,
    val debts: List<DebtItem>,
    val config: UserConfig?
)

data class ImportSummary(
    val importedTransactions: Int,
    val importedDebts: Int,
    val configUpdated: Boolean
)

fun todayIsoDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

fun currentYearMonth(): String {
    val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
    return sdf.format(Date())
}

fun toBanglaDigits(input: String): String {
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(input.length)
    for (ch in input) {
        if (ch in '0'..'9') {
            sb.append(banglaDigits[ch - '0'])
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

fun formatTaka(amount: Double, symbol: String = "৳"): String {
    val absVal = kotlin.math.abs(amount)
    val formatter = if (absVal % 1.0 == 0.0) {
        DecimalFormat("#,##0")
    } else {
        DecimalFormat("#,##0.##")
    }
    val formatted = toBanglaDigits(formatter.format(absVal))
    return if (amount < 0) "-$symbol$formatted" else "$symbol$formatted"
}

private val BANGLA_MONTHS = mapOf(
    "01" to "জানুয়ারি",
    "02" to "ফেব্রুয়ারি",
    "03" to "মার্চ",
    "04" to "এপ্রিল",
    "05" to "মে",
    "06" to "জুন",
    "07" to "জুলাই",
    "08" to "আগস্ট",
    "09" to "সেপ্টেম্বর",
    "10" to "অক্টোবর",
    "11" to "নভেম্বর",
    "12" to "ডিসেম্বর"
)

fun formatBanglaDate(isoDate: String): String {
    val parts = isoDate.split("-")
    if (parts.size != 3) return toBanglaDigits(isoDate)
    val year = toBanglaDigits(parts[0])
    val month = BANGLA_MONTHS[parts[1]] ?: toBanglaDigits(parts[1])
    val day = toBanglaDigits(parts[2].trimStart('0').ifEmpty { "0" })
    return "$day $month, $year"
}

fun formatBanglaMonth(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return toBanglaDigits(yearMonth)
    val year = toBanglaDigits(parts[0])
    val month = BANGLA_MONTHS[parts[1]] ?: toBanglaDigits(parts[1])
    return "$month $year"
}
