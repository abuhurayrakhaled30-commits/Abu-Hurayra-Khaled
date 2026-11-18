package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.HisabRepository
import com.example.data.ThemePreferences
import com.example.model.DebtItem
import com.example.model.ParsedBackup
import com.example.model.TransactionItem
import com.example.model.todayIsoDate
import com.example.ui.HisabDashboardData
import com.example.ui.HisabViewModel
import com.example.ui.UiState
import com.example.ui.components.AddEditDebtDialog
import com.example.ui.components.AddEditTransactionDialog
import com.example.ui.components.BackupImportPreviewDialog
import com.example.ui.components.DebtPaymentDialog
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.AmarHisabTheme
import com.example.util.BackupAndExportUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HisabTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DASHBOARD("ড্যাশবোর্ড", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    TRANSACTIONS("লেনদেন", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_transactions"),
    DEBTS("দেনা-পাওনা", Icons.Filled.Handshake, Icons.Outlined.Handshake, "nav_debts"),
    REPORTS("রিপোর্ট", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_reports"),
    SETTINGS("সেটিংস", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePrefs = ThemePreferences(applicationContext)
        setContent {
            val cachedDarkMode by themePrefs.darkModeFlow.collectAsStateWithLifecycle(initialValue = false)
            AppNavigation(cachedDarkMode = cachedDarkMode, themePrefs = themePrefs)
        }
    }
}

@Composable
fun AppNavigation(
    cachedDarkMode: Boolean,
    themePrefs: ThemePreferences,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AmarHisabTheme(darkTheme = cachedDarkMode) {
            AuthScreen(onAuthSuccess = { })
        }
    } else {
        key(user.uid) {
            val viewModel: HisabViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        HisabViewModel(
                            repository = HisabRepository(db),
                            themePreferences = ThemePreferences(app),
                            currentUserId = user.uid
                        )
                    }
                }
            )
            AuthenticatedHisabApp(
                user = user,
                cachedDarkMode = cachedDarkMode,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun AuthenticatedHisabApp(
    user: FirebaseUser,
    cachedDarkMode: Boolean,
    viewModel: HisabViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val darkMode = (uiState as? UiState.Success)?.data?.config?.darkMode ?: cachedDarkMode

    var currentTab by remember { mutableStateOf(HisabTab.DASHBOARD) }
    if (currentTab != HisabTab.DASHBOARD) {
        BackHandler { currentTab = HisabTab.DASHBOARD }
    }

    // Dialog states
    var showTxDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<TransactionItem?>(null) }
    var initialTxType by remember { mutableStateOf("expense") }

    var showDebtDialog by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtItem?>(null) }
    var paymentTargetDebt by remember { mutableStateOf<DebtItem?>(null) }
    var previewReceiptBase64 by remember { mutableStateOf<String?>(null) }
    var pendingBackupImport by remember { mutableStateOf<ParsedBackup?>(null) }

    // SAF Launchers (Zero broad storage permissions)
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }
                    if (text.isNullOrBlank()) {
                        viewModel.notifyMessage("ফাইলটি খালি বা পড়া যায়নি")
                    } else {
                        val parsed = withContext(Dispatchers.Default) {
                            BackupAndExportUtils.parseBackupJson(text)
                        }
                        if (parsed.transactions.isEmpty() && parsed.debts.isEmpty() && parsed.config == null) {
                            viewModel.notifyMessage("ফাইলে কোনো বৈধ হিসাব পাওয়া যায়নি")
                        } else {
                            pendingBackupImport = parsed
                        }
                    }
                } catch (e: Exception) {
                    viewModel.notifyMessage("সঠিক আমার হিসাব JSON ব্যাকআপ ফাইল নির্বাচন করুন")
                }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val data = (uiState as? UiState.Success)?.data ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.Default) {
                        BackupAndExportUtils.buildBackupJson(data.transactions, data.debts, data.config)
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }
                    viewModel.notifyMessage("JSON ব্যাকআপ সংরক্ষিত হয়েছে")
                } catch (_: Exception) {
                    viewModel.notifyMessage("ব্যাকআপ সংরক্ষণ ব্যর্থ হয়েছে")
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val data = (uiState as? UiState.Success)?.data ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                try {
                    val csv = withContext(Dispatchers.Default) {
                        BackupAndExportUtils.buildCsvExport(
                            data.transactions,
                            data.debts,
                            data.config.currencySymbol
                        )
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    }
                    viewModel.notifyMessage("Excel (CSV) ফাইল সংরক্ষিত হয়েছে")
                } catch (_: Exception) {
                    viewModel.notifyMessage("CSV ডাউনলোড ব্যর্থ হয়েছে")
                }
            }
        }
    }

    AmarHisabTheme(darkTheme = darkMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 680.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.navigationBarsPadding()
                    )
                },
                floatingActionButton = {
                    if (currentTab == HisabTab.DASHBOARD || currentTab == HisabTab.TRANSACTIONS || currentTab == HisabTab.DEBTS) {
                        FloatingActionButton(
                            onClick = {
                                if (currentTab == HisabTab.DEBTS) {
                                    editingDebt = null
                                    showDebtDialog = true
                                } else {
                                    editingTx = null
                                    initialTxType = "expense"
                                    showTxDialog = true
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("main_fab")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "নতুন যোগ করুন")
                        }
                    }
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            HisabTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HisabTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 880.dp)
                                .fillMaxSize()
                        ) {
                            when (val state = uiState) {
                                is UiState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("আপনার হিসাব লোড হচ্ছে...")
                                        }
                                    }
                                }

                                is UiState.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "তথ্য লোড করা যায়নি",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = state.message,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = {
                                                signOut(credentialManager, scope)
                                            }) {
                                                Text("সাইন আউট করে পুনরায় চেষ্টা করুন")
                                            }
                                        }
                                    }
                                }

                                is UiState.Success -> {
                                    val data: HisabDashboardData = state.data
                                    when (currentTab) {
                                        HisabTab.DASHBOARD -> DashboardScreen(
                                            data = data,
                                            userPhotoUrl = user.photoUrl?.toString(),
                                            onToggleDarkMode = { viewModel.toggleDarkMode(data.config) },
                                            onAddIncome = {
                                                editingTx = null
                                                initialTxType = "income"
                                                showTxDialog = true
                                            },
                                            onAddExpense = {
                                                editingTx = null
                                                initialTxType = "expense"
                                                showTxDialog = true
                                            },
                                            onAddDebt = {
                                                editingDebt = null
                                                showDebtDialog = true
                                            },
                                            onImportBackup = {
                                                importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                                            },
                                            onSeeAllTransactions = { currentTab = HisabTab.TRANSACTIONS },
                                            onEditTransaction = { tx ->
                                                editingTx = tx
                                                showTxDialog = true
                                            },
                                            onViewReceipt = { b64 -> previewReceiptBase64 = b64 }
                                        )

                                        HisabTab.TRANSACTIONS -> TransactionsScreen(
                                            data = data,
                                            onEditTransaction = { tx ->
                                                editingTx = tx
                                                showTxDialog = true
                                            },
                                            onDeleteTransaction = { txId -> viewModel.deleteTransaction(txId) },
                                            onViewReceipt = { b64 -> previewReceiptBase64 = b64 }
                                        )

                                        HisabTab.DEBTS -> DebtsScreen(
                                            data = data,
                                            onEditDebt = { debt ->
                                                editingDebt = debt
                                                showDebtDialog = true
                                            },
                                            onRecordPayment = { debt -> paymentTargetDebt = debt },
                                            onToggleSettled = { debt -> viewModel.toggleDebtSettled(debt) },
                                            onDeleteDebt = { debtId -> viewModel.deleteDebt(debtId) }
                                        )

                                        HisabTab.REPORTS -> ReportsScreen(data = data)

                                        HisabTab.SETTINGS -> SettingsScreen(
                                            data = data,
                                            userEmail = user.email,
                                            userPhotoUrl = user.photoUrl?.toString(),
                                            onToggleDarkMode = { viewModel.toggleDarkMode(data.config) },
                                            onSaveConfig = { cfg -> viewModel.updateConfig(cfg) },
                                            onAddCategory = { isInc, name -> viewModel.addCategory(data.config, isInc, name) },
                                            onRemoveCategory = { isInc, name -> viewModel.removeCategory(data.config, isInc, name) },
                                            onImportBackupJson = {
                                                importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                                            },
                                            onExportBackupJson = {
                                                exportJsonLauncher.launch("amar_hisab_backup_${todayIsoDate()}.json")
                                            },
                                            onExportCsvExcel = {
                                                exportCsvLauncher.launch("amar_hisab_${todayIsoDate()}.csv")
                                            },
                                            onSignOut = {
                                                signOut(credentialManager, scope)
                                            }
                                        )
                                    }

                                    // Dialogs
                                    if (showTxDialog) {
                                        AddEditTransactionDialog(
                                            existing = editingTx,
                                            initialType = initialTxType,
                                            config = data.config,
                                            isBusy = isBusy,
                                            onDismiss = { showTxDialog = false },
                                            onSave = { item ->
                                                viewModel.saveTransaction(editingTx?.id, item) {
                                                    showTxDialog = false
                                                }
                                            }
                                        )
                                    }

                                    if (showDebtDialog) {
                                        AddEditDebtDialog(
                                            existing = editingDebt,
                                            currencySymbol = data.config.currencySymbol,
                                            isBusy = isBusy,
                                            onDismiss = { showDebtDialog = false },
                                            onSave = { item ->
                                                viewModel.saveDebt(editingDebt?.id, item) {
                                                    showDebtDialog = false
                                                }
                                            }
                                        )
                                    }

                                    if (paymentTargetDebt != null) {
                                        DebtPaymentDialog(
                                            debt = paymentTargetDebt!!,
                                            currencySymbol = data.config.currencySymbol,
                                            onDismiss = { paymentTargetDebt = null },
                                            onConfirmPayment = { amt ->
                                                viewModel.recordDebtPayment(paymentTargetDebt!!, amt) {
                                                    paymentTargetDebt = null
                                                }
                                            }
                                        )
                                    }

                                    if (previewReceiptBase64 != null) {
                                        ReceiptPreviewDialog(
                                            receiptBase64 = previewReceiptBase64!!,
                                            onDismiss = { previewReceiptBase64 = null }
                                        )
                                    }

                                    if (pendingBackupImport != null) {
                                        BackupImportPreviewDialog(
                                            backup = pendingBackupImport!!,
                                            isBusy = isBusy,
                                            onDismiss = { pendingBackupImport = null },
                                            onConfirmImport = { replaceExisting ->
                                                viewModel.importBackup(
                                                    backup = pendingBackupImport!!,
                                                    currentConfig = data.config,
                                                    replaceExisting = replaceExisting
                                                ) {
                                                    pendingBackupImport = null
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
