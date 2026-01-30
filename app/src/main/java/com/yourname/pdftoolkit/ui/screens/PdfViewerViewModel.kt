package com.yourname.pdftoolkit.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.yourname.pdftoolkit.data.SafUriManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

enum class AnnotationTool(val displayName: String) {
    NONE("Select"),
    HIGHLIGHTER("Highlighter"),
    MARKER("Marker"),
    UNDERLINE("Underline")
}

data class AnnotationStroke(
    val pageIndex: Int,
    val tool: AnnotationTool,
    val color: Color,
    val points: List<Offset>,
    val strokeWidth: Float
)

// Sealed class for mutually exclusive tool states
sealed class PdfTool {
    object None : PdfTool()
    object Search : PdfTool()
    object Edit : PdfTool()
}

sealed class PdfViewerUiState {
    object Idle : PdfViewerUiState()
    object Loading : PdfViewerUiState()
    data class Error(val message: String) : PdfViewerUiState()
    data class Loaded(val totalPages: Int) : PdfViewerUiState()
}

data class SearchState(
    val isSearching: Boolean = false,
    val results: List<Pair<Int, Int>> = emptyList(), // pageIndex, matchIndex
    val query: String = ""
)

class PdfViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PdfViewerUiState>(PdfViewerUiState.Idle)
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val _toolState = MutableStateFlow<PdfTool>(PdfTool.None)
    val toolState: StateFlow<PdfTool> = _toolState.asStateFlow()

    private val _selectedAnnotationTool = MutableStateFlow(AnnotationTool.NONE)
    val selectedAnnotationTool: StateFlow<AnnotationTool> = _selectedAnnotationTool.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Yellow.copy(alpha = 0.5f))
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _annotations = MutableStateFlow<List<AnnotationStroke>>(emptyList())
    val annotations: StateFlow<List<AnnotationStroke>> = _annotations.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // Document management
    private var document: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null
    private val documentMutex = Mutex()
    private var searchJob: Job? = null

    // Bitmap Cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun loadPdf(context: Context, uri: Uri, password: String = "") {
        viewModelScope.launch {
            _uiState.value = PdfViewerUiState.Loading
            try {
                if (!PDFBoxResourceLoader.isReady()) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                }

                closeDocument()

                withContext(Dispatchers.IO) {
                    var inputStream: InputStream? = null

                    // Try robust loading (with cache fallback if needed)
                    // Check if it's our file provider or needs copying
                    val isOurFileProvider = uri.scheme == "content" &&
                        (uri.authority == "${context.packageName}.provider" ||
                         uri.authority?.startsWith("com.yourname.pdftoolkit") == true &&
                         uri.authority?.endsWith(".provider") == true)

                    if (isOurFileProvider) {
                        // Try direct open
                         try {
                            inputStream = context.contentResolver.openInputStream(uri)
                        } catch (e: Exception) {
                            Log.w("PdfViewerVM", "Failed to open provider URI directly: $e")
                        }
                    } else {
                        // Try direct open first
                        try {
                            inputStream = context.contentResolver.openInputStream(uri)
                        } catch (e: Exception) {
                            Log.w("PdfViewerVM", "Direct open failed, trying cache copy: $e")
                        }

                        // If direct open failed, try copy to cache
                        if (inputStream == null) {
                            val cachedFile = copyUriToCache(context, uri)
                            if (cachedFile != null) {
                                inputStream = cachedFile.inputStream()
                            }
                        }
                    }

                    if (inputStream == null) {
                        throw Exception("Cannot open URI")
                    }

                    val doc = if (password.isNotEmpty()) {
                        PDDocument.load(inputStream, password)
                    } else {
                        PDDocument.load(inputStream)
                    }

                    documentMutex.withLock {
                        document = doc
                        pdfRenderer = PDFRenderer(doc)
                    }

                    _uiState.value = PdfViewerUiState.Loaded(doc.numberOfPages)
                }
            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Error loading PDF", e)
                _uiState.value = PdfViewerUiState.Error(e.message ?: "Failed to load PDF")
            }
        }
    }

    suspend fun loadPage(pageIndex: Int): Bitmap? {
        bitmapCache.get(pageIndex)?.let { return it }

        return withContext(Dispatchers.IO) {
            documentMutex.withLock {
                try {
                    val renderer = pdfRenderer ?: return@withLock null
                    val scale = 1.5f
                    val bitmap = renderer.renderImage(pageIndex, scale)

                    if (bitmap != null) {
                        bitmapCache.put(pageIndex, bitmap)
                    }
                    bitmap
                } catch (e: Exception) {
                    Log.e("PdfViewerVM", "Error rendering page $pageIndex", e)
                    null
                }
            }
        }
    }

    fun setTool(tool: PdfTool) {
        _toolState.value = tool
        if (tool != PdfTool.Edit) {
            _selectedAnnotationTool.value = AnnotationTool.NONE
        } else {
            if (_selectedAnnotationTool.value == AnnotationTool.NONE) {
                _selectedAnnotationTool.value = AnnotationTool.HIGHLIGHTER
            }
        }

        if (tool != PdfTool.Search) {
             // Clear search if we leave search mode
             _searchState.value = SearchState()
             searchJob?.cancel()
        }
    }

    fun setAnnotationTool(tool: AnnotationTool) {
        _selectedAnnotationTool.value = tool
        if (tool != AnnotationTool.NONE) {
            _toolState.value = PdfTool.Edit
        }
    }

    fun setColor(color: Color) {
        _selectedColor.value = color
    }

    fun addAnnotation(stroke: AnnotationStroke) {
        val currentList = _annotations.value.toMutableList()
        currentList.add(stroke)
        _annotations.value = currentList
    }

    fun undoAnnotation() {
        val currentList = _annotations.value.toMutableList()
        if (currentList.isNotEmpty()) {
            currentList.removeAt(currentList.lastIndex)
            _annotations.value = currentList
        }
    }

    fun clearAnnotations() {
        _annotations.value = emptyList()
    }

    fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchState.value = SearchState(query = query)
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _searchState.value = SearchState(isSearching = true, query = query)
            val results = mutableListOf<Pair<Int, Int>>()

            documentMutex.withLock {
                val doc = document ?: return@withLock
                try {
                    val stripper = PDFTextStripper()
                    // Limit search to first 50 pages for performance if needed,
                    // or search all but be aware of time.
                    // Let's search all for correctness but check cancellation.
                    val totalPages = doc.numberOfPages

                    for (i in 1..totalPages) {
                        if (!isActive) break

                        stripper.startPage = i
                        stripper.endPage = i
                        val text = stripper.getText(doc).lowercase()
                        val lowerQuery = query.lowercase()

                        var pos = 0
                        while (true) {
                            val found = text.indexOf(lowerQuery, pos)
                            if (found == -1) break
                            results.add((i - 1) to found)
                            pos = found + 1
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PdfViewerVM", "Error performing search", e)
                }
            }

            if (isActive) {
                _searchState.value = SearchState(
                    isSearching = false,
                    results = results,
                    query = query
                )
            }
        }
    }

    suspend fun getSearchHighlights(pageIndex: Int, query: String): List<List<RectF>> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()

        documentMutex.withLock {
            val doc = document ?: return@withLock emptyList()
            val allMatches = mutableListOf<List<RectF>>()

            try {
                val textPositions = mutableListOf<TextPosition>()
                val stripper = object : PDFTextStripper() {
                    override fun processTextPosition(text: TextPosition) {
                        super.processTextPosition(text)
                        textPositions.add(text)
                    }
                }

                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1

                stripper.getText(doc) // This populates textPositions

                // Now find matches in textPositions
                val sb = StringBuilder()
                textPositions.forEach { sb.append(it.unicode) }
                val rawText = sb.toString().lowercase()
                val lowerQuery = query.lowercase()

                var pos = 0
                while (true) {
                    val found = rawText.indexOf(lowerQuery, pos)
                    if (found == -1) break

                    val matchRects = mutableListOf<RectF>()
                    for (i in found until (found + lowerQuery.length)) {
                        if (i < textPositions.size) {
                            val tp = textPositions[i]
                            // Scale factor: PDFBox standard 72 DPI vs Render scale?
                            // Render scale in loadPage is 1.5f (approx 108 DPI).
                            // TextPosition uses PDF points (72 DPI).
                            // We need rects relative to the PDF page size in points,
                            // then the UI scales them to the image size.
                            // The UI draws the image at some size.
                            // The UI logic:
                            // rects.forEach { rect ->
                            //    val scaleX = size.width / bitmap.width
                            //    ...
                            // }
                            // If the bitmap is 1.5x, and we return rects in 72 DPI,
                            // we need to know what "bitmap.width" corresponds to.
                            //
                            // The previous implementation used: val scale = 150f / 72f
                            // And loadPage used 1.5f? Wait.
                            // Previous loadPage: val scale = 1.5f
                            // Previous getSearchHighlights: val scale = 150f / 72f ~= 2.08f
                            // This implies the previous implementation assumed the bitmap was rendered at 150 DPI (scale ~2.08)
                            // But loadPage says 1.5f.
                            //
                            // Let's look at loadPage again:
                            // renderer.renderImage(pageIndex, scale)
                            // If scale is 1.5, result is 1.5 * 72 = 108 DPI.
                            //
                            // The UI code scaled the rect (from highlight) to the displayed size.
                            // drawRect(topLeft = Offset(rect.left * scaleX, ...))
                            // where scaleX = size.width / bitmap.width.
                            //
                            // So if I return rects in PDF points (72 DPI),
                            // and the bitmap is 108 DPI.
                            // The UI calculates scaleX = ViewWidth / (PageWidthPoints * 1.5).
                            // The rect is in PageWidthPoints.
                            // So we need to return rects in...
                            // Actually, the previous implementation returned rects scaled by `150f / 72f`.
                            // It seems it was trying to match a specific render scale.
                            //
                            // Use PDF points (72 DPI) for the rects.
                            // Then in UI:
                            // Canvas draws on top of Image.
                            // We need to map PDF Point -> UI Coordinate.
                            // UI Coordinate = PDF Point * (ViewWidth / PDFPageWidth).
                            //
                            // The previous UI code:
                            // val scaleX = size.width.toFloat() / bitmap!!.width.toFloat()
                            // drawRect(topLeft = Offset(rect.left * scaleX, ...))
                            //
                            // This assumes `rect.left` is in the same coordinate space as `bitmap.width`.
                            // If `bitmap` is 108 DPI (1.5x), and `rect` is 72 DPI (1x).
                            // Then `rect.left` is 100. `bitmap.width` is 150.
                            // `size.width` is e.g. 300 (displayed size).
                            // `scaleX` = 300 / 150 = 2.
                            // `drawRect` at 100 * 2 = 200.
                            // Should be at 100 * (300/100) = 300? No.
                            // 100 points = 1/6th of 600 points page.
                            // Displayed page is 300 pixels wide.
                            // Point 100 should be at 50 pixels? No, 100/600 * 300 = 50.
                            // My calculation: 100 * 2 = 200. Wrong.
                            //
                            // It implies `rect.left` MUST be in the same scale as `bitmap`.
                            // So if `bitmap` is 1.5x (108 DPI), `rect` must be scaled by 1.5x.
                            //
                            // In loadPage: val scale = 1.5f.
                            // So I should scale the rects by 1.5f here.

                            val renderScale = 1.5f
                            val x = tp.xDirAdj * renderScale
                            val y = tp.yDirAdj * renderScale
                            val w = tp.widthDirAdj * renderScale
                            val h = tp.heightDir * renderScale

                            matchRects.add(RectF(x, y, x + w, y + h))
                        }
                    }
                    if (matchRects.isNotEmpty()) {
                        allMatches.add(matchRects)
                    }
                    pos = found + 1
                }
            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Error getting highlights", e)
            }
            allMatches
        }
    }

    suspend fun saveAnnotations(
        context: Context,
        outputUri: Uri,
        totalPages: Int
    ): Boolean = withContext(Dispatchers.IO) {
        // Need to use the document to render pages, but we create a NEW document for output.
        // We can't use the existing document as the target because we are "flattening" annotations into images.
        // And we don't want to modify the existing document instance in-place if we want "Save Copy".

        var success = false
        val newDoc = PDDocument()

        try {
            documentMutex.withLock {
                // Ensure renderer is available
                if (pdfRenderer == null && document != null) {
                    pdfRenderer = PDFRenderer(document)
                }
            }

            for (pageIndex in 0 until totalPages) {
                // Load original bitmap (uses mutex internally)
                val originalBitmap = loadPage(pageIndex) ?: continue

                val pageAnnotations = _annotations.value.filter { it.pageIndex == pageIndex }

                // Draw annotations
                val annotatedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(annotatedBitmap)

                for (annotation in pageAnnotations) {
                    if (annotation.points.size >= 2) {
                         val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (annotation.color.alpha * 255).toInt(),
                                (annotation.color.red * 255).toInt(),
                                (annotation.color.green * 255).toInt(),
                                (annotation.color.blue * 255).toInt()
                            )
                            strokeWidth = annotation.strokeWidth
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        val path = android.graphics.Path()
                        path.moveTo(annotation.points[0].x, annotation.points[0].y)
                        for (i in 1 until annotation.points.size) {
                            path.lineTo(annotation.points[i].x, annotation.points[i].y)
                        }
                        canvas.drawPath(path, paint)
                    }
                }

                // Add to new doc
                // Scale back to 72 DPI?
                // Original render scale was 1.5f.
                // If we want the PDF page to be standard size (approx A4 or original size), we should scale down.
                // But simple approach: create page of image size.
                // Better approach (matches previous logic):
                // val scaleFactor = 72f / 150f (approx 0.48)
                // If original was 1.5 (108 DPI), then 72/108 = 0.666
                // Previous logic had 150 DPI render?
                // Previous logic used: val scaleFactor = 72f / 150f
                // But previous logic relied on loadPage.
                // If loadPage used 1.5f (108 DPI), then 72/108 is correct to get back to points.
                // Let's use 1/1.5f.

                val scaleFactor = 1f / 1.5f
                val width = annotatedBitmap.width.toFloat()
                val height = annotatedBitmap.height.toFloat()
                val pageWidth = width * scaleFactor
                val pageHeight = height * scaleFactor

                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                newDoc.addPage(page)

                val pdImage = LosslessFactory.createFromImage(newDoc, annotatedBitmap)
                PDPageContentStream(newDoc, page).use { contentStream ->
                    contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)
                }

                annotatedBitmap.recycle()
            }

            val outputStream = context.contentResolver.openOutputStream(outputUri)
            if (outputStream != null) {
                newDoc.save(outputStream)
                outputStream.close()
                success = true
            }

        } catch (e: Exception) {
            Log.e("PdfViewerVM", "Error saving annotations", e)
        } finally {
            newDoc.close()
        }

        success
    }

    private fun copyUriToCache(context: Context, uri: Uri): File? {
        // Implementation from Screen
         try {
            val cacheDir = File(context.cacheDir, "pdf_viewer_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Clean old
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < oneHourAgo) {
                    file.delete()
                }
            }

            val cachedFile = File(cacheDir, "viewer_${System.currentTimeMillis()}.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                cachedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return if (cachedFile.exists() && cachedFile.length() > 0) cachedFile else null
        } catch (e: Exception) {
            Log.e("PdfViewerVM", "Error copying to cache", e)
            return null
        }
    }

    private suspend fun closeDocument() {
        documentMutex.withLock {
            try {
                document?.close()
            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Error closing document", e)
            } finally {
                document = null
                pdfRenderer = null
                bitmapCache.evictAll()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            closeDocument()
        }
    }
}
