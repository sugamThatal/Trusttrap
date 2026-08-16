package com.trusttap.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.trusttap.app.ui.TrustTapApp

/**
 * A deliberate, "trustworthy" color identity instead of Material's default
 * purple - a calm blue for the brand, plus a distinct green/amber/red trio
 * reserved specifically for risk badges elsewhere in the UI (kept separate
 * from this scheme so risk colors never accidentally collide with brand
 * chrome).
 */
private val TrustTapColorScheme = lightColorScheme(
    primary = Color(0xFF215D8F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E7F7),
    onPrimaryContainer = Color(0xFF0B2942),
    secondary = Color(0xFF2E7D5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F0E4),
    onSecondaryContainer = Color(0xFF0B3323),
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEBF0F5),
    error = Color(0xFFB3261E),
    outline = Color(0xFFB6C2CE),
)

class MainActivity : ComponentActivity() {
    private var pendingShare by mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingShare = extractIncomingShare(intent)

        setContent {
            MaterialTheme(colorScheme = TrustTapColorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val share = pendingShare
                    TrustTapApp(
                        initialMediaUri = share?.uri,
                        initialMediaMimeType = share?.mimeType,
                        initialCaption = share?.caption,
                        initialSharedText = share?.text
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = extractIncomingShare(intent)
    }

    /**
     * If this Activity was opened via Android's Share Sheet (someone tapped
     * "Share" on an image or video in another app and picked TrustTap),
     * pull the media Uri out of the incoming Intent. Returns null on a
     * normal launch.
     *
     * This works from ANY app that offers image/video sharing through the
     * system Share Sheet - Facebook, TikTok, Instagram, WhatsApp, Chrome,
     * the Gallery, etc. It's an OS-level mechanism, not app-specific, so
     * there's nothing extra to wire up per-app.
     */
    private fun extractIncomingShare(intent: Intent?): IncomingShare? {
        val action = intent?.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
        val type = intent.type ?: return null
        if (type == "text/plain" || type.startsWith("text/")) {
            return intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.trim()
                ?.ifBlank { null }
                ?.let { IncomingShare(uri = null, mimeType = type, caption = null, text = it) }
        }
        val uri = firstSharedUri(intent, action)
        if (uri == null) {
            // A few apps label a link as a non-text type but still provide
            // only EXTRA_TEXT. Send that into the text review instead of
            // opening an apparently empty Check screen.
            return intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.trim()
                ?.ifBlank { null }
                ?.let { IncomingShare(uri = null, mimeType = "text/plain", caption = null, text = it) }
        }
        return IncomingShare(uri, type, extractSharedCaption(intent), null)
    }

    private fun firstSharedUri(intent: Intent, action: String): Uri? {
        if (action != Intent.ACTION_SEND_MULTIPLE) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }

        intent.clipData?.getItemAt(0)?.uri?.let { return it }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
        }
    }

    /**
     * Pulls along any accompanying caption/claim text that rode in with the
     * shared media, if the sharing app included one. Returns null if there
     * wasn't one - the user can still type a caption manually.
     */
    private fun extractSharedCaption(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return null
        val type = intent.type ?: return null
        if (!type.startsWith("image/") && !type.startsWith("video/")) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.ifBlank { null }
    }
}

private data class IncomingShare(
    val uri: Uri?,
    val mimeType: String,
    val caption: String?,
    val text: String?
)
