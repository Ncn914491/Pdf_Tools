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
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.BufferedOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Moved from PdfViewerScreen.kt
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
    object Edit : PdfTool() // General Edit mode (shows annotation toolbar)
}

sealed class PdfViewerUiState {
    object Idle : PdfViewerUiState()
    object Loading : PdfViewerUiState()
    data class Error(val message: String) : PdfViewerUiState()
    data class Loaded(val totalPages: Int) : PdfViewerUiState()
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

data class SearchState(
    val query: String = "",
    val results: List<Pair<Int, Int>> = emptyList(), // (pageIndex, charIndex)
    val currentResultIndex: Int = 0,
    val isSearching: Boolean = false
)

class PdfViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PdfViewerUiState>(PdfViewerUiState.Idle)
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private val _toolState = MutableStateFlow<PdfTool>(PdfTool.None)
    val toolState: StateFlow<PdfTool> = _toolState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _selectedAnnotationTool = MutableStateFlow(AnnotationTool.NONE)
    val selectedAnnotationTool: StateFlow<AnnotationTool> = _selectedAnnotationTool.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Yellow.copy(alpha = 0.5f))
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _annotations = MutableStateFlow<List<AnnotationStroke>>(emptyList())
    val annotations: StateFlow<List<AnnotationStroke>> = _annotations.asStateFlow()

    // Document management
    private var document: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null
    private val documentMutex = Mutex()

    // Caches
    private val extractedTextCache = mutableMapOf<Int, String>()
    private val textPositionsCache = mutableMapOf<Int, List<TextPosition>>()

    // Bitmap Cache
    // Calculate cache size: Use 1/8th of the available memory for this memory cache.
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            // The cache size will be measured in kilobytes rather than number of items.
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
             // Recycle bitmap if evicted to free native memory?
             // Android Bitmaps on newer versions (Honeycomb+) are managed by Dalvik/ART,
             // but recycling can still help with large images.
             // However, reusing them via an object pool would be better than recycling if we re-render.
             // For simplicity and safety against reusing recycled bitmaps, we won't manually recycle here
             // immediately unless we are sure it's not used.
             // Relying on GC is safer for ViewModels.
        }
    }

    fun loadPdf(context: Context, uri: Uri, password: String = "") {
        viewModelScope.launch {
            _uiState.value = PdfViewerUiState.Loading
            try {
                if (!PDFBoxResourceLoader.isReady()) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                }

                closeDocument() // Close existing if any

                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open URI")

                    val doc = if (password.isNotEmpty()) {
                        PDDocument.load(inputStream, password)
                    } else {
                        PDDocument.load(inputStream)
                    }
                    // inputStream is closed by PDDocument.load usually, but check source.
                    // PDDocument.load(InputStream) reads the stream.

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
        // Check cache first
        bitmapCache.get(pageIndex)?.let { return it }

        return withContext(Dispatchers.IO) {
            documentMutex.withLock {
                try {
                    val renderer = pdfRenderer ?: return@withLock null
                    // Render at 1.5x scale (approx 108 dpi) for good quality on mobile
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
        // Reset specific annotation tool if we leave Edit mode
        if (tool != PdfTool.Edit) {
            _selectedAnnotationTool.value = AnnotationTool.NONE
        } else {
            // Default to Highlighter when entering Edit mode
            if (_selectedAnnotationTool.value == AnnotationTool.NONE) {
                _selectedAnnotationTool.value = AnnotationTool.HIGHLIGHTER
            }
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
                extractedTextCache.clear()
                textPositionsCache.clear()
            }
        }
    }

    fun performSearch(query: String) {
        val currentQuery = query.trim()
        if (currentQuery.length < 2) {
            _searchState.value = SearchState()
            return
        }

        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isSearching = true, query = currentQuery)

            val results = mutableListOf<Pair<Int, Int>>()
            val lowerQuery = currentQuery.lowercase()

            withContext(Dispatchers.IO) {
                // We need to access the document to extract text
                // We will use cache if available
                val totalPages = (uiState.value as? PdfViewerUiState.Loaded)?.totalPages ?: 0

                // Limit search to first 50 pages for performance for now, or all if feasible
                // The previous implementation limited to 20. Let's try 20 first to be safe, or just do it on demand?
                // Better to iterate all pages if we want a real search, but lazily?
                // For now, let's stick to the previous behavior of checking pages but using our cache.
                val maxPages = totalPages.coerceAtMost(50) // Increased from 20 to 50 for better UX

                for (i in 0 until maxPages) {
                    val text = getTextForPage(i) ?: continue
                    val lowerText = text.lowercase()

                    var pos = 0
                    while (true) {
                        val found = lowerText.indexOf(lowerQuery, pos)
                        if (found == -1) break
                        results.add(i to found)
                        pos = found + 1
                    }
                }
            }

            _searchState.value = SearchState(
                query = currentQuery,
                results = results,
                currentResultIndex = 0,
                isSearching = false
            )
        }
    }

    private suspend fun getTextForPage(pageIndex: Int): String? {
        // Check cache first
        extractedTextCache[pageIndex]?.let { return it }

        return documentMutex.withLock {
            val doc = document ?: return@withLock null
            try {
                val stripper = object : PDFTextStripper() {
                    override fun processTextPosition(text: TextPosition) {
                        super.processTextPosition(text)
                        // We also cache positions for highlighting later
                        // But since we can't easily return two things, we might need a separate pass
                        // or just cache it here if we refactor stripper.
                        // For now, let's just extract text standard way.
                    }
                }
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val text = stripper.getText(doc)
                extractedTextCache[pageIndex] = text
                text
            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Error extracting text for page $pageIndex", e)
                null
            }
        }
    }

    fun nextSearchResult() {
        val currentState = _searchState.value
        if (currentState.results.isNotEmpty() && currentState.currentResultIndex < currentState.results.size - 1) {
            _searchState.value = currentState.copy(currentResultIndex = currentState.currentResultIndex + 1)
        }
    }

    fun previousSearchResult() {
        val currentState = _searchState.value
        if (currentState.results.isNotEmpty() && currentState.currentResultIndex > 0) {
            _searchState.value = currentState.copy(currentResultIndex = currentState.currentResultIndex - 1)
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState()
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    fun saveAnnotations(context: Context, outputUri: Uri) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val success = saveAnnotatedPdfInternal(context, outputUri)
                if (success) {
                    _saveState.value = SaveState.Success
                } else {
                    _saveState.value = SaveState.Error("Failed to save annotations")
                }
            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Error saving annotations", e)
                _saveState.value = SaveState.Error(e.message ?: "Error saving annotations")
            }
        }
    }

    private suspend fun saveAnnotatedPdfInternal(context: Context, outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw IOException("Could not open output stream")

            // Use BufferedOutputStream for performance
            val bufferedOutput = BufferedOutputStream(outputStream)

            // Create new PDF document
            val newDocument = PDDocument()

            try {
                val currentUiState = _uiState.value
                val totalPages = if (currentUiState is PdfViewerUiState.Loaded) currentUiState.totalPages else 0
                val currentAnnotations = _annotations.value

                for (pageIndex in 0 until totalPages) {
                    // Fetch page from ourselves (forces render if not cached)
                    // We need to ensure we don't deadlock if loadPage uses the same mutex.
                    // loadPage uses mutex. This function is suspend but does NOT hold mutex yet.
                    // So it is safe to call loadPage.
                    val originalBitmap = loadPage(pageIndex) ?: continue

                    val pageAnnotations = currentAnnotations.filter { it.pageIndex == pageIndex }

                    // Create a mutable copy of the bitmap to draw annotations on
                    val annotatedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(annotatedBitmap)

                    // Draw annotations on the bitmap
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

                    // Create PDF page with the annotated bitmap
                    val width = annotatedBitmap.width.toFloat()
                    val height = annotatedBitmap.height.toFloat()

                    // Scale to reasonable PDF page size (72 DPI equivalent)
                    // Original render was at 1.5x (approx 108 dpi)
                    val scaleFactor = 72f / 108f
                    val pageWidth = width * scaleFactor
                    val pageHeight = height * scaleFactor

                    val page = PDPage(PDRectangle(pageWidth, pageHeight))
                    newDocument.addPage(page)

                    // Create image from bitmap
                    val pdImage = LosslessFactory.createFromImage(newDocument, annotatedBitmap)

                    // Draw image on page
                    PDPageContentStream(newDocument, page).use { contentStream ->
                        contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)
                    }

                    // Recycle the annotated bitmap (not the original!)
                    annotatedBitmap.recycle()
                }

                // Save the document
                newDocument.save(bufferedOutput)
                bufferedOutput.flush() // Ensure everything is written

                true
            } finally {
                newDocument.close()
                try {
                    bufferedOutput.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
                // outputStream is closed by bufferedOutput.close()
            }
        } catch (e: Exception) {
            Log.e("PdfViewerVM", "Error saving annotated PDF: ${e.message}", e)
            false
        }
    }

    suspend fun getSearchHighlights(pageIndex: Int, query: String): List<List<RectF>> {
        if (query.length < 2) return emptyList()

        // This is a heavy operation, ensure we are off main thread
        return withContext(Dispatchers.IO) {
            val highlights = mutableListOf<List<RectF>>()

            documentMutex.withLock {
                val doc = document ?: return@withLock emptyList()
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

                    // Trigger extraction to populate textPositions
                    val dummy = stripper.getText(doc)

                    val sb = StringBuilder()
                    textPositions.forEach { sb.append(it.unicode) }
                    val rawText = sb.toString().lowercase()
                    val lowerQuery = query.lowercase()

                    var pos = 0
                    while (true) {
                        val found = rawText.indexOf(lowerQuery, pos)
                        if (found == -1) break

                        val matchRects = mutableListOf<RectF>()

                        // Construct rect for this match
                        for (i in found until (found + lowerQuery.length)) {
                            if (i < textPositions.size) {
                                val tp = textPositions[i]
                                // PDFBox scale is 72 DPI. Our renderer uses 1.5 * 72 = 108 DPI approx?
                                // Actually renderImage(scale=1.5f) means 1.5 * 72 DPI if we consider 1.0 = 72 DPI
                                // But here we need to map PDF coordinates to Bitmap coordinates.
                                // The bitmap was rendered with scale = 1.5f (in loadPage)
                                // So we should scale PDF coordinates by 1.5f.
                                val scale = 1.5f

                                val x = tp.xDirAdj * scale
                                val y = tp.yDirAdj * scale
                                val w = tp.widthDirAdj * scale
                                val h = tp.heightDir * scale

                                matchRects.add(RectF(x, y, x + w, y + h))
                            }
                        }

                        if (matchRects.isNotEmpty()) {
                            highlights.add(matchRects)
                        }

                        pos = found + 1
                    }
                } catch (e: Exception) {
                    Log.e("PdfViewerVM", "Error getting highlights", e)
                }
            }
            highlights
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            closeDocument()
        }
    }
}
