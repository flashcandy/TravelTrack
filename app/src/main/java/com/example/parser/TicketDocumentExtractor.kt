package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TicketDocumentExtractor(
    private val context: Context,
    private val router: TicketParserRouter = TicketParserRouter()
) {
    init {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    suspend fun extractAndParse(uri: Uri, mimeTypeHint: String? = null): ParsedTicketDraft =
        withContext(Dispatchers.IO) {
            val resolvedMime = mimeTypeHint
                ?: context.contentResolver.getType(uri)
                ?: inferMimeFromUri(uri)

            val extractedText = if (resolvedMime.contains("pdf", ignoreCase = true)) {
                extractTextFromPdf(uri)
            } else {
                extractTextFromImage(uri)
            }

            router.parse(extractedText)
        }

    private suspend fun extractTextFromPdf(uri: Uri): String {
        // Step 1: Extract text layer using PdfBox-Android
        val pdfBoxText = runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    stripper.getText(document).trim()
                }
            }
        }.getOrNull().orEmpty()

        if (pdfBoxText.length >= 20) {
            return pdfBoxText
        }

        // Step 2: Fallback if PDF has no text layer — render each page to Bitmap with PdfRenderer and run ML Kit OCR
        return extractPdfViaOcr(uri)
    }

    private suspend fun extractPdfViaOcr(uri: Uri): String {
        val tempPdf = File(context.cacheDir, "scan_ticket_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdf).use { output ->
                    input.copyTo(output)
                }
            } ?: return ""

            val pageTexts = mutableListOf<String>()
            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount.coerceAtMost(5)
                    for (pageIndex in 0 until pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val scale = 2
                            val width = (page.width * scale).coerceAtLeast(1)
                            val height = (page.height * scale).coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )
                            val ocrText = runMlKitOcrOnBitmap(bitmap)
                            bitmap.recycle()
                            if (ocrText.isNotBlank()) {
                                pageTexts.add(ocrText)
                            }
                        }
                    }
                }
            }
            return pageTexts.joinToString("\n\n")
        } catch (e: Exception) {
            return ""
        } finally {
            runCatching { tempPdf.delete() }
        }
    }

    private suspend fun extractTextFromImage(uri: Uri): String {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return ""
            try {
                runMlKitOcrOnBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun runMlKitOcrOnBitmap(bitmap: Bitmap): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()
            visionText.text.trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun inferMimeFromUri(uri: Uri): String {
        val path = uri.toString().lowercase()
        return if (path.endsWith(".pdf")) "application/pdf" else "image/*"
    }
}
