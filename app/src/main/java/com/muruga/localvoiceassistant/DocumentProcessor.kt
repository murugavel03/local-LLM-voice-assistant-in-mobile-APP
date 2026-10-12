package com.muruga.localvoiceassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Extracts text from an image or PDF using on-device OCR (ML Kit).
 * PDF pages are rendered to bitmaps via Android's built-in PdfRenderer,
 * then each page is OCR'd the same way as a plain image -- one unified
 * pipeline for both file types, fully offline.
 */
class DocumentProcessor(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(uri: Uri, mimeType: String?): String {
        return if (mimeType == "application/pdf") {
            extractFromPdf(uri)
        } else {
            extractFromImageUri(uri)
        }
    }

    private suspend fun extractFromImageUri(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.text
    }

    private suspend fun extractFromPdf(uri: Uri): String {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Could not open PDF")

        val renderer = PdfRenderer(pfd)
        val builder = StringBuilder()

        try {
            for (pageIndex in 0 until renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(image).await()
                builder.append(result.text).append("\n\n")

                page.close()
                bitmap.recycle()
            }
        } finally {
            renderer.close()
            pfd.close()
        }

        return builder.toString().trim()
    }
}
