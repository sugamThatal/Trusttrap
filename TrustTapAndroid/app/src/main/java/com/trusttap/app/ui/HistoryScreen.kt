package com.trusttap.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trusttap.app.data.HistoryEntity
import com.trusttap.app.data.HistoryRepository
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(entries: List<HistoryEntity>, onOpen: (Long) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        if (entries.isEmpty()) {
            EmptyHistory(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Your checks stay on this device", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(entry, onClick = { onOpen(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
            Text("No checks yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
            Text("Completed photo and video checks will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.thumbnailPath != null) {
                AsyncImage(
                    model = File(entry.thumbnailPath),
                    contentDescription = "Thumbnail from ${entry.mediaType.lowercase()} check",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp).clip(MaterialTheme.shapes.medium)
                )
            } else {
                Icon(
                    imageVector = when (entry.mediaType) {
                        "Video" -> Icons.Filled.VideoFile
                        "Text" -> Icons.Filled.Edit
                        else -> Icons.Filled.Image
                    },
                    contentDescription = entry.mediaType,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(76.dp).padding(20.dp)
                )
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(entry.mediaType, style = MaterialTheme.typography.titleMedium)
                Text(formatDate(entry.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                entry.claimedCaption?.let { caption ->
                    Text(caption, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(entry: HistoryEntity?, repository: HistoryRepository, onBack: () -> Unit) {
    val speak = rememberTrustTapSpeaker()
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved result") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (entry == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("This history item is no longer available.") }
        } else {
            val response = remember(entry.responseJson) { repository.decodeResponse(entry) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                item {
                    Text("${entry.mediaType} check · ${formatDate(entry.createdAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    entry.claimedCaption?.let { caption -> Text("Claim checked: $caption", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                    if (entry.thumbnailPath != null) {
                        AsyncImage(model = File(entry.thumbnailPath), contentDescription = "Saved ${entry.mediaType.lowercase()} thumbnail", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(MaterialTheme.shapes.large))
                    }
                    TrustTapResultCard(response, onReplay = { speak(buildSpokenSummary(response)) }, onOpenEvidence = uriHandler::openUri)
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
