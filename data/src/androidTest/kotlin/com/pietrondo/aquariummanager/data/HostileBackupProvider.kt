package com.pietrondo.aquariummanager.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Test double for a SAF document provider that misbehaves on demand: it can disappear,
 * lose its permission grant, run out of space or store only part of an archive.
 *
 * The real [android.content.ContentResolver] talks to this provider, so the production
 * [SafBackupDocumentGateway] runs unmodified against it on a device.
 */
class HostileBackupProvider : ContentProvider() {
    enum class Behavior {
        NORMAL,
        UNAVAILABLE,
        REVOKED,
        OUT_OF_SPACE,
        TRUNCATED_WRITE,
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val docId = uri.lastPathSegment ?: return null
        val isWrite = mode != "r"
        when (behaviors[docId] ?: Behavior.NORMAL) {
            Behavior.REVOKED ->
                throw SecurityException("Permission Denial: access to $uri has been revoked")

            Behavior.UNAVAILABLE -> return null

            Behavior.OUT_OF_SPACE ->
                if (isWrite) throw IOException("No space left on device")

            Behavior.TRUNCATED_WRITE ->
                if (isWrite) return truncatingDescriptor(docId)

            Behavior.NORMAL -> Unit
        }

        val file = documentFile(docId)
        if (isWrite) {
            file.parentFile?.mkdirs()
            return ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE,
            )
        }
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val docId = uri.lastPathSegment ?: return 0
        val file = documentFile(docId)
        return if (file.exists() && file.delete()) 1 else 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "deleteDocument") return null
        val docId = extras?.getString("documentId") ?: return null
        documentFile(docId).delete()
        return Bundle().apply { putParcelable("result", uriFor(docId)) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "application/zip"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun documentFile(docId: String): File =
        File(requireNotNull(context).cacheDir, "hostile-backups").apply { mkdirs() }
            .let { File(it, "$docId.zip") }

    /** Accepts the write but stores only the first bytes, like a provider that loses data. */
    private fun truncatingDescriptor(docId: String): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        Thread {
            val buffer = ByteArray(8 * 1024)
            var stored = 0
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        if (stored < TRUNCATED_BYTES) {
                            val keep = minOf(TRUNCATED_BYTES - stored, read)
                            documentFile(docId).outputStream().use { output ->
                                output.write(buffer, 0, keep)
                            }
                            stored += keep
                        }
                    }
                }
            } catch (_: IOException) {
                // The writer failed: the stored prefix stays as the incomplete document.
            }
            storedBytes[docId] = stored
        }.start()
        return writeEnd
    }

    companion object {
        const val AUTHORITY = "com.pietrondo.aquariummanager.data.test.backup"
        private const val TRUNCATED_BYTES = 64

        val behaviors = ConcurrentHashMap<String, Behavior>()
        val storedBytes = ConcurrentHashMap<String, Int>()

        fun uriFor(docId: String): Uri = Uri.parse("content://$AUTHORITY/document/$docId")

        fun reset() {
            behaviors.clear()
            storedBytes.clear()
        }
    }
}
