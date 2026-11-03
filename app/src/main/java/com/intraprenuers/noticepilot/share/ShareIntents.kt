package com.intraprenuers.noticepilot.share

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Reads what another app shared into NoticePilot (ACTION_SEND / ACTION_SEND_MULTIPLE). */
object ShareIntents {

    sealed interface Shared {
        data class Single(val uri: Uri, val mimeType: String?) : Shared
        data class Multiple(val uris: List<Uri>) : Shared
    }

    /** Null when the intent isn't a share, or carries no usable URI. Never throws. */
    fun parse(intent: Intent?): Shared? = try {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = streamExtra(intent) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                uri?.let { Shared.Single(it, intent.type) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val fromExtra = streamListExtra(intent).orEmpty()
                val uris = fromExtra.ifEmpty {
                    val clip = intent.clipData
                    if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                }
                if (uris.isEmpty()) null else Shared.Multiple(uris)
            }
            else -> null
        }
    } catch (e: Exception) {
        null // malformed extras from the sending app
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun streamListExtra(intent: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
}
