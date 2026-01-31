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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

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

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState() // Extracting text
    data class Ready(
        val matches: List<Pair<Int, Int>> = emptyList(), // pageIndex, matchIndex
        val currentMatchIndex: Int = 0,
        val highlights: Map<Int, List<List<RectF>>> = emptyMap() // pageIndex -> list of matches(list of rects)
    ) : SearchState()
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

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

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // Document management
    private var document: PDDocument? = null
    private var pdfRenderer: PDFRenderer? = null
    private val documentMutex = Mutex()

    // Text Extraction Cache
    private val textPositionsCache = mutableMapOf<Int, List<TextPosition>>()
    private val extractedTextCache = mutableMapOf<Int, String>()
    private var isTextExtracted = false
    private var extractionJob: Job? = null

    // Bitmap Cache
    // Calculate cache size: Use 1/8th of the available memory for this memory cache.
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            // The cache size will be measured in kilobytes rather than number of items.
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

                closeDocument() // Close existing if any

                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open URI")

                    val doc = if (password.isNotEmpty()) {
                        PDDocument.load(inputStream, password)
                    } else {
                        PDDocument.load(inputStream)
                    }

                    documentMutex.withLock {
                        document = doc
                        pdfRenderer = PDFRenderer(doc)
                    }
                    // Reset caches
                    textPositionsCache.clear()
                    extractedTextCache.clear()
                    isTextExtracted = false

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

        when (tool) {
            PdfTool.Search -> {
                // Prepare for search if needed
                if (!isTextExtracted) {
                   extractText()
                }
            }
            PdfTool.Edit -> {
                 // Default to Highlighter when entering Edit mode
                if (_selectedAnnotationTool.value == AnnotationTool.NONE) {
                    _selectedAnnotationTool.value = AnnotationTool.HIGHLIGHTER
                }
            }
            PdfTool.None -> {
                _selectedAnnotationTool.value = AnnotationTool.NONE
                _searchState.value = SearchState.Idle
            }
        }
    }

    private fun extractText() {
        if (isTextExtracted || extractionJob?.isActive == true) return

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            _searchState.value = SearchState.Loading
            try {
                val doc = document ?: return@launch
                val pages = doc.numberOfPages
                val maxPages = pages.coerceAtMost(50) // Limit to first 50 pages for performance initially

                // Use PDFTextStripper to get text and positions
                // We need to override processTextPosition to capture coordinates
                for (i in 0 until maxPages) {
                    val pageTextPositions = mutableListOf<TextPosition>()
                    val stripper = object : PDFTextStripper() {
                        override fun processTextPosition(text: TextPosition) {
                            super.processTextPosition(text)
                            pageTextPositions.add(text)
                        }
                    }
                    stripper.sortByPosition = true
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1

                    documentMutex.withLock {
                        stripper.getText(doc)
                    }

                    textPositionsCache[i] = pageTextPositions

                    val sb = StringBuilder()
                    pageTextPositions.forEach { sb.append(it.unicode) }
                    extractedTextCache[i] = sb.toString()
                }

                isTextExtracted = true
                _searchState.value = SearchState.Ready()

            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Error extracting text", e)
                _searchState.value = SearchState.Idle // Reset on error
            }
        }
    }

    fun search(query: String) {
        if (query.length < 2) {
             _searchState.value = if (isTextExtracted) SearchState.Ready() else SearchState.Idle
             return
        }

        viewModelScope.launch(Dispatchers.Default) {
             val matches = mutableListOf<Pair<Int, Int>>()
             val highlights = mutableMapOf<Int, List<List<RectF>>>()
             val lowerQuery = query.lowercase()

             extractedTextCache.forEach { (pageIndex, text) ->
                 val lowerText = text.lowercase()
                 val pagePositions = textPositionsCache[pageIndex] ?: return@forEach

                 var pos = 0
                 while (true) {
                     val found = lowerText.indexOf(lowerQuery, pos)
                     if (found == -1) break

                     matches.add(pageIndex to found)

                     // Calculate highlights for this match
                     val matchRects = mutableListOf<RectF>()
                     for (i in found until (found + lowerQuery.length)) {
                         if (i < pagePositions.size) {
                             val tp = pagePositions[i]
                             // Scale PDF coordinates (72 dpi) to Render coordinates (1.5x of 72 = 108 dpi, but Screen uses its own scale)
                             // Actually, we store raw PDF coordinates here, and Screen scales them to the View size.
                             // But PDFBox renderImage is usually 72dpi * scale.
                             // Wait, PdfViewerScreen uses: scale = 150f / 72f in the old code.
                             // Let's store raw PDF coordinates (72 dpi)

                             val x = tp.xDirAdj
                             val y = tp.yDirAdj
                             val w = tp.widthDirAdj
                             val h = tp.heightDir

                             matchRects.add(RectF(x, y, x + w, y + h))
                         }
                     }

                     if (matchRects.isNotEmpty()) {
                         val currentHighlights = highlights[pageIndex]?.toMutableList() ?: mutableListOf()
                         currentHighlights.add(matchRects)
                         highlights[pageIndex] = currentHighlights
                     }

                     pos = found + 1
                 }
             }

             _searchState.value = SearchState.Ready(matches, 0, highlights)
        }
    }

    fun navigateSearchResult(direction: Int) { // -1 for prev, 1 for next
        val currentState = _searchState.value
        if (currentState is SearchState.Ready && currentState.matches.isNotEmpty()) {
            val newIndex = (currentState.currentMatchIndex + direction).mod(currentState.matches.size)
             _searchState.value = currentState.copy(currentMatchIndex = newIndex)
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

    fun saveAnnotatedPdf(context: Context, outputUri: Uri) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val success = saveAnnotatedPdfInternal(context, outputUri)
            if (success) {
                _saveState.value = SaveState.Success
            } else {
                _saveState.value = SaveState.Error("Failed to save PDF")
            }
            // Reset state after a delay or let UI handle it
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    private suspend fun saveAnnotatedPdfInternal(context: Context, outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        // Use documentMutex to ensure no other operation interferes
        documentMutex.withLock {
            try {
                val doc = document ?: return@withLock false
                val currentAnnotations = _annotations.value

                val outputStream = context.contentResolver.openOutputStream(outputUri)
                    ?: throw IOException("Could not open output stream")

                // We will create a NEW document where we draw the original pages + annotations as images
                // This flattens the annotations.
                // Alternatively, we could add annotations to the existing PDDocument pages,
                // but that's more complex with PDFBox on Android (need to manipulate content stream).
                // The "Flatten" approach (drawing as image) is what the previous code did.

                val newDocument = PDDocument()

                try {
                    for (pageIndex in 0 until doc.numberOfPages) {
                         // Render the original page
                         val renderer = pdfRenderer ?: continue
                         // Use high quality for saving
                         val scale = 2.0f
                         val originalBitmap = renderer.renderImage(pageIndex, scale)

                         // Prepare to draw annotations
                         val pageAnnotations = currentAnnotations.filter { it.pageIndex == pageIndex }

                         val finalBitmap = if (pageAnnotations.isNotEmpty()) {
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
                                         // Scale stroke width
                                         strokeWidth = annotation.strokeWidth * (scale / 1.5f) // Adjust for scale difference (1.5f is screen render scale)
                                         style = android.graphics.Paint.Style.STROKE
                                         strokeCap = android.graphics.Paint.Cap.ROUND
                                         strokeJoin = android.graphics.Paint.Join.ROUND
                                         isAntiAlias = true
                                     }

                                     val path = android.graphics.Path()
                                     // Scale points
                                     val s = scale / 1.5f // Ratio between save scale and screen scale
                                     path.moveTo(annotation.points[0].x * s, annotation.points[0].y * s)
                                     for (i in 1 until annotation.points.size) {
                                         path.lineTo(annotation.points[i].x * s, annotation.points[i].y * s)
                                     }
                                     canvas.drawPath(path, paint)
                                 }
                             }
                             originalBitmap.recycle() // Recycle original
                             annotatedBitmap
                         } else {
                             originalBitmap
                         }

                         // Add to new document
                         // Calculate PDF page size based on bitmap size (72 DPI)
                         // PDFBox default user space unit is 1/72 inch.
                         // finalBitmap is at 'scale' (2.0f, approx 144 DPI)
                         val width = finalBitmap.width.toFloat()
                         val height = finalBitmap.height.toFloat()
                         val pdfScale = 72f / (72f * scale) // = 1/scale = 0.5

                         val pageWidth = width * pdfScale
                         val pageHeight = height * pdfScale

                         val page = PDPage(PDRectangle(pageWidth, pageHeight))
                         newDocument.addPage(page)

                         val pdImage = LosslessFactory.createFromImage(newDocument, finalBitmap)
                         PDPageContentStream(newDocument, page).use { contentStream ->
                             contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)
                         }

                         finalBitmap.recycle()
                    }

                    newDocument.save(outputStream)
                    true
                } catch (e: Exception) {
                    Log.e("PdfViewerVM", "Error saving", e)
                    false
                } finally {
                    newDocument.close()
                    outputStream.close()
                }
            } catch (e: Exception) {
                Log.e("PdfViewerVM", "Outer Error saving", e)
                false
            }
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
                textPositionsCache.clear()
                extractedTextCache.clear()
                isTextExtracted = false
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
