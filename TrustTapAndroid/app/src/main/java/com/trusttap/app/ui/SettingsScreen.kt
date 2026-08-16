package com.trusttap.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.trusttap.app.data.AppPreferences
import com.trusttap.app.data.HistoryCrypto
import com.trusttap.app.model.CapabilitiesResponse
import com.trusttap.app.network.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(baseUrl: String, onSaveBaseUrl: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var draftUrl by remember(baseUrl) { mutableStateOf(baseUrl) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var capabilityMessage by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Backend connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
            Text("The Android app sends media to your FastAPI server. Keep this address ending in / and do not leave spaces in it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                label = { Text("Backend URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val normalized = RetrofitClient.normalizeBaseUrl(draftUrl)
                        if (!isValidUrl(normalized)) {
                            statusMessage = "Enter a full http:// or https:// address with no spaces."
                        } else {
                            draftUrl = normalized
                            onSaveBaseUrl(normalized)
                            statusMessage = "Saved. The next check will use this address."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save address") }
                OutlinedButton(
                    onClick = {
                        draftUrl = AppPreferences.DEFAULT_BASE_URL
                        onSaveBaseUrl(AppPreferences.DEFAULT_BASE_URL)
                        statusMessage = "Reset to the real-phone address."
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
            }
            OutlinedButton(
                onClick = {
                    testing = true
                    statusMessage = null
                    scope.launch {
                        try {
                            val api = RetrofitClient.api(draftUrl)
                            api.healthCheck()
                            val capabilities: CapabilitiesResponse = api.capabilities()
                            statusMessage = "Backend is reachable."
                            capabilityMessage = "OCR: ${if (capabilities.ocr.available) "ready" else "not available"}. " +
                                "Trained text model: ${if (capabilities.text_model.available) "loaded" else "not loaded"}. " +
                                "TEE backend: ${if (capabilities.tee.available) "active" else "not active"}."
                        } catch (error: Exception) {
                            statusMessage = "Backend test failed: ${error.message ?: "check the URL, Wi-Fi, and firewall"}"
                        } finally {
                            testing = false
                        }
                    }
                },
                enabled = !testing && isValidUrl(draftUrl),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testing) CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Text("  Test connection")
            }
            statusMessage?.let { message ->
                Text(message, color = if (message.startsWith("Backend is")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            capabilityMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Which address should I use?", fontWeight = FontWeight.Bold)
                    Text("Android Emulator: ${AppPreferences.EMULATOR_BASE_URL}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text("Real phone: ${AppPreferences.DEFAULT_BASE_URL}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("For a real phone, the phone and computer must be on the same Wi-Fi and Uvicorn must be started with --host 0.0.0.0.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Project status", fontWeight = FontWeight.Bold)
                    Text("• Text review: available through Share or paste; it is safety triage, not fact-checking.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text("• Model training: not run in this ZIP because no real labeled dataset was supplied. See training/README.md.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("• TEE: not active; a real attested confidential backend is required. See TEE-PLAN.md.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("• SMS permission: none. Share or paste the message instead, so TrustTap cannot silently read your inbox.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("• Local privacy: ${HistoryCrypto.status(context)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun isValidUrl(value: String): Boolean =
    value.isNotBlank() && !value.contains(" ") && (value.startsWith("http://") || value.startsWith("https://"))
