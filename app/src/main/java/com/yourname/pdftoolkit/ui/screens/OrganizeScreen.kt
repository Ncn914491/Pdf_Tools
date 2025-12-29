package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.data.FileManager
import com.yourname.pdftoolkit.data.PdfFileInfo
import com.yourname.pdftoolkit.domain.operations.PdfOrganizer
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for organizing PDF pages (remove, reorder).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val organizer = remember { PdfOrganizer() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var selectedPages by remember { mutableStateOf(setOf<Int>()) }
    var isRemoveMode by remember { mutableStateOf(true) } // true = remove, false = keep/reorder
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var useCustomLocation by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    
    // File picker launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFile = FileManager.getFileInfo(context, uri)
            selectedPages = setOf()
            
            scope.launch {
                pageCount = organizer.getPageCount(context, uri)
            }
        }
    }
    
    // Save file launcher (for custom location)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { saveUri ->
            val file = selectedFile ?: return@let
            
            scope.launch {
                isProcessing = true
                progress = 0f
                
                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                    val result = if (isRemoveMode) {
                        organizer.removePages(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputStream,
                            pagesToRemove = selectedPages,
                            onProgress = { progress = it }
                        )
                    } else {
                        organizer.reorderPages(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputStream,
                            newOrder = selectedPages.sorted(),
                            onProgress = { progress = it }
                        )
                    }
                    
                    result.fold(
                        onSuccess = { organizeResult ->
                            resultSuccess = true
                            resultUri = saveUri
                            resultMessage = if (isRemoveMode) {
                                "Removed ${organizeResult.pagesRemoved} pages. Result has ${organizeResult.resultPageCount} pages."
                            } else {
                                "Extracted ${organizeResult.resultPageCount} pages."
                            }
                            selectedFile = null
                            selectedPages = setOf()
                        },
                        onFailure = { error ->
                            resultSuccess = false
                            resultMessage = error.message ?: "Operation failed"
                        }
                    )
                } ?: run {
                    resultSuccess = false
                    resultMessage = "Cannot create output file"
                }
                
                isProcessing = false
                showResult = true
            }
        }
    }
    
    // Function to organize with default location
    fun organizeWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val file = selectedFile!!
                    val baseName = file.name.removeSuffix(".pdf")
                    val suffix = if (isRemoveMode) "_edited" else "_extracted"
                    val fileName = "${baseName}${suffix}.pdf"
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val organizeResult = if (isRemoveMode) {
                            organizer.removePages(
                                context = context,
                                inputUri = file.uri,
                                outputStream = outputResult.outputStream,
                                pagesToRemove = selectedPages,
                                onProgress = { progress = it }
                            )
                        } else {
                            organizer.reorderPages(
                                context = context,
                                inputUri = file.uri,
                                outputStream = outputResult.outputStream,
                                newOrder = selectedPages.sorted(),
                                onProgress = { progress = it }
                            )
                        }
                        
                        outputResult.outputStream.close()
                        
                        organizeResult.fold(
                            onSuccess = { oResult ->
                                val message = if (isRemoveMode) {
                                    "Removed ${oResult.pagesRemoved} pages. Result has ${oResult.resultPageCount} pages.\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}"
                                } else {
                                    "Extracted ${oResult.resultPageCount} pages.\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}"
                                }
                                Triple(true, message, outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Operation failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Operation failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) {
                selectedFile = null
                selectedPages = setOf()
            }
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Organize Pages",
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
                        icon = Icons.Default.SwapVert,
                        title = "No PDF Selected",
                        subtitle = "Select a PDF to remove or reorder pages",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Selected file info
                        FileItemCard(
                            fileName = selectedFile!!.name,
                            fileSize = "${pageCount} pages • ${selectedFile!!.formattedSize}",
                            onRemove = { 
                                selectedFile = null
                                selectedPages = setOf()
                                pageCount = 0
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Mode toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isRemoveMode,
                                onClick = { 
                                    isRemoveMode = true
                                    selectedPages = setOf()
                                },
                                label = { Text("Remove Pages") },
                                leadingIcon = if (isRemoveMode) {
                                    { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !isRemoveMode,
                                onClick = { 
                                    isRemoveMode = false
                                    selectedPages = setOf()
                                },
                                label = { Text("Keep Pages") },
                                leadingIcon = if (!isRemoveMode) {
                                    { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Selection info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRemoveMode) {
                                    "Select pages to remove"
                                } else {
                                    "Select pages to keep"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row {
                                TextButton(onClick = { 
                                    selectedPages = (1..pageCount).toSet()
                                }) {
                                    Text("All")
                                }
                                TextButton(onClick = { 
                                    selectedPages = setOf()
                                }) {
                                    Text("None")
                                }
                            }
                        }
                        
                        Text(
                            text = "${selectedPages.size} of $pageCount selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Page grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items((1..pageCount).toList()) { pageNum ->
                                val isSelected = pageNum in selectedPages
                                
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPages = if (isSelected) {
                                            selectedPages - pageNum
                                        } else {
                                            selectedPages + pageNum
                                        }
                                    },
                                    label = {
                                        Text(
                                            "$pageNum",
                                            modifier = Modifier.fillMaxWidth(),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isRemoveMode) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Progress overlay
                if (isProcessing) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .align(Alignment.Center)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OperationProgress(
                                progress = progress,
                                message = if (isRemoveMode) "Removing pages..." else "Extracting pages..."
                            )
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
                        val canProcess = selectedPages.isNotEmpty() && 
                            (isRemoveMode && selectedPages.size < pageCount) || 
                            (!isRemoveMode && selectedPages.isNotEmpty())
                        
                        // Save location option
                        SaveLocationSelector(
                            useCustomLocation = useCustomLocation,
                            onUseCustomLocationChange = { useCustomLocation = it }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ActionButton(
                            text = if (isRemoveMode) {
                                "Remove ${selectedPages.size} Pages"
                            } else {
                                "Keep ${selectedPages.size} Pages"
                            },
                            onClick = {
                                if (useCustomLocation) {
                                    val baseName = selectedFile!!.name.removeSuffix(".pdf")
                                    val suffix = if (isRemoveMode) "_edited" else "_extracted"
                                    saveFileLauncher.launch("${baseName}${suffix}.pdf")
                                } else {
                                    organizeWithDefaultLocation()
                                }
                            },
                            enabled = canProcess,
                            isLoading = isProcessing,
                            icon = if (isRemoveMode) Icons.Default.Delete else Icons.Default.ContentCopy
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
            title = if (resultSuccess) "Success" else "Error",
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
