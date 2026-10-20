package com.pietrondo.aquariummanager.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.pietrondo.aquariummanager.domain.BackupDocumentGateway
import com.pietrondo.aquariummanager.domain.BackupDocumentDeleteResult
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafBackupDocumentGateway(
    private val context: Context,
) : BackupDocumentGateway {
    internal var inputStreamOpener: (Uri) -> InputStream? = { context.contentResolver.openInputStream(it) }
    internal var outputStreamOpener: (Uri, String?) -> java.io.OutputStream? = { uri, mode ->
        if (mode == null) context.contentResolver.openOutputStream(uri)
        else context.contentResolver.openOutputStream(uri, mode)
    }

    internal constructor(
        context: Context,
        inputStreamOpener: (Uri) -> InputStream?,
        outputStreamOpener: (Uri, String?) -> java.io.OutputStream?,
    ) : this(context) {
        this.inputStreamOpener = inputStreamOpener
        this.outputStreamOpener = outputStreamOpener
    }

    override suspend fun writeDocument(destinationUri: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(destinationUri)
            val existingContent = inputStreamOpener(uri)
                ?: throw IOException("Cannot safely verify backup destination; choose a new file")
            val hasExistingContent = existingContent.use { it.read() != -1 }
            if (hasExistingContent) {
                throw IOException(
                    "This document provider cannot atomically replace an existing backup. Choose a new file.",
                )
            }

            val stream = outputStreamOpener(uri, "wt")
                ?: outputStreamOpener(uri, null)
                ?: throw IOException("Could not open output stream for backup destination")
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
        }

    @SuppressLint("Recycle")
    override suspend fun openDocumentInputStream(sourceUri: String): InputStream =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            inputStreamOpener(uri)
                ?: throw IOException("Could not open input stream for backup document")
        }

    override suspend fun deleteDocumentBestEffort(documentUri: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(documentUri)
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun deleteDocumentResult(documentUri: String): BackupDocumentDeleteResult =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(documentUri)
            try {
                if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                    BackupDocumentDeleteResult.DELETED
                } else {
                    val cursor = context.contentResolver.query(
                        uri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        null,
                        null,
                        null,
                    ) ?: return@withContext BackupDocumentDeleteResult.FAILED
                    cursor.use {
                        if (it.moveToFirst()) BackupDocumentDeleteResult.FAILED
                        else BackupDocumentDeleteResult.NOT_FOUND
                    }
                }
            } catch (_: FileNotFoundException) {
                BackupDocumentDeleteResult.NOT_FOUND
            } catch (_: Exception) {
                BackupDocumentDeleteResult.FAILED
            }
        }

    override suspend fun hasPersistedTreePermission(treeUri: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = Uri.parse(treeUri)
            context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == target && perm.isReadPermission && perm.isWritePermission
            }
        }

    override suspend fun createAutoBackupDocument(treeUri: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(treeUri)
            val treeDocId = DocumentsContract.getTreeDocumentId(tree)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/zip",
                fileName,
            ) ?: throw IOException("Could not create automatic backup file in selected folder")
            createdUri.toString()
        }

    @SuppressLint("Recycle")
    override suspend fun findAutoBackupDocument(treeUri: String, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(treeUri)
            val treeDocId = DocumentsContract.getTreeDocumentId(tree)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("Could not scan selected backup folder for recovery")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (it.moveToNext()) {
                    if (it.getString(nameColumn) == fileName) {
                        return@withContext DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(idColumn)).toString()
                    }
                }
            }
            null
        }
}
