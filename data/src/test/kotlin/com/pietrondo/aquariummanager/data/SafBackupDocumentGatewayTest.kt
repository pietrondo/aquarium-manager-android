package com.pietrondo.aquariummanager.data

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SafBackupDocumentGatewayTest {
    @Test
    fun failedWriteToExistingDocumentPreservesOldBytesAndDoesNotOpenOutput() = runBlocking {
        val uriText = "content://example/backup.zip"
        val partialUriText = "content://example/new-backup.zip"
        val oldBackup = "previous verified backup".toByteArray()
        val files = mutableMapOf(uriText to oldBackup.copyOf(), partialUriText to byteArrayOf())
        var outputWasOpened = false
        val gateway = SafBackupDocumentGateway(
            context = RuntimeEnvironment.getApplication(),
            inputStreamOpener = { uri -> ByteArrayInputStream(files.getValue(uri.toString())) },
            outputStreamOpener = { uri, _ ->
                outputWasOpened = true
                files[uri.toString()] = byteArrayOf()
                object : OutputStream() {
                    override fun write(value: Int) {
                        files[uri.toString()] = files.getValue(uri.toString()) + value.toByte()
                        throw IOException("Injected provider failure after truncate")
                    }
                }
            },
        )

        try {
            gateway.writeDocument(uriText, "new backup bytes".toByteArray())
            fail("Expected existing destination to be rejected safely")
        } catch (_: IOException) {
            // Providers do not guarantee atomic replacement; do not touch an existing document.
        }
        assertEquals(false, outputWasOpened)
        assertEquals(oldBackup.toList(), files.getValue(uriText).toList())

        try {
            gateway.writeDocument(partialUriText, "new backup bytes".toByteArray())
            fail("Expected injected provider failure")
        } catch (_: IOException) {
            // A failed new-file write cannot affect the existing verified backup.
        }
        assertEquals(true, outputWasOpened)
        assertEquals(oldBackup.toList(), files.getValue(uriText).toList())
    }
}
