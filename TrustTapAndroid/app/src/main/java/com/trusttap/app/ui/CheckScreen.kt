package com.trusttap.app.ui

import android.content.Intent
import android.speech.RecognizerIntent
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.trusttap.app.data.HistoryRepository
import com.trusttap.app.model.AnalysisResponse
import com.trusttap.app.model.Evidence
import com.trusttap.app.network.RetrofitClient
import com.trusttap.app.network.TextAnalysisRequest
import com.trusttap.app.network.TrustTapApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

private enum class InputMode { MEDIA, TEXT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckScreen(
    initialMediaUri: Uri?,
    initialMediaMimeType: String?,
    initialCaption: String?,
    initialSharedText: String?,
    baseUrl: String,
    historyRepository: HistoryRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val speak = rememberTrustTapSpeaker()

    var inputMode by remember { mutableStateOf(if (initialSharedText.isNullOrBlank()) InputMode.MEDIA else InputMode.TEXT) }
    var selectedMediaUri by remember { mutableStateOf(initialMediaUri) }
    var selectedMediaMimeType by remember { mutableStateOf(initialMediaMimeType) }
    var claimedCaption by remember { mutableStateOf(initialCaption.orEmpty()) }
    var textToCheck by remember { mutableStateOf(initialSharedText.orEmpty()) }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AnalysisResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var videoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var followUpQuestion by remember { mutableStateOf("") }
    var followUpAnswer by remember { mutableStateOf<String?>(null) }
    val voiceQuestionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        activityResult.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { followUpQuestion = it }
    }

    val isVideo = selectedMediaMimeType?.startsWith("video/") == true ||
        selectedMediaUri?.let { isVideoUri(context.contentResolver, it) } == true

    LaunchedEffect(selectedMediaUri, selectedMediaMimeType, isVideo) {
        videoThumbnail = null
        val uri = selectedMediaUri
        if (uri != null && isVideo) videoThumbnail = loadVideoThumbnail(context, uri)
    }

    suspend fun runMediaAnalysis(uri: Uri, caption: String?, uploadingVideo: Boolean) {
        isLoading = true
        errorMessage = null
        result = null
        try {
            val response = withContext(Dispatchers.IO) {
                val api = RetrofitClient.api(baseUrl)
                if (uploadingVideo) {
                    analyzeVideoFile(uri, caption, context.cacheDir, context.contentResolver, api)
                } else {
                    analyzeImageFile(uri, caption, context.cacheDir, context.contentResolver, api)
                }
            }
            result = response
            try {
                historyRepository.save(uri, response, caption)
            } catch (_: Exception) {
                // A temporary Share URI can expire after upload. The verdict
                // remains useful even if its thumbnail cannot be saved.
            }
            speak(buildSpokenSummary(response))
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            val message = "TrustTap could not reach the backend. Check the server, Wi-Fi, firewall, and Settings address.$detail"
            errorMessage = message
            speak(message)
        } finally {
            isLoading = false
        }
    }

    suspend fun runTextAnalysis(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            errorMessage = "Enter or paste some text first."
            speak(errorMessage!!)
            return
        }
        isLoading = true
        errorMessage = null
        result = null
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.api(baseUrl).analyzeText(TextAnalysisRequest(cleanText))
            }
            result = response
            try {
                historyRepository.saveText(response, cleanText)
            } catch (_: Exception) {
                // The result can still be used if local history is unavailable.
            }
            speak(buildSpokenSummary(response))
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            val message = "TrustTap could not check that text. Check the backend address and connection.$detail"
            errorMessage = message
            speak(message)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(initialMediaUri, initialSharedText) {
        when {
            !initialSharedText.isNullOrBlank() -> {
                inputMode = InputMode.TEXT
                textToCheck = initialSharedText
                runTextAnalysis(initialSharedText)
            }
            initialMediaUri != null -> {
                inputMode = InputMode.MEDIA
                selectedMediaUri = initialMediaUri
                runMediaAnalysis(initialMediaUri, initialCaption, initialMediaMimeType?.startsWith("video/") == true)
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            inputMode = InputMode.MEDIA
            selectedMediaUri = uri
            selectedMediaMimeType = context.contentResolver.getType(uri)
            result = null
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("TrustTap", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text("What do you want to check?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Choose a photo/video or paste a message. TrustTap reads the result aloud and keeps a copy in History.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 7.dp, bottom = 16.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (inputMode == InputMode.MEDIA) {
                    Button(onClick = { inputMode = InputMode.MEDIA }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Photo or video")
                    }
                } else {
                    OutlinedButton(onClick = { inputMode = InputMode.MEDIA }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Photo or video")
                    }
                }
                if (inputMode == InputMode.TEXT) {
                    Button(onClick = { inputMode = InputMode.TEXT }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Plain text")
                    }
                } else {
                    OutlinedButton(onClick = { inputMode = InputMode.TEXT }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Plain text")
                    }
                }
            }

            if (inputMode == InputMode.MEDIA) {
                MediaInputCard(
                    selectedMediaUri = selectedMediaUri,
                    isVideo = isVideo,
                    videoThumbnail = videoThumbnail,
                    onChoose = {
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onClear = {
                        selectedMediaUri = null
                        selectedMediaMimeType = null
                        result = null
                        errorMessage = null
                    }
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = claimedCaption,
                    onValueChange = { claimedCaption = it },
                    label = { Text("What is this claimed to show? (optional)") },
                    placeholder = { Text("Example: flooding in the city today") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        selectedMediaUri?.let { uri ->
                            scope.launch { runMediaAnalysis(uri, claimedCaption, isVideo) }
                        }
                    },
                    enabled = selectedMediaUri != null && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(if (isLoading) "Checking…" else "Check photo or video", fontWeight = FontWeight.Bold) }
            } else {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Paste or type the message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "This is a safety review, not proof that a message is true or false. Never share passwords, one-time codes, or private information here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                        )
                        OutlinedTextField(
                            value = textToCheck,
                            onValueChange = { textToCheck = it },
                            label = { Text("Text to check") },
                            placeholder = { Text("Paste a forwarded message, SMS text, or claim") },
                            minLines = 7,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { scope.launch { runTextAnalysis(textToCheck) } },
                            enabled = textToCheck.isNotBlank() && !isLoading,
                            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)
                        ) { Text(if (isLoading) "Checking…" else "Check plain text", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (inputMode == InputMode.MEDIA) {
                Text(
                    "Share tip: open an image in another app, tap Share, tap More if needed, then choose TrustTap. Some apps only share a link and cannot provide the image file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Text(
                    "You can also use Android Share on a text message and choose TrustTap. No SMS permission is requested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Checking, please wait"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("This may take a little while.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }

            errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            result?.let { response ->
                TrustTapResultCard(response, onReplay = { speak(buildSpokenSummary(response)) }, onOpenEvidence = uriHandler::openUri)
                FollowUpPanel(
                    question = followUpQuestion,
                    answer = followUpAnswer,
                    onQuestionChange = { followUpQuestion = it },
                    onAsk = {
                        followUpAnswer = buildFollowUpAnswer(followUpQuestion, response)
                        followUpAnswer?.let(speak)
                    },
                    onReplay = { followUpAnswer?.let(speak) },
                    onVoiceQuestion = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask TrustTap about this result")
                        }
                        voiceQuestionLauncher.launch(intent)
                    }
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MediaInputCard(
    selectedMediaUri: Uri?,
    isVideo: Boolean,
    videoThumbnail: Bitmap?,
    onChoose: () -> Unit,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            if (selectedMediaUri != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        videoThumbnail?.let { bitmap ->
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Selected video preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .55f), modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.padding(13.dp))
                        }
                    } else {
                        Image(painter = rememberAsyncImagePainter(selectedMediaUri), contentDescription = "Selected image preview", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Media ready to check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Choose a photo or video", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("No media selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Choose one here or use Share from another app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp)) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selectedMediaUri == null) "Choose photo or video" else "Choose a different one")
            }
            if (selectedMediaUri != null) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Clear selection") }
            }
        }
    }
}

fun buildSpokenSummary(result: AnalysisResponse): String {
    val reasons = if (result.reason.isEmpty()) "" else " Reasons: ${result.reason.joinToString(". ")}."
    val evidence = result.evidence?.size ?: 0
    val sources = if (evidence == 0) "" else " Found $evidence related source${if (evidence == 1) "" else "s"}; see the evidence section for details."
    val readable = result.extracted_text?.takeIf { it.isNotBlank() }?.let { " Readable text: ${it.replace("\n", " ").take(260)}." }.orEmpty()
    val next = result.next_actions?.firstOrNull()?.let { " Next step: $it" }.orEmpty()
    return "${result.risk} risk. Trust score ${result.trust_score} out of 100. ${result.accessible_description}.${reasons}${readable}${next}${sources}"
}

@Composable
private fun FollowUpPanel(
    question: String,
    answer: String?,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onReplay: () -> Unit,
    onVoiceQuestion: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ask about this result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Try: What should I do next? Why was this flagged? Read the text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                label = { Text("Your question") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(onClick = onVoiceQuestion, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Speak question")
                }
                Button(
                    onClick = onAsk,
                    enabled = question.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Ask TrustTap", fontWeight = FontWeight.Bold) }
            }
            answer?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Read answer aloud")
                }
            }
        }
    }
}

fun buildFollowUpAnswer(question: String, result: AnalysisResponse): String {
    val lower = question.trim().lowercase()
    return when {
        lower.contains("read") || lower.contains("text") ->
            result.extracted_text?.takeIf { it.isNotBlank() } ?: result.accessible_description
        lower.contains("why") || lower.contains("flag") ->
            if (result.reason.isEmpty()) "No specific warning reason was returned." else "TrustTap flagged this because: ${result.reason.joinToString(". ")}."
        lower.contains("link") ->
            result.url_findings?.joinToString(" ") { finding -> "${finding.host ?: finding.url}: ${finding.signals?.joinToString(". ") ?: "check this link carefully"}." }
                ?.takeIf { it.isNotBlank() } ?: "No link warning details were found."
        lower.contains("do") || lower.contains("next") || lower.contains("safe") || lower.contains("act") ->
            result.next_actions?.joinToString(" ") ?: "Pause and verify important claims through a trusted source."
        else -> "I can explain the warning, read extracted text, explain a link, or tell you what to do next."
    }
}

@Composable
fun TrustTapResultCard(result: AnalysisResponse, onReplay: () -> Unit, onOpenEvidence: (String) -> Unit) {
    val riskColor: Color
    val riskIcon: androidx.compose.ui.graphics.vector.ImageVector
    when (result.risk.lowercase()) {
        "high" -> { riskColor = Color(0xFFB3261E); riskIcon = Icons.Filled.Error }
        "medium" -> { riskColor = Color(0xFF9A6700); riskIcon = Icons.Filled.Warning }
        else -> { riskColor = Color(0xFF237A50); riskIcon = Icons.Filled.CheckCircle }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = .10f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(riskIcon, contentDescription = null, tint = riskColor, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${result.risk} risk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = riskColor)
                    Text("Trust score ${result.trust_score.coerceIn(0, 100)} / 100", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { result.trust_score.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)), color = riskColor, trackColor = riskColor.copy(alpha = .16f))
            Spacer(Modifier.height(16.dp))
            Text(result.accessible_description, style = MaterialTheme.typography.bodyLarge)
            result.analysis_method?.let { method ->
                Text("Checked with: $method", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            result.frames_analyzed?.let { count -> Text("Based on $count sampled video frames", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            if (result.reason.isNotEmpty()) {
                Text("Why this score", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                result.reason.forEach { reason -> Text("• $reason", modifier = Modifier.padding(top = 3.dp)) }
            }
            result.extracted_text?.takeIf { it.isNotBlank() }?.let { extracted ->
                Text("Readable text", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                Text(extracted, style = MaterialTheme.typography.bodyMedium, maxLines = 8)
            }
            result.next_actions?.takeIf { it.isNotEmpty() }?.let { actions ->
                Text("Safer next steps", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                actions.forEach { action -> Text("• $action", modifier = Modifier.padding(top = 3.dp)) }
            }
            result.url_findings?.takeIf { it.isNotEmpty() }?.let { findings ->
                Text("Link checks", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                findings.forEach { finding ->
                    Text("${finding.risk ?: "Unknown"} risk: ${finding.host ?: finding.url}", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
                    finding.signals?.forEach { signal -> Text("• $signal", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp)) }
                }
            }
            result.limitations?.takeIf { it.isNotEmpty() }?.let { limitations ->
                Text("Limits", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                limitations.forEach { limitation -> Text("• $limitation", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (!result.evidence.isNullOrEmpty()) {
                Text("Related sources", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                result.evidence.forEach { evidence -> EvidenceRow(evidence, onOpenEvidence) }
            }
            OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Replay result")
            }
        }
    }
}

@Composable
private fun EvidenceRow(evidence: Evidence, onOpen: (String) -> Unit) {
    val label = when (evidence.type) {
        "fact_check" -> "${evidence.rating ?: "Unrated"} — ${evidence.publisher ?: "Fact-checker"}"
        "reverse_image" -> evidence.page_title?.takeIf { it.isNotBlank() } ?: "Found elsewhere online"
        else -> evidence.claim ?: evidence.page_title ?: "Source"
    }
    val url = evidence.url?.takeIf { it.isNotBlank() }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (url != null) {
            IconButton(onClick = { onOpen(url) }) { Icon(Icons.Filled.OpenInNew, contentDescription = "Open source in browser", modifier = Modifier.size(17.dp)) }
        }
    }
}

private fun isVideoUri(contentResolver: ContentResolver, uri: Uri): Boolean = contentResolver.getType(uri)?.startsWith("video/") == true

private suspend fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.frameAtTime
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun copyUriToTempFile(uri: Uri, cacheDir: File, contentResolver: ContentResolver, suffix: String): File {
    val tempFile = File.createTempFile("upload", suffix, cacheDir)
    val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("The shared file could not be opened")
    input.use { source -> FileOutputStream(tempFile).use { output -> source.copyTo(output) } }
    return tempFile
}

private suspend fun analyzeImageFile(uri: Uri, caption: String?, cacheDir: File, contentResolver: ContentResolver, api: TrustTapApi): AnalysisResponse {
    val tempFile = copyUriToTempFile(uri, cacheDir, contentResolver, ".jpg")
    return try {
        val part = MultipartBody.Part.createFormData("image", tempFile.name, tempFile.asRequestBody("image/*".toMediaTypeOrNull()))
        api.analyzeImage(part, caption?.trim()?.ifBlank { null }?.toRequestBody("text/plain".toMediaTypeOrNull()))
    } finally {
        tempFile.delete()
    }
}

private suspend fun analyzeVideoFile(uri: Uri, caption: String?, cacheDir: File, contentResolver: ContentResolver, api: TrustTapApi): AnalysisResponse {
    val tempFile = copyUriToTempFile(uri, cacheDir, contentResolver, ".mp4")
    return try {
        val part = MultipartBody.Part.createFormData("video", tempFile.name, tempFile.asRequestBody("video/*".toMediaTypeOrNull()))
        api.analyzeVideo(part, caption?.trim()?.ifBlank { null }?.toRequestBody("text/plain".toMediaTypeOrNull()))
    } finally {
        tempFile.delete()
    }
}
