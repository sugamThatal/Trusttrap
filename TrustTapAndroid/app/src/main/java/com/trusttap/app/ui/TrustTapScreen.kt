package com.trusttap.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.trusttap.app.model.AnalysisResponse
import com.trusttap.app.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

/**
 * @param initialImageUri set when the user Shared an image into TrustTap
 *        from another app (WhatsApp, a news app, etc). Null on a normal
 *        cold launch from the home screen.
 */
@Composable
fun TrustTapScreen(initialImageUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf(initialImageUri) }
    var claimedCaption by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AnalysisResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            result = null
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TrustTap",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Check whether an image is what it claims to be",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // --- Image preview / picker ---
        if (selectedImageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(selectedImageUri),
                contentDescription = "Selected image to analyze",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedImageUri == null) "Pick an image to check" else "Pick a different image")
        }

        Text(
            text = "Or share an image into TrustTap from any other app",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        // --- Optional claimed caption ---
        OutlinedTextField(
            value = claimedCaption,
            onValueChange = { claimedCaption = it },
            label = { Text("What is this image being claimed to show? (optional)") },
            placeholder = { Text("e.g. \"Flooding in the city today\"") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val uri = selectedImageUri ?: return@Button
                isLoading = true
                errorMessage = null
                result = null

                scope.launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            analyzeImage(
                                uri = uri,
                                claimedCaption = claimedCaption.trim().ifBlank { null },
                                cacheDir = context.cacheDir,
                                contentResolver = context.contentResolver
                            )
                        }
                        result = response
                    } catch (e: Exception) {
                        errorMessage = "Couldn't reach TrustTap's server. Is it running, " +
                            "and is the app pointed at the right address? (${e.message})"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = selectedImageUri != null && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze")
        }

        Spacer(Modifier.height(20.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Text(
                text = "Analyzing - this can take a little while on the first request",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        result?.let { ResultCard(it) }
    }
}

@Composable
private fun ResultCard(result: AnalysisResponse) {
    val riskColor = when (result.risk) {
        "High" -> Color(0xFFB3261E)
        "Medium" -> Color(0xFFB8860B)
        else -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${result.risk} risk  ·  trust score ${result.trust_score}/100",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = riskColor
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = result.accessible_description,
                style = MaterialTheme.typography.bodyLarge
            )

            if (result.reason.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(text = "Why:", fontWeight = FontWeight.Bold)
                result.reason.forEach { reason ->
                    Text(text = "•  $reason", modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/**
 * Reads the picked image into a temp file and uploads it as multipart form
 * data matching the backend's exact contract: an "image" file part plus an
 * optional "claimed_caption" text part.
 */
private suspend fun analyzeImage(
    uri: Uri,
    claimedCaption: String?,
    cacheDir: File,
    contentResolver: android.content.ContentResolver
): AnalysisResponse {
    val tempFile = File.createTempFile("upload", ".jpg", cacheDir)
    contentResolver.openInputStream(uri).use { input ->
        FileOutputStream(tempFile).use { output ->
            input?.copyTo(output)
        }
    }

    val imageRequestBody = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
    val imagePart = MultipartBody.Part.createFormData("image", tempFile.name, imageRequestBody)

    val captionBody = claimedCaption?.toRequestBody("text/plain".toMediaTypeOrNull())

    return RetrofitClient.api.analyzeImage(imagePart, captionBody)
}
