package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR language support.
 * Note: ML Kit's default recognizer supports Latin-based languages.
 * For other languages, different recognizers would be needed.
 */
enum class OcrLanguage(val displayName: String) {
    LATIN("Latin-based (English, Spanish, French, German, etc.)"),
    // Future: Add support for other scripts with ML Kit
}

/**
 * OCR result for a single page.
 */
data class OcrPageResult(
    val pageNumber: Int, // 1-indexed
    val text: String,
    val blocks: List<OcrTextBlock>,
    val confidence: Float
)

/**
 * Text block detected by OCR.
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val lines: List<OcrTextLine>
)

/**
 * Text line detected by OCR.
 */
data class OcrTextLine(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val words: List<OcrWord>
)

/**
 * Word detected by OCR.
 */
data class OcrWord(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val confidence: Float
)

/**
 * Bounding box for OCR elements.
 */
data class OcrBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Full OCR result for a PDF.
 */
data class OcrResult(
    val success: Boolean,
    val pages: List<OcrPageResult>,
    val fullText: String,
    val errorMessage: String? = null
)

/**
 * Result of making a PDF searchable.
 */
data class SearchablePdfResult(
    val success: Boolean,
    val pagesProcessed: Int,
    val errorMessage: String? = null
)

/**
 * OCR Processor - Performs Optical Character Recognition on PDF pages.
 * Uses Google ML Kit Text Recognition (Apache 2.0 compatible).
 * Can extract text and make scanned PDFs searchable.
 */
class PdfOcrProcessor(private val context: Context) {
    
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    
    /**
     * Extract text from a PDF using OCR.
     * Useful for scanned PDFs that don't have embedded text.
     *
     * @param pdfUri PDF file URI
     * @param pageRange Pages to process (null for all pages)
     * @param progressCallback Progress callback (0-100)
     * @return OcrResult with extracted text
     */
    suspend fun extractTextWithOcr(
        pdfUri: Uri,
        pageRange: IntRange? = null,
        progressCallback: (Int) -> Unit = {}
    ): OcrResult = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            progressCallback(0)
            
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: return@withContext OcrResult(
                    success = false,
                    pages = emptyList(),
                    fullText = "",
                    errorMessage = "Cannot open PDF file"
                )
            
            document = PDDocument.load(inputStream)
            inputStream.close()
            
            val totalPages = document.numberOfPages
            val pagesToProcess = pageRange ?: (0 until totalPages)
            val validPages = pagesToProcess.filter { it in 0 until totalPages }
            
            progressCallback(10)
            
            val renderer = PDFRenderer(document)
            val pageResults = mutableListOf<OcrPageResult>()
            val fullTextBuilder = StringBuilder()
            
            for ((index, pageIndex) in validPages.withIndex()) {
                // Render page to image
                val dpi = 200f // Good balance of quality and speed
                val pageImage = renderer.renderImageWithDPI(pageIndex, dpi)
                
                // Perform OCR on the image
                val ocrResult = performOcrOnBitmap(pageImage)
                pageImage.recycle()
                
                if (ocrResult != null) {
                    val pageResult = OcrPageResult(
                        pageNumber = pageIndex + 1,
                        text = ocrResult.text,
                        blocks = convertTextBlocks(ocrResult),
                        confidence = calculateConfidence(ocrResult)
                    )
                    pageResults.add(pageResult)
                    
                    if (fullTextBuilder.isNotEmpty()) {
                        fullTextBuilder.append("\n\n--- Page ${pageIndex + 1} ---\n\n")
                    }
                    fullTextBuilder.append(ocrResult.text)
                }
                
                val progress = 10 + ((index + 1) * 85 / validPages.size)
                progressCallback(progress)
            }
            
            document.close()
            progressCallback(100)
            
            OcrResult(
                success = true,
                pages = pageResults,
                fullText = fullTextBuilder.toString()
            )
            
        } catch (e: IOException) {
            document?.close()
            OcrResult(
                success = false,
                pages = emptyList(),
                fullText = "",
                errorMessage = "IO Error: ${e.message}"
            )
        } catch (e: Exception) {
            document?.close()
            OcrResult(
                success = false,
                pages = emptyList(),
                fullText = "",
                errorMessage = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Make a scanned PDF searchable by adding a hidden text layer.
     * The visual appearance remains the same, but text becomes searchable/selectable.
     *
     * @param inputUri Source PDF file URI
     * @param outputUri Destination PDF file URI
     * @param progressCallback Progress callback (0-100)
     * @return SearchablePdfResult with operation status
     */
    suspend fun makeSearchable(
        inputUri: Uri,
        outputUri: Uri,
        progressCallback: (Int) -> Unit = {}
    ): SearchablePdfResult = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            progressCallback(0)
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext SearchablePdfResult(
                    success = false,
                    pagesProcessed = 0,
                    errorMessage = "Cannot open source PDF"
                )
            
            document = PDDocument.load(inputStream)
            inputStream.close()
            
            val totalPages = document.numberOfPages
            progressCallback(10)
            
            val renderer = PDFRenderer(document)
            
            for (pageIndex in 0 until totalPages) {
                val page = document.getPage(pageIndex)
                
                // Render page to image for OCR
                val dpi = 200f
                val pageImage = renderer.renderImageWithDPI(pageIndex, dpi)
                
                // Perform OCR
                val ocrResult = performOcrOnBitmap(pageImage)
                
                if (ocrResult != null) {
                    // Add invisible text layer to the page
                    addTextLayerToPage(document, page, ocrResult, pageImage.width, pageImage.height, dpi)
                }
                
                pageImage.recycle()
                
                val progress = 10 + ((pageIndex + 1) * 80 / totalPages)
                progressCallback(progress)
            }
            
            progressCallback(90)
            
            // Save the document
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                document.save(outputStream)
            }
            
            document.close()
            progressCallback(100)
            
            SearchablePdfResult(
                success = true,
                pagesProcessed = totalPages
            )
            
        } catch (e: IOException) {
            document?.close()
            SearchablePdfResult(
                success = false,
                pagesProcessed = 0,
                errorMessage = "IO Error: ${e.message}"
            )
        } catch (e: Exception) {
            document?.close()
            SearchablePdfResult(
                success = false,
                pagesProcessed = 0,
                errorMessage = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Extract text from an image using OCR.
     */
    suspend fun extractTextFromImage(
        imageUri: Uri
    ): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext ""
            
            val result = performOcrOnBitmap(bitmap)
            bitmap.recycle()
            
            result?.text ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Perform OCR on a bitmap using ML Kit.
     */
    private suspend fun performOcrOnBitmap(bitmap: Bitmap): Text? {
        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    continuation.resume(text)
                }
                .addOnFailureListener { e ->
                    continuation.resume(null)
                }
        }
    }
    
    /**
     * Convert ML Kit text blocks to our data class.
     */
    private fun convertTextBlocks(text: Text): List<OcrTextBlock> {
        return text.textBlocks.map { block ->
            OcrTextBlock(
                text = block.text,
                boundingBox = block.boundingBox?.let {
                    OcrBoundingBox(it.left, it.top, it.right, it.bottom)
                },
                lines = block.lines.map { line ->
                    OcrTextLine(
                        text = line.text,
                        boundingBox = line.boundingBox?.let {
                            OcrBoundingBox(it.left, it.top, it.right, it.bottom)
                        },
                        words = line.elements.map { element ->
                            OcrWord(
                                text = element.text,
                                boundingBox = element.boundingBox?.let {
                                    OcrBoundingBox(it.left, it.top, it.right, it.bottom)
                                },
                                confidence = 0.9f // ML Kit doesn't expose per-word confidence
                            )
                        }
                    )
                }
            )
        }
    }
    
    /**
     * Calculate average confidence for OCR result.
     */
    private fun calculateConfidence(text: Text): Float {
        // ML Kit doesn't provide explicit confidence scores
        // We estimate based on text presence
        return if (text.text.isNotEmpty()) 0.85f else 0f
    }
    
    /**
     * Add invisible text layer to a page for searchability.
     */
    private fun addTextLayerToPage(
        document: PDDocument,
        page: PDPage,
        text: Text,
        imageWidth: Int,
        imageHeight: Int,
        dpi: Float
    ) {
        val pageRect = page.mediaBox
        val pageWidth = pageRect.width
        val pageHeight = pageRect.height
        
        // Scale factor from image pixels to PDF points
        val scaleX = pageWidth / imageWidth
        val scaleY = pageHeight / imageHeight
        
        val contentStream = PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        )
        
        try {
            // Make text invisible
            val graphicsState = PDExtendedGraphicsState()
            graphicsState.nonStrokingAlphaConstant = 0f
            contentStream.setGraphicsStateParameters(graphicsState)
            
            val font = PDType1Font.HELVETICA
            
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val bbox = line.boundingBox ?: continue
                    
                    // Convert coordinates from image space to PDF space
                    // Note: PDF origin is bottom-left, image origin is top-left
                    val x = bbox.left * scaleX
                    val y = pageHeight - (bbox.bottom * scaleY)
                    
                    // Estimate font size based on bounding box height
                    val lineHeight = (bbox.bottom - bbox.top) * scaleY
                    val fontSize = (lineHeight * 0.8f).coerceIn(6f, 24f)
                    
                    contentStream.setFont(font, fontSize)
                    contentStream.beginText()
                    contentStream.newLineAtOffset(x, y)
                    
                    // Clean the text to remove unsupported characters
                    val cleanText = line.text.filter { it.code < 256 }
                    if (cleanText.isNotEmpty()) {
                        try {
                            contentStream.showText(cleanText)
                        } catch (e: Exception) {
                            // Skip if text can't be rendered
                        }
                    }
                    
                    contentStream.endText()
                }
            }
        } finally {
            contentStream.close()
        }
    }
    
    /**
     * Close the recognizer when done.
     */
    fun close() {
        recognizer.close()
    }
}
