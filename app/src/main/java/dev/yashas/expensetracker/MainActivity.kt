package dev.yashas.expensetracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.yashas.expensetracker.data.repo.DemoData
import dev.yashas.expensetracker.data.repo.UserPrefs
import dev.yashas.expensetracker.ui.ExpenseApp
import dev.yashas.expensetracker.ui.theme.ExpenseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestSms =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onboardingSmsGranted = granted
            if (granted) finishOnboarding()
        }

    private var onboardingSmsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val txnRepo = AppGraph.txnRepository(this)
        val analyticsRepo = AppGraph.analyticsRepository(this)
        val budgetRepo = AppGraph.budgetRepository(this)
        val prefs = UserPrefs(this)
        lifecycleScope.launch(Dispatchers.IO) {
            AppGraph.bootstrap(this@MainActivity)
            DemoData.seedIfNeeded(AppGraph.database(this@MainActivity))
        }

        val onboardingDonePref = getSharedPreferences("app_flags", MODE_PRIVATE)
            .getBoolean("onboarding_done", false)
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        setContent {
            ExpenseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!onboardingDonePref) {
                        OnboardingGate(
                            prefs = prefs,
                            smsAlreadyGranted = hasSms,
                            onGrantSms = { requestSms.launch(Manifest.permission.RECEIVE_SMS) },
                            onSkip = { finishOnboarding() },
                        )
                    } else {
                        ExpenseApp(txnRepo = txnRepo, analyticsRepo = analyticsRepo, budgetRepo = budgetRepo)
                    }
                }
            }
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences("app_flags", MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).apply()
        recreate() // flips the composition into the real app
    }
}

/**
 * P6 onboarding (00-MASTER §10 v1): what the app is → your name → SMS permission.
 * Skipping is allowed (manual quick-add works without SMS); the gate never returns
 * after the first run.
 */
@Composable
private fun OnboardingGate(
    prefs: UserPrefs,
    smsAlreadyGranted: Boolean,
    onGrantSms: () -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            0 -> {
                Text("Expense Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Reads your bank SMS on-device and turns them into a private, offline ledger. " +
                        "No accounts, no cloud, no internet permission — your money data never leaves this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
            }
            1 -> {
                Text("What should we call you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch { prefs.setName(name.ifBlank { UserPrefs.DEFAULT_NAME }) }
                        step = 2
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue") }
            }
            else -> {
                Text("Auto-capture bank SMS?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "With the SMS permission, transactions are detected and categorised automatically. " +
                        "You can skip and add everything manually — you can always allow it later from system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                if (smsAlreadyGranted) {
                    Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("All set — open the app") }
                } else {
                    Button(onClick = onGrantSms, modifier = Modifier.fillMaxWidth()) { Text("Allow SMS access") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
                }
            }
        }
    }
}
