package com.yourname.pdftoolkit.domain.operations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Page sizes for PDF creation.
 */
enum class PageSize(val rectangle: PDRectangle) {
    A4(PDRectangle.A4),
    LETTER(PDRectangle.LETTER),
    LEGAL(PDRectangle.LEGAL),
    FIT_IMAGE(PDRectangle.A4) // Will be adjusted to fit image
}

/**
 * Image formats for PDF to image conversion.
 */
enum class ImageFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg")
}

/**
 * Handles image to PDF and PDF to image conversion operations.
 */
class ImageConverter {
    
    /**
     * Convert multiple images to a single PDF.
     * 
     * @param context Android context
     * @param imageUris List of image URIs (supports JPEG, PNG)
     * @param outputStream Output stream for the PDF
     * @param pageSize Page size for the PDF
     * @param quality Image quality (1-100, only affects JPEG images)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Number of images converted
     */
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputStream: OutputStream,
        pageSize: PageSize = PageSize.A4,
        quality: Int = 85,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("No images provided")
            )
        }
        
        val document = PDDocument()
        
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = loadBitmap(context, uri)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Cannot load image: $uri")
                    )
                
                try {
                    addImageAsPage(document, bitmap, pageSize)
                } finally {
                    bitmap.recycle()
                }
                
                onProgress((index + 1).toFloat() / imageUris.size)
            }
            
            document.save(outputStream)
            
            Result.success(imageUris.size)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document.close()
        }
    }
    
    /**
     * Convert PDF pages to images.
     * 
     * @param context Android context
     * @param inputUri URI of the PDF
     * @param format Output image format
     * @param dpi Resolution (72, 150, 300, etc.)
     * @param pageNumbers Specific pages to convert (1-indexed), or null for all pages
     * @param outputCallback Callback for each generated image (pageNumber, bitmap)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Number of images created
     */
    suspend fun pdfToImages(
        context: Context,
        inputUri: Uri,
        format: ImageFormat = ImageFormat.PNG,
        dpi: Int = 150,
        pageNumbers: List<Int>? = null,
        outputCallback: suspend (pageNumber: Int, bitmap: Bitmap) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open input file")
                )
            
            document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            
            if (totalPages == 0) {
                return@withContext Result.failure(
                    IllegalStateException("PDF has no pages")
                )
            }
            
            val renderer = PDFRenderer(document)
            val pagesToConvert = pageNumbers ?: (1..totalPages).toList()
            
            // Validate page numbers
            val invalidPages = pagesToConvert.filter { it < 1 || it > totalPages }
            if (invalidPages.isNotEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid page numbers: $invalidPages")
                )
            }
            
            pagesToConvert.forEachIndexed { index, pageNum ->
                val bitmap = renderer.renderImageWithDPI(pageNum - 1, dpi.toFloat())
                
                try {
                    outputCallback(pageNum, bitmap)
                } finally {
                    bitmap.recycle()
                }
                
                onProgress((index + 1).toFloat() / pagesToConvert.size)
            }
            
            Result.success(pagesToConvert.size)
            
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            document?.close()
        }
    }
    
    /**
     * Load a bitmap from a URI with memory-efficient options.
     */
    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First, get image dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Calculate sample size to avoid OOM
                val maxDimension = 2048
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || 
                       options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                
                // Decode with sample size
                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(stream2, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Add an image as a new page in the document.
     */
    private fun addImageAsPage(
        document: PDDocument,
        bitmap: Bitmap,
        pageSize: PageSize
    ) {
        val pageRect = if (pageSize == PageSize.FIT_IMAGE) {
            // Create page that fits the image
            PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
        } else {
            pageSize.rectangle
        }
        
        val page = PDPage(pageRect)
        document.addPage(page)
        
        // Create image from bitmap
        val pdImage = LosslessFactory.createFromImage(document, bitmap)
        
        // Calculate scaling to fit the page while maintaining aspect ratio
        val pageWidth = pageRect.width
        val pageHeight = pageRect.height
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        
        val scale = minOf(pageWidth / imageWidth, pageHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        
        // Center the image on the page
        val x = (pageWidth - scaledWidth) / 2
        val y = (pageHeight - scaledHeight) / 2
        
        PDPageContentStream(document, page).use { contentStream ->
            contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight)
        }
    }
}
