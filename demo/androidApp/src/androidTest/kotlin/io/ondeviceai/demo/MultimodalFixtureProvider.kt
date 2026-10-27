package io.ondeviceai.demo

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException

/** Test APK only: fixed public bytes, independent of the target APK fixture data. */
class MultimodalFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        @Suppress("UNCHECKED_CAST")
        val columns = projection as? Array<String>
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val path = uri.lastPathSegment ?: ""
        val name = if (containsSlow(path)) {
            "public-slow.png"
        } else {
            "public-synthetic.png"
        }
        val cursor = MatrixCursor(columns)
        cursor.addRow(Array<Any?>(columns.size) { index ->
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) name else 3
        })
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (!mode.equals("r")) throw FileNotFoundException("read only")
        try {
            if (containsSlow(uri.lastPathSegment ?: "")) SystemClock.sleep(1800)
            val pipe = ParcelFileDescriptor.createPipe()
            val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            try {
                output.write(byteArrayOf(1, 2, 3))
            } finally {
                output.close()
            }
            return pipe[0]
        } catch (_: IOException) {
            throw FileNotFoundException("fixture unavailable")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("read only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun containsSlow(value: String): Boolean {
        var index = 0
        while (index + 3 < value.length) {
            if (value[index] == 's' && value[index + 1] == 'l' &&
                value[index + 2] == 'o' && value[index + 3] == 'w') return true
            index++
        }
        return false
    }
}
