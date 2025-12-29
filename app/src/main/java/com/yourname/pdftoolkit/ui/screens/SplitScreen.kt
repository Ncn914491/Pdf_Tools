package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.data.FileManager
import com.yourname.pdftoolkit.data.PdfFileInfo
import com.yourname.pdftoolkit.domain.operations.PdfSplitter
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Split modes available in the UI.
 */
private enum class SplitMode(val title: String, val description: String) {
    EXTRACT_RANGE("Extract Range", "Extract a range of pages to a new PDF"),
    ALL_PAGES("All Pages", "Split into individual pages (1 file per page)"),
    SPECIFIC_PAGES("Specific Pages", "Extract specific page numbers")
}

/**
 * Screen for splitting PDF files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfSplitter = remember { PdfSplitter() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var selectedMode by remember { mutableStateOf(SplitMode.EXTRACT_RANGE) }
    var startPage by remember { mutableStateOf("1") }
    var endPage by remember { mutableStateOf("1") }
    var specificPages by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }
    
    // File picker launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileInfo = FileManager.getFileInfo(context, uri)
            selectedFile = fileInfo
            
            scope.launch {
                pageCount = pdfSplitter.getPageCount(context, uri)
                endPage = pageCount.toString()
            }
        }
    }
    
    // Save file launcher (for custom location)
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            performSplit(
                context = context,
                scope = scope,
                pdfSplitter = pdfSplitter,
                file = selectedFile!!,
                selectedMode = selectedMode,
                startPage = startPage,
                endPage = endPage,
                specificPages = specificPages,
                pageCount = pageCount,
                outputUri = outputUri,
                onProgress = { progress = it },
                onProcessing = { isProcessing = it },
                onResult = { success, message, uri ->
                    resultSuccess = success
                    resultMessage = message
                    resultUri = uri
                    showResult = true
                }
            )
        }
    }
    
    // Function to split with default location
    fun splitWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = FileManager.generateOutputFileName("split")
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val file = selectedFile!!
                        val pages = when (selectedMode) {
                            SplitMode.EXTRACT_RANGE -> {
                                val start = startPage.toIntOrNull() ?: 1
                                val end = endPage.toIntOrNull() ?: pageCount
                                (start..end).toList()
                            }
                            SplitMode.SPECIFIC_PAGES -> parsePageNumbers(specificPages, pageCount)
                            SplitMode.ALL_PAGES -> (1..pageCount).toList()
                        }
                        
                        val splitResult = pdfSplitter.extractPages(
                            context = context,
                            inputUri = file.uri,
                            pageNumbers = pages,
                            outputStream = outputResult.outputStream,
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        splitResult.fold(
                            onSuccess = { count ->
                                Triple(true, "Successfully extracted $count pages\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}", outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Split failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Split failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Split PDF",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedFile == null) {
                    EmptyState(
                        icon = Icons.Default.CallSplit,
                        title = "No PDF Selected",
                        subtitle = "Select a PDF file to split or extract pages",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        // Selected file info
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedFile!!.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "$pageCount pages • ${selectedFile!!.formattedSize}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                    IconButton(onClick = { selectedFile = null }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Split mode selection
                        item {
                            Text(
                                text = "Split Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SplitMode.entries.forEach { mode ->
                                    SplitModeOption(
                                        mode = mode,
                                        isSelected = selectedMode == mode,
                                        onClick = { selectedMode = mode }
                                    )
                                }
                            }
                        }
                        
                        // Mode-specific options
                        item {
                            when (selectedMode) {
                                SplitMode.EXTRACT_RANGE -> {
                                    RangeInput(
                                        startPage = startPage,
                                        endPage = endPage,
                                        maxPages = pageCount,
                                        onStartChange = { startPage = it },
                                        onEndChange = { endPage = it }
                                    )
                                }
                                SplitMode.SPECIFIC_PAGES -> {
                                    SpecificPagesInput(
                                        value = specificPages,
                                        onChange = { specificPages = it },
                                        maxPages = pageCount
                                    )
                                }
                                SplitMode.ALL_PAGES -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "This will create $pageCount separate PDF files, one for each page.",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Save location option
                        item {
                            SaveLocationSelector(
                                useCustomLocation = useCustomLocation,
                                onUseCustomLocationChange = { useCustomLocation = it }
                            )
                        }
                    }
                }
                
                // Progress overlay
                if (isProcessing) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OperationProgress(
                                    progress = progress,
                                    message = "Extracting pages..."
                                )
                            }
                        }
                    }
                }
            }
            
            // Bottom action area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (selectedFile == null) {
                        ActionButton(
                            text = "Select PDF",
                            onClick = {
                                pickPdfLauncher.launch(arrayOf("application/pdf"))
                            },
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        val pageInfo = when (selectedMode) {
                            SplitMode.EXTRACT_RANGE -> {
                                val start = startPage.toIntOrNull() ?: 1
                                val end = endPage.toIntOrNull() ?: pageCount
                                "${(end - start + 1).coerceAtLeast(0)} pages"
                            }
                            SplitMode.SPECIFIC_PAGES -> {
                                "${parsePageNumbers(specificPages, pageCount).size} pages"
                            }
                            SplitMode.ALL_PAGES -> "$pageCount files"
                        }
                        
                        ActionButton(
                            text = "Split ($pageInfo)",
                            onClick = {
                                if (useCustomLocation) {
                                    val fileName = FileManager.generateOutputFileName("split")
                                    savePdfLauncher.launch(fileName)
                                } else {
                                    splitWithDefaultLocation()
                                }
                            },
                            enabled = isValidInput(selectedMode, startPage, endPage, specificPages, pageCount),
                            isLoading = isProcessing,
                            icon = Icons.Default.CallSplit
                        )
                    }
                }
            }
        }
    }
    
    // Result dialog with View option
    if (showResult) {
        ResultDialog(
            isSuccess = resultSuccess,
            title = if (resultSuccess) "Split Complete" else "Split Failed",
            message = resultMessage,
            onDismiss = { 
                showResult = false
                resultUri = null
            },
            onAction = resultUri?.let { uri ->
                { FileOpener.openPdf(context, uri) }
            },
            actionText = "Open PDF"
        )
    }
}

private fun performSplit(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    pdfSplitter: PdfSplitter,
    file: PdfFileInfo,
    selectedMode: SplitMode,
    startPage: String,
    endPage: String,
    specificPages: String,
    pageCount: Int,
    outputUri: Uri,
    onProgress: (Float) -> Unit,
    onProcessing: (Boolean) -> Unit,
    onResult: (Boolean, String, Uri?) -> Unit
) {
    scope.launch {
        onProcessing(true)
        onProgress(0f)
        
        val outputStream = context.contentResolver.openOutputStream(outputUri)
        if (outputStream != null) {
            val pages = when (selectedMode) {
                SplitMode.EXTRACT_RANGE -> {
                    val start = startPage.toIntOrNull() ?: 1
                    val end = endPage.toIntOrNull() ?: pageCount
                    (start..end).toList()
                }
                SplitMode.SPECIFIC_PAGES -> parsePageNumbers(specificPages, pageCount)
                SplitMode.ALL_PAGES -> (1..pageCount).toList()
            }
            
            val result = pdfSplitter.extractPages(
                context = context,
                inputUri = file.uri,
                pageNumbers = pages,
                outputStream = outputStream,
                onProgress = onProgress
            )
            
            outputStream.close()
            
            result.fold(
                onSuccess = { count ->
                    onResult(true, "Successfully extracted $count pages", outputUri)
                },
                onFailure = { error ->
                    onResult(false, error.message ?: "Split failed", null)
                }
            )
        } else {
            onResult(false, "Cannot create output file", null)
        }
        
        onProcessing(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitModeOption(
    mode: SplitMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RangeInput(
    startPage: String,
    endPage: String,
    maxPages: Int,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Page Range (1 - $maxPages)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = startPage,
                    onValueChange = onStartChange,
                    label = { Text("From") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                Text("to", style = MaterialTheme.typography.bodyMedium)
                
                OutlinedTextField(
                    value = endPage,
                    onValueChange = onEndChange,
                    label = { Text("To") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun SpecificPagesInput(
    value: String,
    onChange: (String) -> Unit,
    maxPages: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text("Page Numbers") },
                placeholder = { Text("e.g., 1, 3, 5-8, 10") },
                supportingText = { Text("Enter page numbers or ranges (1 - $maxPages)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

/**
 * Parse page numbers from input string.
 * Supports formats: "1, 3, 5-8, 10"
 */
private fun parsePageNumbers(input: String, maxPages: Int): List<Int> {
    if (input.isBlank()) return emptyList()
    
    val pages = mutableSetOf<Int>()
    
    input.split(",").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toIntOrNull() ?: return@forEach
                val end = range[1].trim().toIntOrNull() ?: return@forEach
                if (start in 1..maxPages && end in 1..maxPages) {
                    pages.addAll(start..end)
                }
            }
        } else {
            val page = trimmed.toIntOrNull()
            if (page != null && page in 1..maxPages) {
                pages.add(page)
            }
        }
    }
    
    return pages.sorted()
}

private fun isValidInput(
    mode: SplitMode,
    startPage: String,
    endPage: String,
    specificPages: String,
    pageCount: Int
): Boolean {
    return when (mode) {
        SplitMode.EXTRACT_RANGE -> {
            val start = startPage.toIntOrNull() ?: return false
            val end = endPage.toIntOrNull() ?: return false
            start in 1..pageCount && end in 1..pageCount && start <= end
        }
        SplitMode.SPECIFIC_PAGES -> {
            parsePageNumbers(specificPages, pageCount).isNotEmpty()
        }
        SplitMode.ALL_PAGES -> true
    }
}
