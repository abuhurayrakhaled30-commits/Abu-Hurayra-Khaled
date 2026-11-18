package com.example.data

import android.content.Context
import com.example.R
import com.example.model.DEFAULT_EXPENSE_CATEGORIES
import com.example.model.DEFAULT_INCOME_CATEGORIES
import com.example.model.DebtItem
import com.example.model.ImportSummary
import com.example.model.ParsedBackup
import com.example.model.TransactionItem
import com.example.model.UserConfig
import com.example.model.todayIsoDate
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HisabRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireActiveUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("ব্যবহারকারী সাইন ইন করা নেই।")
        check(uid == expectedUserId && uid.isNotBlank()) {
            "সেশন পরিবর্তিত হয়েছে; অনুগ্রহ করে পুনরায় চেষ্টা করুন।"
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun configRef(uid: String) = userDoc(uid).collection("settings").document("config")
    private fun txCollection(uid: String) = userDoc(uid).collection("transactions")
    private fun debtsCollection(uid: String) = userDoc(uid).collection("debts")

    fun observeConfig(userId: String): Flow<UserConfig?> =
        configRef(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "users/$userId/settings/config")
                }
                throw error
            }

    fun observeTransactions(userId: String): Flow<List<TransactionItem>> =
        txCollection(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TransactionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users/$userId/transactions")
                }
                throw error
            }

    fun observeDebts(userId: String): Flow<List<DebtItem>> =
        debtsCollection(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DebtItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users/$userId/debts")
                }
                throw error
            }

    suspend fun ensureDefaultConfig(userId: String, displayName: String): Result<Unit> {
        val path = "users/$userId/settings/config"
        return try {
            val uid = requireActiveUserId(userId)
            val snap = configRef(uid).get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "userName" to displayName.take(80),
                    "darkMode" to false,
                    "currencySymbol" to "৳",
                    "monthlyBudget" to 0.0,
                    "incomeCategories" to DEFAULT_INCOME_CATEGORIES,
                    "expenseCategories" to DEFAULT_EXPENSE_CATEGORIES,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                configRef(uid).set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveConfig(userId: String, config: UserConfig): Result<Unit> {
        val path = "users/$userId/settings/config"
        return try {
            val uid = requireActiveUserId(userId)
            val sanitizedIncome = config.incomeCategories.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().take(40)
            val sanitizedExpense = config.expenseCategories.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().take(40)
            val snap = configRef(uid).get().await()
            if (snap.exists()) {
                configRef(uid).update(
                    mapOf(
                        "userName" to config.userName.trim().take(80),
                        "darkMode" to config.darkMode,
                        "currencySymbol" to config.currencySymbol.ifBlank { "৳" }.take(8),
                        "monthlyBudget" to config.monthlyBudget.coerceIn(0.0, 1_000_000_000.0),
                        "incomeCategories" to sanitizedIncome,
                        "expenseCategories" to sanitizedExpense,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                configRef(uid).set(
                    mapOf(
                        "userId" to uid,
                        "userName" to config.userName.trim().take(80),
                        "darkMode" to config.darkMode,
                        "currencySymbol" to config.currencySymbol.ifBlank { "৳" }.take(8),
                        "monthlyBudget" to config.monthlyBudget.coerceIn(0.0, 1_000_000_000.0),
                        "incomeCategories" to sanitizedIncome,
                        "expenseCategories" to sanitizedExpense,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addTransaction(userId: String, item: TransactionItem): Result<Unit> {
        var docPath = "users/$userId/transactions"
        return try {
            val uid = requireActiveUserId(userId)
            require(item.amount > 0.0 && item.amount <= 1_000_000_000.0) { "সঠিক পরিমাণ দিন" }
            val cleanCategory = item.category.trim().take(60)
            require(cleanCategory.isNotEmpty()) { "খাত নির্বাচন করুন" }
            val docRef = txCollection(uid).document()
            docPath = docRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "txType" to if (item.txType == "income") "income" else "expense",
                "amount" to item.amount,
                "category" to cleanCategory,
                "note" to item.note.trim().take(300),
                "date" to normalizeDate(item.date),
                "paymentMethod" to item.paymentMethod.trim().ifBlank { "নগদ" }.take(40),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val receipt = item.receiptBase64?.takeIf { it.isNotBlank() && it.length <= 350_000 }
            if (receipt != null) {
                payload["receiptBase64"] = receipt
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(userId: String, txId: String, item: TransactionItem): Result<Unit> {
        val docPath = "users/$userId/transactions/$txId"
        return try {
            val uid = requireActiveUserId(userId)
            require(txId.isNotBlank()) { "লেনদেন আইডি সঠিক নয়" }
            require(item.amount > 0.0 && item.amount <= 1_000_000_000.0) { "সঠিক পরিমাণ দিন" }
            val cleanCategory = item.category.trim().take(60)
            require(cleanCategory.isNotEmpty()) { "খাত নির্বাচন করুন" }

            val updates = mutableMapOf<String, Any>(
                "txType" to if (item.txType == "income") "income" else "expense",
                "amount" to item.amount,
                "category" to cleanCategory,
                "note" to item.note.trim().take(300),
                "date" to normalizeDate(item.date),
                "paymentMethod" to item.paymentMethod.trim().ifBlank { "নগদ" }.take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val receipt = item.receiptBase64?.takeIf { it.isNotBlank() && it.length <= 350_000 }
            if (receipt != null) {
                updates["receiptBase64"] = receipt
            } else {
                updates["receiptBase64"] = FieldValue.delete()
            }
            txCollection(uid).document(txId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, txId: String): Result<Unit> {
        val docPath = "users/$userId/transactions/$txId"
        return try {
            val uid = requireActiveUserId(userId)
            require(txId.isNotBlank())
            txCollection(uid).document(txId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addDebt(userId: String, item: DebtItem): Result<Unit> {
        var docPath = "users/$userId/debts"
        return try {
            val uid = requireActiveUserId(userId)
            val person = item.personName.trim().take(80)
            require(person.isNotEmpty()) { "ব্যক্তির নাম লিখুন" }
            require(item.amount > 0.0 && item.amount <= 1_000_000_000.0) { "সঠিক পরিমাণ দিন" }
            val paid = item.paidAmount.coerceIn(0.0, item.amount)
            val docRef = debtsCollection(uid).document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "personName" to person,
                "phone" to item.phone.trim().take(30),
                "debtType" to if (item.debtType == "took") "took" else "gave",
                "amount" to item.amount,
                "paidAmount" to paid,
                "note" to item.note.trim().take(300),
                "date" to normalizeDate(item.date),
                "dueDate" to item.dueDate.trim().take(20),
                "settled" to (item.settled || paid >= item.amount),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDebt(userId: String, debtId: String, item: DebtItem): Result<Unit> {
        val docPath = "users/$userId/debts/$debtId"
        return try {
            val uid = requireActiveUserId(userId)
            require(debtId.isNotBlank())
            val person = item.personName.trim().take(80)
            require(person.isNotEmpty()) { "ব্যক্তির নাম লিখুন" }
            require(item.amount > 0.0 && item.amount <= 1_000_000_000.0) { "সঠিক পরিমাণ দিন" }
            val paid = item.paidAmount.coerceIn(0.0, item.amount)

            debtsCollection(uid).document(debtId).update(
                mapOf(
                    "personName" to person,
                    "phone" to item.phone.trim().take(30),
                    "debtType" to if (item.debtType == "took") "took" else "gave",
                    "amount" to item.amount,
                    "paidAmount" to paid,
                    "note" to item.note.trim().take(300),
                    "date" to normalizeDate(item.date),
                    "dueDate" to item.dueDate.trim().take(20),
                    "settled" to (item.settled || paid >= item.amount),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDebt(userId: String, debtId: String): Result<Unit> {
        val docPath = "users/$userId/debts/$debtId"
        return try {
            val uid = requireActiveUserId(userId)
            require(debtId.isNotBlank())
            debtsCollection(uid).document(debtId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun importBackupData(
        userId: String,
        backup: ParsedBackup,
        currentConfig: UserConfig?,
        replaceExisting: Boolean
    ): Result<ImportSummary> {
        val basePath = "users/$userId"
        return try {
            val uid = requireActiveUserId(userId)

            if (replaceExisting) {
                val existingTx = txCollection(uid).get().await()
                val existingDebts = debtsCollection(uid).get().await()
                val deleteRefs = existingTx.documents.map { it.reference } + existingDebts.documents.map { it.reference }
                for (chunk in deleteRefs.chunked(400)) {
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it) }
                    batch.commit().await()
                }
            }

            var importedTx = 0
            val validTransactions = backup.transactions.filter { it.amount > 0.0 && it.amount <= 1_000_000_000.0 }
            for (chunk in validTransactions.chunked(400)) {
                val batch = db.batch()
                for (tx in chunk) {
                    val safeId = sanitizeId(tx.id)
                    val docRef = if (safeId != null && !replaceExisting) {
                        txCollection(uid).document(safeId)
                    } else if (safeId != null && replaceExisting) {
                        txCollection(uid).document(safeId)
                    } else {
                        txCollection(uid).document()
                    }
                    val payload = mutableMapOf<String, Any>(
                        "userId" to uid,
                        "txType" to if (tx.txType == "income") "income" else "expense",
                        "amount" to tx.amount,
                        "category" to tx.category.trim().ifBlank { "অন্যান্য" }.take(60),
                        "note" to tx.note.trim().take(300),
                        "date" to normalizeDate(tx.date),
                        "paymentMethod" to tx.paymentMethod.trim().ifBlank { "নগদ" }.take(40),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    val receipt = tx.receiptBase64?.takeIf { it.isNotBlank() && it.length <= 350_000 }
                    if (receipt != null) {
                        payload["receiptBase64"] = receipt
                    }
                    batch.set(docRef, payload)
                    importedTx++
                }
                batch.commit().await()
            }

            var importedDebts = 0
            val validDebts = backup.debts.filter { it.personName.isNotBlank() && it.amount > 0.0 && it.amount <= 1_000_000_000.0 }
            for (chunk in validDebts.chunked(400)) {
                val batch = db.batch()
                for (debt in chunk) {
                    val safeId = sanitizeId(debt.id)
                    val docRef = if (safeId != null) {
                        debtsCollection(uid).document(safeId)
                    } else {
                        debtsCollection(uid).document()
                    }
                    val paid = debt.paidAmount.coerceIn(0.0, debt.amount)
                    val payload = mapOf(
                        "userId" to uid,
                        "personName" to debt.personName.trim().take(80),
                        "phone" to debt.phone.trim().take(30),
                        "debtType" to if (debt.debtType == "took") "took" else "gave",
                        "amount" to debt.amount,
                        "paidAmount" to paid,
                        "note" to debt.note.trim().take(300),
                        "date" to normalizeDate(debt.date),
                        "dueDate" to debt.dueDate.trim().take(20),
                        "settled" to (debt.settled || paid >= debt.amount),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                    importedDebts++
                }
                batch.commit().await()
            }

            var configUpdated = false
            val importedCfg = backup.config
            if (importedCfg != null) {
                val base = currentConfig ?: UserConfig(userId = uid)
                val mergedIncome = if (replaceExisting) {
                    importedCfg.incomeCategories.ifEmpty { DEFAULT_INCOME_CATEGORIES }
                } else {
                    (base.incomeCategories + importedCfg.incomeCategories).distinct().take(40)
                }
                val mergedExpense = if (replaceExisting) {
                    importedCfg.expenseCategories.ifEmpty { DEFAULT_EXPENSE_CATEGORIES }
                } else {
                    (base.expenseCategories + importedCfg.expenseCategories).distinct().take(40)
                }
                val mergedConfig = base.copy(
                    userName = importedCfg.userName.ifBlank { base.userName },
                    darkMode = if (replaceExisting) importedCfg.darkMode else base.darkMode,
                    currencySymbol = importedCfg.currencySymbol.ifBlank { base.currencySymbol },
                    monthlyBudget = if (importedCfg.monthlyBudget > 0) importedCfg.monthlyBudget else base.monthlyBudget,
                    incomeCategories = mergedIncome,
                    expenseCategories = mergedExpense
                )
                saveConfig(uid, mergedConfig).getOrThrow()
                configUpdated = true
            }

            Result.success(
                ImportSummary(
                    importedTransactions = importedTx,
                    importedDebts = importedDebts,
                    configUpdated = configUpdated
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    private fun sanitizeId(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        return cleaned.takeIf { it.isNotEmpty() && it.length <= 128 }
    }

    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length in 10..20) trimmed else todayIsoDate()
    }
}
