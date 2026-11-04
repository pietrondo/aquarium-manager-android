package com.pietrondo.aquariummanager.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.SystemClock
import com.pietrondo.aquariummanager.domain.normalizeNfcTagId

/**
 * Reads the hardware UID of an NFC tag and keeps the reader mode active while the
 * app is in the foreground.
 *
 * The UID is used instead of the NDEF content on purpose: it is readable on every
 * NTAG21x tag without writing to it, so a sticker keeps identifying its aquarium
 * even if it was never formatted or is write protected.
 */
class NfcTagReader(private val activity: Activity) {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private var lastTagId: String? = null
    private var lastSeenAt = 0L

    /**
     * Starts polling. [onTag] receives the lowercase hexadecimal tag id.
     * Reader mode keeps firing while a tag stays on the phone, so the same tag is
     * reported only once per repeat window.
     */
    fun start(onTag: (String) -> Unit) {
        val nfcAdapter = adapter ?: return
        nfcAdapter.enableReaderMode(
            activity,
            { tag -> onTagProcessed(tag, onTag) },
            // NTAG21x stickers answer ISO/IEC 14443-3A (NFC-A), not NFC-V: the reader
            // would never see them with FLAG_READER_NFC_V alone.
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }

    private fun onTagProcessed(tag: Tag, onTag: (String) -> Unit) {
        val tagId = readTagId(tag) ?: return
        val now = SystemClock.elapsedRealtime()
        if (tagId == lastTagId && now - lastSeenAt < REPEAT_WINDOW_MS) return
        lastTagId = tagId
        lastSeenAt = now
        onTag(tagId)
    }

    companion object {
        private const val REPEAT_WINDOW_MS = 2_000L

        /** True when the device exposes an NFC adapter. */
        fun isSupported(context: Context): Boolean =
            NfcAdapter.getDefaultAdapter(context) != null

        /** True when NFC is switched on in the system settings. */
        fun isEnabled(context: Context): Boolean =
            NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

        /** Lowercase hexadecimal hardware UID of a tag, or null. */
        fun readTagId(tag: Tag): String? {
            // The UID is protocol independent: NTAG21x report it over NFC-A, other
            // stickers over NFC-V, so no technology filter is needed here.
            val uid = tag.id ?: return null
            if (uid.isEmpty()) return null
            return try {
                normalizeNfcTagId(uid.toHexString())
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** Finds the hosting activity of a Compose context. */
        fun activityFrom(context: Context): Activity? {
            var current = context
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }
    }
}
