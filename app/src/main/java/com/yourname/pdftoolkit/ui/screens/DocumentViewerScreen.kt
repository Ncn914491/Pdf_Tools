package com.yourname.pdftoolkit.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.IOException

/**
 * Types of Office documents supported.
 */
enum class DocumentType(val extensions: List<String>, val mimeTypes: List<String>) {
    WORD(
        listOf("docx", "doc"),
        listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")
    ),
    EXCEL(
        listOf("xlsx", "xls"),
        listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")
    ),
    POWERPOINT(
        listOf("pptx", "ppt"),
        listOf("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/vnd.ms-powerpoint")
    ),
    UNKNOWN(emptyList(), emptyList())
}

/**
 * Represents parsed content from an Office document.
 */
sealed class DocumentContent {
    data class WordContent(
        val paragraphs: List<WordParagraph>,
        val pageCount: Int = 1
    ) : DocumentContent()
    
    data class ExcelContent(
        val sheets: List<ExcelSheet>
    ) : DocumentContent()
    
    data class PowerPointContent(
        val slides: List<SlideContent>
    ) : DocumentContent()
    
    data class Error(val message: String) : DocumentContent()
}

data class WordParagraph(
    val text: String,
    val isHeading: Boolean = false,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

data class ExcelSheet(
    val name: String,
    val rows: List<List<String>>
)

data class SlideContent(
    val slideNumber: Int,
    val title: String,
    val content: List<String>
)

/**
 * Document Viewer Screen for Office documents (DOCX, XLSX, PPTX).
 * Supports search, share, and open with external app functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentUri: Uri?,
    documentName: String = "Document",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(true) }
    var documentContent by remember { mutableStateOf<DocumentContent?>(null) }
    var documentType by remember { mutableStateOf(DocumentType.UNKNOWN) }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResultCount by remember { mutableIntStateOf(0) }
    
    // Load document when screen opens
    LaunchedEffect(documentUri) {
        if (documentUri == null) {
            documentContent = DocumentContent.Error("No document provided")
            isLoading = false
            return@LaunchedEffect
        }
        
        isLoading = true
        scope.launch {
            try {
                val type = detectDocumentType(context, documentUri, documentName)
                documentType = type
                documentContent = loadDocument(context, documentUri, type)
            } catch (e: Exception) {
                documentContent = DocumentContent.Error("Failed to load document: ${e.localizedMessage}")
            }
            isLoading = false
        }
    }
    
    // Update search result count when search query or content changes
    LaunchedEffect(searchQuery, documentContent) {
        if (searchQuery.isBlank()) {
            searchResultCount = 0
            return@LaunchedEffect
        }
        
        searchResultCount = when (documentContent) {
            is DocumentContent.WordContent -> {
                val content = documentContent as DocumentContent.WordContent
                content.paragraphs.sumOf { para ->
                    para.text.lowercase().split(searchQuery.lowercase()).size - 1
                }
            }
            is DocumentContent.ExcelContent -> {
                val content = documentContent as DocumentContent.ExcelContent
                content.sheets.sumOf { sheet ->
                    sheet.rows.sumOf { row ->
                        row.sumOf { cell ->
                            cell.lowercase().split(searchQuery.lowercase()).size - 1
                        }
                    }
                }
            }
            is DocumentContent.PowerPointContent -> {
                val content = documentContent as DocumentContent.PowerPointContent
                content.slides.sumOf { slide ->
                    val titleMatches = slide.title.lowercase().split(searchQuery.lowercase()).size - 1
                    val contentMatches = slide.content.sumOf { text ->
                        text.lowercase().split(searchQuery.lowercase()).size - 1
                    }
                    titleMatches + contentMatches
                }
            }
            else -> 0
        }
    }
    
    Scaffold(
        topBar = {
            if (isSearchActive) {
                // Search mode top bar
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    resultCount = searchResultCount
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = documentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = when (documentType) {
                                    DocumentType.WORD -> "Word Document"
                                    DocumentType.EXCEL -> "Excel Spreadsheet"
                                    DocumentType.POWERPOINT -> "PowerPoint Presentation"
                                    DocumentType.UNKNOWN -> "Document"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search button
                        if (documentContent != null && documentContent !is DocumentContent.Error) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        
                        // Share button
                        if (documentUri != null) {
                            IconButton(onClick = { shareDocument(context, documentUri, documentType) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = { openWithExternalApp(context, documentUri, documentType) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open with...")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    // Loading state
                    DocumentLoadingState()
                }
                
                documentContent is DocumentContent.Error -> {
                    // Error state
                    val error = documentContent as DocumentContent.Error
                    DocumentErrorState(
                        message = error.message,
                        onGoBack = onNavigateBack
                    )
                }
                
                documentContent is DocumentContent.WordContent -> {
                    WordDocumentView(
                        content = documentContent as DocumentContent.WordContent,
                        searchQuery = searchQuery
                    )
                }
                
                documentContent is DocumentContent.ExcelContent -> {
                    val excelContent = documentContent as DocumentContent.ExcelContent
                    ExcelDocumentView(
                        content = excelContent,
                        selectedSheetIndex = selectedSheetIndex,
                        onSheetSelected = { selectedSheetIndex = it },
                        searchQuery = searchQuery
                    )
                }
                
                documentContent is DocumentContent.PowerPointContent -> {
                    PowerPointDocumentView(
                        content = documentContent as DocumentContent.PowerPointContent,
                        searchQuery = searchQuery
                    )
                }
            }
        }
    }
}

/**
 * Search top bar with text field and result count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    resultCount: Int
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search in document...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Text(
                            text = if (resultCount > 0) "$resultCount found" else "No matches",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (resultCount > 0) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close search")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Loading state composable.
 */
@Composable
private fun DocumentLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading document...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Error state composable.
 */
@Composable
private fun DocumentErrorState(
    message: String,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Unable to open document",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoBack) {
            Text("Go Back")
        }
    }
}

@Composable
private fun WordDocumentView(
    content: DocumentContent.WordContent,
    searchQuery: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(content.paragraphs) { paragraph ->
            if (paragraph.text.isNotBlank()) {
                Text(
                    text = buildHighlightedText(
                        text = paragraph.text,
                        searchQuery = searchQuery,
                        baseStyle = SpanStyle(
                            fontWeight = if (paragraph.isBold || paragraph.isHeading) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (paragraph.isItalic) FontStyle.Italic else FontStyle.Normal,
                            fontSize = if (paragraph.isHeading) 20.sp else 16.sp
                        )
                    ),
                    modifier = Modifier.padding(vertical = if (paragraph.isHeading) 12.dp else 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        
        if (content.paragraphs.isEmpty()) {
            item {
                Text(
                    text = "This document appears to be empty.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
private fun ExcelDocumentView(
    content: DocumentContent.ExcelContent,
    selectedSheetIndex: Int,
    onSheetSelected: (Int) -> Unit,
    searchQuery: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Sheet tabs
        if (content.sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheetIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                content.sheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = selectedSheetIndex == index,
                        onClick = { onSheetSelected(index) },
                        text = { Text(sheet.name) }
                    )
                }
            }
        }
        
        // Sheet content
        if (content.sheets.isNotEmpty() && selectedSheetIndex < content.sheets.size) {
            val sheet = content.sheets[selectedSheetIndex]
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(sheet.rows) { row ->
                    val rowIndex = sheet.rows.indexOf(row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        row.forEachIndexed { index, cell ->
                            val containsMatch = searchQuery.isNotBlank() && 
                                cell.contains(searchQuery, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .background(
                                        when {
                                            containsMatch -> Color(0xFFFFEB3B).copy(alpha = 0.3f)
                                            rowIndex == 0 -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = buildHighlightedText(
                                        text = cell,
                                        searchQuery = searchQuery,
                                        baseStyle = SpanStyle(
                                            fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                                        )
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3
                                )
                            }
                            if (index < row.size - 1) {
                                Divider(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(40.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PowerPointDocumentView(
    content: DocumentContent.PowerPointContent,
    searchQuery: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(content.slides) { slide ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Slide number badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Slide ${slide.slideNumber}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Title with search highlight
                    if (slide.title.isNotBlank()) {
                        Text(
                            text = buildHighlightedText(
                                text = slide.title,
                                searchQuery = searchQuery,
                                baseStyle = SpanStyle(fontWeight = FontWeight.Bold)
                            ),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Content with search highlight
                    slide.content.forEach { text ->
                        if (text.isNotBlank()) {
                            Text(
                                text = buildHighlightedText(
                                    text = "• $text",
                                    searchQuery = searchQuery,
                                    baseStyle = SpanStyle()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    
                    if (slide.title.isBlank() && slide.content.isEmpty()) {
                        Text(
                            text = "(Empty slide)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
        
        if (content.slides.isEmpty()) {
            item {
                Text(
                    text = "This presentation appears to be empty.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

/**
 * Build annotated string with highlighted search matches.
 */
private fun buildHighlightedText(
    text: String,
    searchQuery: String,
    baseStyle: SpanStyle
) = buildAnnotatedString {
    if (searchQuery.isBlank()) {
        withStyle(baseStyle) {
            append(text)
        }
        return@buildAnnotatedString
    }
    
    val highlightStyle = baseStyle.copy(
        background = Color(0xFFFFEB3B).copy(alpha = 0.6f),
        fontWeight = FontWeight.Bold
    )
    
    var startIndex = 0
    val lowercaseText = text.lowercase()
    val lowercaseQuery = searchQuery.lowercase()
    
    while (true) {
        val matchIndex = lowercaseText.indexOf(lowercaseQuery, startIndex)
        if (matchIndex == -1) {
            // No more matches, append rest of text
            withStyle(baseStyle) {
                append(text.substring(startIndex))
            }
            break
        }
        
        // Append text before match
        if (matchIndex > startIndex) {
            withStyle(baseStyle) {
                append(text.substring(startIndex, matchIndex))
            }
        }
        
        // Append highlighted match
        withStyle(highlightStyle) {
            append(text.substring(matchIndex, matchIndex + searchQuery.length))
        }
        
        startIndex = matchIndex + searchQuery.length
    }
}

// Helper functions

private fun detectDocumentType(context: Context, uri: Uri, fileName: String): DocumentType {
    // Try by MIME type first
    val mimeType = context.contentResolver.getType(uri)
    
    DocumentType.entries.forEach { type ->
        if (type.mimeTypes.any { it.equals(mimeType, ignoreCase = true) }) {
            return type
        }
    }
    
    // Fall back to file extension
    val extension = fileName.substringAfterLast('.', "").lowercase()
    DocumentType.entries.forEach { type ->
        if (type.extensions.contains(extension)) {
            return type
        }
    }
    
    return DocumentType.UNKNOWN
}

private suspend fun loadDocument(
    context: Context,
    uri: Uri,
    type: DocumentType
): DocumentContent = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open document")
        
        inputStream.use { stream ->
            when (type) {
                DocumentType.WORD -> loadWordDocument(stream)
                DocumentType.EXCEL -> loadExcelDocument(stream)
                DocumentType.POWERPOINT -> loadPowerPointDocument(stream)
                DocumentType.UNKNOWN -> DocumentContent.Error("Unsupported document format")
            }
        }
    } catch (e: Exception) {
        DocumentContent.Error("Error reading document: ${e.localizedMessage}")
    }
}

private fun loadWordDocument(inputStream: java.io.InputStream): DocumentContent {
    return try {
        val document = XWPFDocument(inputStream)
        val paragraphs = mutableListOf<WordParagraph>()
        
        document.paragraphs.forEach { para ->
            val text = para.text
            val isHeading = para.style?.lowercase()?.contains("heading") == true
            var isBold = false
            var isItalic = false
            
            // Check run styles
            para.runs.forEach { run ->
                if (run.isBold) isBold = true
                if (run.isItalic) isItalic = true
            }
            
            paragraphs.add(WordParagraph(
                text = text,
                isHeading = isHeading,
                isBold = isBold,
                isItalic = isItalic
            ))
        }
        
        document.close()
        DocumentContent.WordContent(paragraphs = paragraphs)
    } catch (e: Exception) {
        DocumentContent.Error("Failed to parse Word document: ${e.localizedMessage}")
    }
}

private fun loadExcelDocument(inputStream: java.io.InputStream): DocumentContent {
    return try {
        val workbook = XSSFWorkbook(inputStream)
        val sheets = mutableListOf<ExcelSheet>()
        
        for (i in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(i)
            val rows = mutableListOf<List<String>>()
            
            // Limit rows to prevent memory issues
            val maxRows = minOf(sheet.physicalNumberOfRows, 100)
            
            for (rowIndex in 0 until maxRows) {
                val row = sheet.getRow(rowIndex) ?: continue
                val cells = mutableListOf<String>()
                
                // Limit columns
                val maxCols = minOf(row.lastCellNum.toInt(), 20)
                
                for (colIndex in 0 until maxCols) {
                    val cell = row.getCell(colIndex)
                    val value = try {
                        cell?.toString() ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    cells.add(value)
                }
                
                if (cells.isNotEmpty()) {
                    rows.add(cells)
                }
            }
            
            sheets.add(ExcelSheet(
                name = sheet.sheetName,
                rows = rows
            ))
        }
        
        workbook.close()
        DocumentContent.ExcelContent(sheets = sheets)
    } catch (e: Exception) {
        DocumentContent.Error("Failed to parse Excel document: ${e.localizedMessage}")
    }
}

private fun loadPowerPointDocument(inputStream: java.io.InputStream): DocumentContent {
    return try {
        val ppt = XMLSlideShow(inputStream)
        val slides = mutableListOf<SlideContent>()
        
        ppt.slides.forEachIndexed { index, slide ->
            var title = ""
            val content = mutableListOf<String>()
            
            slide.shapes.forEach { shape ->
                if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                    val text = shape.text?.trim() ?: ""
                    if (text.isNotBlank()) {
                        // First text shape is usually the title
                        if (title.isBlank() && shape.shapeName?.contains("Title", ignoreCase = true) == true) {
                            title = text
                        } else {
                            // Split by newlines for bullet points
                            text.split("\n").forEach { line ->
                                if (line.isNotBlank()) {
                                    content.add(line.trim())
                                }
                            }
                        }
                    }
                }
            }
            
            // If no title found, use first content as title
            if (title.isBlank() && content.isNotEmpty()) {
                title = content.removeAt(0)
            }
            
            slides.add(SlideContent(
                slideNumber = index + 1,
                title = title,
                content = content
            ))
        }
        
        ppt.close()
        DocumentContent.PowerPointContent(slides = slides)
    } catch (e: Exception) {
        DocumentContent.Error("Failed to parse PowerPoint document: ${e.localizedMessage}")
    }
}

private fun shareDocument(context: Context, uri: Uri, type: DocumentType) {
    try {
        val mimeType = type.mimeTypes.firstOrNull() ?: "*/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            this.type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share document", Toast.LENGTH_SHORT).show()
    }
}

private fun openWithExternalApp(context: Context, uri: Uri, type: DocumentType) {
    try {
        val mimeType = type.mimeTypes.firstOrNull() ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this document", Toast.LENGTH_SHORT).show()
    }
}
