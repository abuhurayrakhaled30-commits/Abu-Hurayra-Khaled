package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HisabRepository
import com.example.data.ThemePreferences
import com.example.data.unwrapFirestoreError
import com.example.model.DebtItem
import com.example.model.ParsedBackup
import com.example.model.TransactionItem
import com.example.model.UserConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class HisabDashboardData(
    val config: UserConfig,
    val transactions: List<TransactionItem>,
    val debts: List<DebtItem>
)

class HisabViewModel(
    private val repository: HisabRepository,
    private val themePreferences: ThemePreferences,
    private val currentUserId: String
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    private var configInitialized = false

    val uiState: StateFlow<UiState<HisabDashboardData>> = combine(
        repository.observeConfig(currentUserId),
        repository.observeTransactions(currentUserId),
        repository.observeDebts(currentUserId)
    ) { cfg, txs, debts ->
        if (cfg == null && !configInitialized) {
            configInitialized = true
            val defaultName = Firebase.auth.currentUser?.displayName.orEmpty()
            viewModelScope.launch {
                repository.ensureDefaultConfig(currentUserId, defaultName)
            }
        }
        if (cfg != null) {
            viewModelScope.launch {
                themePreferences.setDarkMode(cfg.darkMode)
            }
        }
        val effectiveConfig = cfg ?: UserConfig(
            userId = currentUserId,
            userName = Firebase.auth.currentUser?.displayName.orEmpty()
        )
        UiState.Success(
            HisabDashboardData(
                config = effectiveConfig,
                transactions = txs,
                debts = debts
            )
        ) as UiState<HisabDashboardData>
    }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing dashboard data", root)
            emit(UiState.Error(root.message ?: "ডেটা লোড করতে সমস্যা হয়েছে"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun saveTransaction(existingId: String?, item: TransactionItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = if (existingId.isNullOrBlank()) {
                repository.addTransaction(currentUserId, item)
            } else {
                repository.updateTransaction(currentUserId, existingId, item)
            }
            _isBusy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(if (existingId.isNullOrBlank()) "লেনদেন সংরক্ষিত হয়েছে" else "লেনদেন হালনাগাদ হয়েছে")
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "সংরক্ষণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteTransaction(currentUserId, txId)
            _isBusy.value = false
            result.onSuccess {
                _toastEvents.tryEmit("লেনদেন মুছে ফেলা হয়েছে")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "মুছে ফেলা ব্যর্থ হয়েছে")
            }
        }
    }

    fun saveDebt(existingId: String?, item: DebtItem, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = if (existingId.isNullOrBlank()) {
                repository.addDebt(currentUserId, item)
            } else {
                repository.updateDebt(currentUserId, existingId, item)
            }
            _isBusy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(if (existingId.isNullOrBlank()) "দেনা-পাওনা যোগ হয়েছে" else "দেনা-পাওনা হালনাগাদ হয়েছে")
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "সংরক্ষণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun recordDebtPayment(debt: DebtItem, paymentAmount: Double, onDone: () -> Unit = {}) {
        if (paymentAmount <= 0.0) {
            _toastEvents.tryEmit("সঠিক পরিমাণ লিখুন")
            return
        }
        val newPaid = (debt.paidAmount + paymentAmount).coerceAtMost(debt.amount)
        val updated = debt.copy(
            paidAmount = newPaid,
            settled = newPaid >= debt.amount
        )
        saveDebt(debt.id, updated, onDone)
    }

    fun toggleDebtSettled(debt: DebtItem) {
        val nextSettled = !debt.settled
        val updated = debt.copy(
            paidAmount = if (nextSettled) debt.amount else 0.0,
            settled = nextSettled
        )
        saveDebt(debt.id, updated)
    }

    fun deleteDebt(debtId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteDebt(currentUserId, debtId)
            _isBusy.value = false
            result.onSuccess {
                _toastEvents.tryEmit("হিসাব মুছে ফেলা হয়েছে")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "মুছে ফেলা ব্যর্থ হয়েছে")
            }
        }
    }

    fun toggleDarkMode(currentConfig: UserConfig) {
        val nextDark = !currentConfig.darkMode
        viewModelScope.launch {
            themePreferences.setDarkMode(nextDark)
            repository.saveConfig(currentUserId, currentConfig.copy(darkMode = nextDark))
                .onFailure { err ->
                    _toastEvents.tryEmit(err.message ?: "থিম পরিবর্তন সংরক্ষিত হয়নি")
                }
        }
    }

    fun updateConfig(newConfig: UserConfig, successMsg: String = "সেটিংস সংরক্ষিত হয়েছে") {
        viewModelScope.launch {
            _isBusy.value = true
            themePreferences.setDarkMode(newConfig.darkMode)
            val result = repository.saveConfig(currentUserId, newConfig)
            _isBusy.value = false
            result.onSuccess {
                _toastEvents.tryEmit(successMsg)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "সেটিংস সংরক্ষণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun addCategory(currentConfig: UserConfig, isIncome: Boolean, name: String) {
        val clean = name.trim().take(60)
        if (clean.isEmpty()) {
            _toastEvents.tryEmit("খাতের নাম লিখুন")
            return
        }
        val list = if (isIncome) currentConfig.incomeCategories else currentConfig.expenseCategories
        if (list.any { it.equals(clean, ignoreCase = true) }) {
            _toastEvents.tryEmit("এই খাতটি আগে থেকেই আছে")
            return
        }
        if (list.size >= 40) {
            _toastEvents.tryEmit("সর্বোচ্চ ৪০টি খাত যোগ করা যাবে")
            return
        }
        val updated = if (isIncome) {
            currentConfig.copy(incomeCategories = list + clean)
        } else {
            currentConfig.copy(expenseCategories = list + clean)
        }
        updateConfig(updated, "নতুন খাত '$clean' যোগ হয়েছে")
    }

    fun removeCategory(currentConfig: UserConfig, isIncome: Boolean, name: String) {
        val list = if (isIncome) currentConfig.incomeCategories else currentConfig.expenseCategories
        if (list.size <= 1) {
            _toastEvents.tryEmit("কমপক্ষে ১টি খাত থাকতে হবে")
            return
        }
        val updated = if (isIncome) {
            currentConfig.copy(incomeCategories = list - name)
        } else {
            currentConfig.copy(expenseCategories = list - name)
        }
        updateConfig(updated, "খাত '$name' সরানো হয়েছে")
    }

    fun importBackup(
        backup: ParsedBackup,
        currentConfig: UserConfig?,
        replaceExisting: Boolean,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.importBackupData(currentUserId, backup, currentConfig, replaceExisting)
            _isBusy.value = false
            result.onSuccess { summary ->
                _toastEvents.tryEmit(
                    "ইমপোর্ট সফল: ${summary.importedTransactions}টি লেনদেন ও ${summary.importedDebts}টি দেনা-পাওনা যুক্ত হয়েছে"
                )
                onDone()
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "ব্যাকআপ ইমপোর্ট ব্যর্থ হয়েছে")
            }
        }
    }

    fun notifyMessage(msg: String) {
        _toastEvents.tryEmit(msg)
    }

    private companion object {
        const val TAG = "HisabVM"
    }
}
