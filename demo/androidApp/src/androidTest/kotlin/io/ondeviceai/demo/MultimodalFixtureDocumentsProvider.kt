package io.ondeviceai.demo

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.FileNotFoundException
import java.io.IOException

/** Test APK only: a public, fixed image exposed through the real DocumentsUI provider contract. */
class MultimodalFixtureDocumentsProvider : DocumentsProvider() {
    companion object {
        const val AUTHORITY = "io.ondeviceai.demo.test.multimodal-documents"
        private const val ROOT_ID = "fixture-root"
        private const val DOCUMENT_ID = "fixture-image"
        private val BYTES = byteArrayOf(1, 2, 3)
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: defaultRootProjection()).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT_ID)
                .add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
                .add(Root.COLUMN_TITLE, "OnDevice AI test fixtures")
                .add(Root.COLUMN_SUMMARY, "Public verification fixture")
                .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
                .add(Root.COLUMN_ICON, android.R.drawable.ic_menu_gallery)
                .add(Root.COLUMN_MIME_TYPES, "image/*")
                .add(Root.COLUMN_AVAILABLE_BYTES, BYTES.size)
        }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor = MatrixCursor(projection ?: defaultDocumentProjection()).apply {
        addDocumentRow(this, documentId)
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: defaultDocumentProjection()).apply {
        check(ROOT_ID.equals(parentDocumentId)) { "unknown parent $parentDocumentId" }
        addDocumentRow(this, DOCUMENT_ID)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        ROOT_ID.equals(parentDocumentId) && DOCUMENT_ID.equals(documentId)

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        check(DOCUMENT_ID.equals(documentId)) { "unknown document $documentId" }
        if (!"r".equals(mode)) throw FileNotFoundException("read only")
        return try {
            val pipe = ParcelFileDescriptor.createPipe()
            val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            try {
                output.write(BYTES)
            } finally {
                output.close()
            }
            val reader: ParcelFileDescriptor? = pipe[0]
            if (reader == null) throw FileNotFoundException("fixture pipe unavailable")
            reader
        } catch (error: IOException) {
            throw FileNotFoundException(error.message ?: "fixture unavailable")
        }
    }

    override fun getDocumentType(documentId: String): String =
        if (ROOT_ID.equals(documentId)) "vnd.android.document/directory" else "image/png"

    private fun addDocumentRow(cursor: MatrixCursor, documentId: String) {
        check(ROOT_ID.equals(documentId) || DOCUMENT_ID.equals(documentId)) { "unknown document $documentId" }
        val directory = ROOT_ID.equals(documentId)
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, if (directory) "Fixtures" else "real-system.png")
            .add(Document.COLUMN_SIZE, if (directory) null else BYTES.size)
            .add(Document.COLUMN_MIME_TYPE, if (directory) Document.MIME_TYPE_DIR else "image/png")
            .add(Document.COLUMN_FLAGS, 0)
            .add(Document.COLUMN_LAST_MODIFIED, 0L)
    }

    private fun defaultDocumentProjection(): Array<String> = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS,
        Document.COLUMN_LAST_MODIFIED,
    )

    private fun defaultRootProjection(): Array<String> = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON,
        Root.COLUMN_MIME_TYPES,
        Root.COLUMN_AVAILABLE_BYTES,
    )

}
