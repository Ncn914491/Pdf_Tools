package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.yourname.pdftoolkit.domain.operations.PdfMerger
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for merging multiple PDF files into one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfMerger = remember { PdfMerger() }
    
    // State
    var selectedFiles by remember { mutableStateOf<List<PdfFileInfo>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }
    
    // File picker launcher for multiple PDFs
    val pickPdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newFiles = uris.mapNotNull { uri ->
                FileManager.getFileInfo(context, uri)
            }
            selectedFiles = selectedFiles + newFiles
        }
    }
    
    // Custom save file launcher (optional)
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { outputUri ->
            performMerge(
                context = context,
                scope = scope,
                pdfMerger = pdfMerger,
                selectedFiles = selectedFiles,
                outputUri = outputUri,
                onProgress = { progress = it },
                onProcessing = { isProcessing = it },
                onResult = { success, message, uri ->
                    resultSuccess = success
                    resultMessage = message
                    resultUri = uri
                    if (success) selectedFiles = emptyList()
                    showResult = true
                }
            )
        }
    }
    
    // Function to merge with default location
    fun mergeWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileName = FileManager.generateOutputFileName("merged")
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val mergeResult = pdfMerger.mergePdfs(
                            context = context,
                            inputUris = selectedFiles.map { it.uri },
                            outputStream = outputResult.outputStream,
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        mergeResult.fold(
                            onSuccess = {
                                Triple(true, "Successfully merged ${selectedFiles.size} PDFs\n\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}", outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Merge failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Merge failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) selectedFiles = emptyList()
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Merge PDFs",
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
                if (selectedFiles.isEmpty()) {
                    // Empty state
                    EmptyState(
                        icon = Icons.Default.MergeType,
                        title = "No PDFs Selected",
                        subtitle = "Add 2 or more PDF files to merge them into a single document",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    // File list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        item {
                            Text(
                                text = "Selected Files (${selectedFiles.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        itemsIndexed(
                            items = selectedFiles,
                            key = { index, file -> "${file.uri}-$index" }
                        ) { index, file ->
                            FileItemCardWithOrder(
                                index = index + 1,
                                fileName = file.name,
                                fileSize = file.formattedSize,
                                onRemove = {
                                    selectedFiles = selectedFiles.toMutableList().apply {
                                        removeAt(index)
                                    }
                                },
                                onMoveUp = if (index > 0) {
                                    {
                                        selectedFiles = selectedFiles.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index - 1, item)
                                        }
                                    }
                                } else null,
                                onMoveDown = if (index < selectedFiles.lastIndex) {
                                    {
                                        selectedFiles = selectedFiles.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index + 1, item)
                                        }
                                    }
                                } else null
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    pickPdfsLauncher.launch(arrayOf("application/pdf"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add More PDFs")
                            }
                        }
                        
                        // Save location option
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useCustomLocation,
                                    onCheckedChange = { useCustomLocation = it }
                                )
                                Text(
                                    text = "Choose custom save location",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (!useCustomLocation) {
                                Text(
                                    text = "Default: ${OutputFolderManager.getOutputFolderPath(context)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 48.dp)
                                )
                            }
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
                                    message = "Merging PDFs..."
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedFiles.isEmpty()) {
                        ActionButton(
                            text = "Select PDFs",
                            onClick = {
                                pickPdfsLauncher.launch(arrayOf("application/pdf"))
                            },
                            icon = Icons.Default.FolderOpen
                        )
                    } else {
                        ActionButton(
                            text = "Merge ${selectedFiles.size} PDFs",
                            onClick = {
                                if (useCustomLocation) {
                                    val fileName = FileManager.generateOutputFileName("merged")
                                    savePdfLauncher.launch(fileName)
                                } else {
                                    mergeWithDefaultLocation()
                                }
                            },
                            enabled = selectedFiles.size >= 2,
                            isLoading = isProcessing,
                            icon = Icons.Default.MergeType
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
            title = if (resultSuccess) "Merge Complete" else "Merge Failed",
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

private fun performMerge(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    pdfMerger: PdfMerger,
    selectedFiles: List<PdfFileInfo>,
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
            val result = pdfMerger.mergePdfs(
                context = context,
                inputUris = selectedFiles.map { it.uri },
                outputStream = outputStream,
                onProgress = onProgress
            )
            
            outputStream.close()
            
            result.fold(
                onSuccess = {
                    onResult(true, "Successfully merged ${selectedFiles.size} PDFs", outputUri)
                },
                onFailure = { error ->
                    onResult(false, error.message ?: "Merge failed", null)
                }
            )
        } else {
            onResult(false, "Cannot create output file", null)
        }
        
        onProcessing(false)
    }
}

/**
 * File item card with ordering controls.
 */
@Composable
private fun FileItemCardWithOrder(
    index: Int,
    fileName: String,
    fileSize: String,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order number
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Reorder buttons
            Column {
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

