package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.UserConfig
import com.example.ui.HisabDashboardData
import com.example.ui.theme.ExpenseRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    data: HisabDashboardData,
    userEmail: String?,
    userPhotoUrl: String?,
    onToggleDarkMode: () -> Unit,
    onSaveConfig: (UserConfig) -> Unit,
    onAddCategory: (isIncome: Boolean, name: String) -> Unit,
    onRemoveCategory: (isIncome: Boolean, name: String) -> Unit,
    onImportBackupJson: () -> Unit,
    onExportBackupJson: () -> Unit,
    onExportCsvExcel: () -> Unit,
    onSignOut: () -> Unit
) {
    val cfg = data.config
    var budgetText by remember(cfg.monthlyBudget) {
        mutableStateOf(
            if (cfg.monthlyBudget <= 0.0) ""
            else if (cfg.monthlyBudget % 1.0 == 0.0) cfg.monthlyBudget.toLong().toString()
            else cfg.monthlyBudget.toString()
        )
    }
    var userNameText by remember(cfg.userName) { mutableStateOf(cfg.userName) }
    var categoryIsIncome by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "সেটিংস, খাত ও ব্যাকআপ",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Google Account Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!userPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userPhotoUrl,
                            contentDescription = "Google প্রোফাইল ছবি",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cfg.userName.ifBlank { "Google অ্যাকাউন্ট" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সাইন আউট")
                    }
                }
            }
        }

        // Appearance & Budget Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("সাধারণ সেটিংস", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ডার্ক মোড (Dark Mode)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "চোখের আরামের জন্য গাঢ় থিম চালু করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cfg.darkMode,
                            onCheckedChange = { onToggleDarkMode() },
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }

                    OutlinedTextField(
                        value = userNameText,
                        onValueChange = { if (it.length <= 80) userNameText = it },
                        label = { Text("আপনার নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it },
                        label = { Text("মাসিক ব্যয়ের বাজেট (${cfg.currencySymbol}) (ঐচ্ছিক)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val budget = budgetText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            onSaveConfig(cfg.copy(userName = userNameText.trim(), monthlyBudget = budget))
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("সেটিংস সংরক্ষণ")
                    }
                }
            }
        }

        // Custom Categories Management ("খাত তৈরি")
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("খাত তৈরি ও পরিচালনা", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !categoryIsIncome,
                            onClick = { categoryIsIncome = false },
                            label = { Text("ব্যয়ের খাত") }
                        )
                        FilterChip(
                            selected = categoryIsIncome,
                            onClick = { categoryIsIncome = true },
                            label = { Text("আয়ের খাত") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { if (it.length <= 60) newCategoryName = it },
                            placeholder = { Text("নতুন খাতের নাম লিখুন...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_category_input")
                        )
                        Button(
                            onClick = {
                                onAddCategory(categoryIsIncome, newCategoryName)
                                newCategoryName = ""
                            },
                            modifier = Modifier.testTag("add_category_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "যোগ করুন")
                        }
                    }

                    val cats = if (categoryIsIncome) cfg.incomeCategories else cfg.expenseCategories
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cats.forEach { cat ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveCategory(categoryIsIncome, cat) },
                                label = { Text(cat) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "$cat সরান",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("category_chip_$cat")
                            )
                        }
                    }
                    Text(
                        text = "কোনো খাত সরাতে চাইলে খাতটিতে ট্যাপ করুন",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Backup & Excel Export Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("ব্যাকআপ, রিস্টোর ও Excel এক্সপোর্ট", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "পুরোনো {v:2, tx, debts, cfg} JSON ফাইল ইমপোর্ট করুন অথবা ক্লাউডের সব হিসাব ব্যাকআপ ও Excel (CSV) হিসেবে ডাউনলোড করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onImportBackupJson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_import_json_button")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ব্যাকআপ ফাইল ইমপোর্ট করুন (JSON)")
                    }

                    OutlinedButton(
                        onClick = onExportBackupJson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_export_json_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ব্যাকআপ ডাউনলোড করুন (JSON)")
                    }

                    OutlinedButton(
                        onClick = onExportCsvExcel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_export_csv_button")
                    ) {
                        Icon(Icons.Filled.TableView, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Excel (CSV) ডাউনলোড করুন")
                    }
                }
            }
        }
    }
}
