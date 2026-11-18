package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.DEFAULT_EXPENSE_CATEGORIES
import com.example.model.DEFAULT_INCOME_CATEGORIES
import com.example.model.DebtItem
import com.example.model.ParsedBackup
import com.example.model.TransactionItem
import com.example.model.UserConfig
import com.example.model.todayIsoDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupAndExportUtils {

    fun buildBackupJson(
        transactions: List<TransactionItem>,
        debts: List<DebtItem>,
        config: UserConfig
    ): String {
        val root = JSONObject()
        root.put("v", 2)
        root.put("app", "amar_hisab")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val txArray = JSONArray()
        for (tx in transactions) {
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("type", tx.txType)
                put("txType", tx.txType)
                put("amount", tx.amount)
                put("category", tx.category)
                put("note", tx.note)
                put("date", tx.date)
                put("paymentMethod", tx.paymentMethod)
                if (!tx.receiptBase64.isNullOrBlank()) {
                    put("receiptBase64", tx.receiptBase64)
                }
            }
            txArray.put(obj)
        }
        root.put("tx", txArray)

        val debtsArray = JSONArray()
        for (debt in debts) {
            val obj = JSONObject().apply {
                put("id", debt.id)
                put("personName", debt.personName)
                put("phone", debt.phone)
                put("type", debt.debtType)
                put("debtType", debt.debtType)
                put("amount", debt.amount)
                put("paidAmount", debt.paidAmount)
                put("note", debt.note)
                put("date", debt.date)
                put("dueDate", debt.dueDate)
                put("settled", debt.settled)
            }
            debtsArray.put(obj)
        }
        root.put("debts", debtsArray)

        val cfgObj = JSONObject().apply {
            put("userName", config.userName)
            put("darkMode", config.darkMode)
            put("currencySymbol", config.currencySymbol)
            put("monthlyBudget", config.monthlyBudget)
            put("incomeCategories", JSONArray(config.incomeCategories))
            put("expenseCategories", JSONArray(config.expenseCategories))
        }
        root.put("cfg", cfgObj)

        return root.toString(2)
    }

    fun parseBackupJson(jsonText: String): ParsedBackup {
        val clean = jsonText.trim().removePrefix("\uFEFF")
        val root = JSONObject(clean)
        val version = root.optInt("v", root.optInt("version", 2))

        val txJsonArray = root.optJSONArray("tx")
            ?: root.optJSONArray("transactions")
            ?: JSONArray()
        val transactions = mutableListOf<TransactionItem>()
        for (i in 0 until txJsonArray.length()) {
            val obj = txJsonArray.optJSONObject(i) ?: continue
            val rawType = (obj.optString("txType").ifBlank {
                obj.optString("type").ifBlank { obj.optString("kind") }
            }).lowercase(Locale.US)
            val txType = when {
                rawType == "income" || rawType == "in" || rawType == "credit" || rawType == "আয়" -> "income"
                else -> "expense"
            }

            val amount = listOf("amount", "amt", "val", "value")
                .firstNotNullOfOrNull { key ->
                    if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null
                } ?: 0.0
            if (amount <= 0.0) continue

            val category = obj.optString("category")
                .ifBlank { obj.optString("cat") }
                .ifBlank { if (txType == "income") "অন্যান্য আয়" else "অন্যান্য ব্যয়" }
                .take(60)
            val note = obj.optString("note")
                .ifBlank { obj.optString("desc") }
                .ifBlank { obj.optString("description") }
                .take(300)
            val date = normalizeIsoDate(
                obj.optString("date").ifBlank { obj.optString("dt") }
            )
            val method = obj.optString("paymentMethod")
                .ifBlank { obj.optString("method") }
                .ifBlank { obj.optString("pm") }
                .ifBlank { "নগদ" }
                .take(40)
            val receipt = obj.optString("receiptBase64")
                .ifBlank { obj.optString("receipt") }
                .ifBlank { obj.optString("img") }
                .ifBlank { obj.optString("photo") }
                .takeIf { it.isNotBlank() && it.length <= 350_000 }

            transactions.add(
                TransactionItem(
                    id = obj.optString("id"),
                    txType = txType,
                    amount = amount,
                    category = category,
                    note = note,
                    date = date,
                    paymentMethod = method,
                    receiptBase64 = receipt
                )
            )
        }

        val debtsJsonArray = root.optJSONArray("debts")
            ?: root.optJSONArray("loans")
            ?: JSONArray()
        val debts = mutableListOf<DebtItem>()
        for (i in 0 until debtsJsonArray.length()) {
            val obj = debtsJsonArray.optJSONObject(i) ?: continue
            val personName = obj.optString("personName")
                .ifBlank { obj.optString("person") }
                .ifBlank { obj.optString("name") }
                .trim()
                .take(80)
            if (personName.isEmpty()) continue

            val amount = listOf("amount", "amt")
                .firstNotNullOfOrNull { key ->
                    if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null
                } ?: 0.0
            if (amount <= 0.0) continue

            val rawDebtType = (obj.optString("debtType").ifBlank {
                obj.optString("type").ifBlank { obj.optString("dir") }
            }).lowercase(Locale.US)
            val debtType = when {
                rawDebtType == "took" || rawDebtType == "payable" || rawDebtType == "borrow" || rawDebtType == "দেনা" || rawDebtType == "নিয়েছি" -> "took"
                else -> "gave"
            }

            val paidAmount = (if (obj.has("paidAmount")) obj.optDouble("paidAmount", 0.0) else obj.optDouble("paid", 0.0))
                .coerceIn(0.0, amount)
            val settled = obj.optBoolean("settled", obj.optBoolean("isSettled", obj.optBoolean("done", false))) ||
                paidAmount >= amount

            debts.add(
                DebtItem(
                    id = obj.optString("id"),
                    personName = personName,
                    phone = obj.optString("phone").ifBlank { obj.optString("mobile") }.take(30),
                    debtType = debtType,
                    amount = amount,
                    paidAmount = paidAmount,
                    note = obj.optString("note").ifBlank { obj.optString("desc") }.take(300),
                    date = normalizeIsoDate(obj.optString("date").ifBlank { obj.optString("dt") }),
                    dueDate = obj.optString("dueDate").ifBlank { obj.optString("due") }.take(20),
                    settled = settled
                )
            )
        }

        val cfgObj = root.optJSONObject("cfg") ?: root.optJSONObject("config")
        val parsedConfig = cfgObj?.let { cfg ->
            val incomeCats = mutableListOf<String>()
            val expenseCats = mutableListOf<String>()

            extractStringList(cfg.optJSONArray("incomeCategories") ?: cfg.optJSONArray("incCats"), incomeCats)
            extractStringList(cfg.optJSONArray("expenseCategories") ?: cfg.optJSONArray("expCats"), expenseCats)

            // Also support { cats: { income: [...], expense: [...] } } or { cats: [{ name, type }] }
            val catsObj = cfg.optJSONObject("cats")
            if (catsObj != null) {
                extractStringList(catsObj.optJSONArray("income"), incomeCats)
                extractStringList(catsObj.optJSONArray("expense"), expenseCats)
            } else {
                val catsArr = cfg.optJSONArray("cats") ?: cfg.optJSONArray("categories")
                if (catsArr != null) {
                    for (j in 0 until catsArr.length()) {
                        val cObj = catsArr.optJSONObject(j)
                        if (cObj != null) {
                            val name = cObj.optString("name").trim()
                            val type = cObj.optString("type").lowercase(Locale.US)
                            if (name.isNotEmpty()) {
                                if (type == "income" || type == "in") incomeCats.add(name)
                                else expenseCats.add(name)
                            }
                        } else {
                            val s = catsArr.optString(j).trim()
                            if (s.isNotEmpty()) expenseCats.add(s)
                        }
                    }
                }
            }

            UserConfig(
                userName = cfg.optString("userName").ifBlank { cfg.optString("name") }.take(80),
                darkMode = cfg.optBoolean("darkMode", cfg.optBoolean("dark", false)),
                currencySymbol = cfg.optString("currencySymbol").ifBlank { cfg.optString("currency") }.ifBlank { "৳" }.take(8),
                monthlyBudget = cfg.optDouble("monthlyBudget", cfg.optDouble("budget", 0.0)).coerceAtLeast(0.0),
                incomeCategories = incomeCats.distinct().ifEmpty { DEFAULT_INCOME_CATEGORIES }.take(40),
                expenseCategories = expenseCats.distinct().ifEmpty { DEFAULT_EXPENSE_CATEGORIES }.take(40)
            )
        }

        return ParsedBackup(
            version = version,
            transactions = transactions,
            debts = debts,
            config = parsedConfig
        )
    }

    private fun extractStringList(arr: JSONArray?, target: MutableList<String>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim().take(60)
            if (s.isNotEmpty()) target.add(s)
        }
    }

    private fun normalizeIsoDate(raw: String): String {
        val trimmed = raw.trim()
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(trimmed)) return trimmed
        if (trimmed.length >= 10 && Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(trimmed)) {
            return trimmed.substring(0, 10)
        }
        return todayIsoDate()
    }

    fun buildCsvExport(
        transactions: List<TransactionItem>,
        debts: List<DebtItem>,
        currencySymbol: String
    ): String {
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel renders Bangla characters cleanly
        sb.append('\uFEFF')
        sb.appendLine("তারিখ,ধরন,খাত,পরিমাণ ($currencySymbol),মাধ্যম,নোট")
        for (tx in transactions) {
            val typeLabel = if (tx.isIncome) "আয়" else "ব্যয়"
            sb.appendLine(
                listOf(
                    csvCell(tx.date),
                    csvCell(typeLabel),
                    csvCell(tx.category),
                    tx.amount.toString(),
                    csvCell(tx.paymentMethod),
                    csvCell(tx.note)
                ).joinToString(",")
            )
        }

        if (debts.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("দেনা-পাওনা হিসাব")
            sb.appendLine("ব্যক্তি,ধরন,মোট পরিমাণ ($currencySymbol),পরিশোধিত ($currencySymbol),বাকি ($currencySymbol),তারিখ,সম্ভাব্য তারিখ,অবস্থা,ফোন,নোট")
            for (d in debts) {
                val typeLabel = if (d.isReceivable) "পাওনা (পাবো)" else "দেনা (দিবো)"
                val status = if (d.settled) "পরিশোধিত" else "চলমান"
                sb.appendLine(
                    listOf(
                        csvCell(d.personName),
                        csvCell(typeLabel),
                        d.amount.toString(),
                        d.paidAmount.toString(),
                        d.remainingAmount.toString(),
                        csvCell(d.date),
                        csvCell(d.dueDate),
                        csvCell(status),
                        csvCell(d.phone),
                        csvCell(d.note)
                    ).joinToString(",")
                )
            }
        }
        return sb.toString()
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")
        return "\"$escaped\""
    }

    suspend fun compressReceiptImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return@withContext null

            val maxDim = 800
            var inSample = 1
            while (origW / inSample > maxDim * 2 || origH / inSample > maxDim * 2) {
                inSample *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            val scaled = scaleDownIfNeeded(decoded, maxDim)
            if (scaled !== decoded) decoded.recycle()

            var quality = 68
            var bytes: ByteArray
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                bytes = baos.toByteArray()
                quality -= 12
            } while (bytes.size > 95_000 && quality >= 28)

            scaled.recycle()
            if (bytes.size > 240_000) return@withContext null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    fun decodeReceiptBitmap(base64OrDataUrl: String?): ImageBitmap? {
        if (base64OrDataUrl.isNullOrBlank()) return null
        return try {
            val pureB64 = base64OrDataUrl.substringAfter("base64,", base64OrDataUrl)
            val bytes = Base64.decode(pureB64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
