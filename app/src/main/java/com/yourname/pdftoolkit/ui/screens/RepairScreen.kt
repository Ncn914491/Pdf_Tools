package com.yourname.pdftoolkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.yourname.pdftoolkit.domain.operations.PdfRepairer
import com.yourname.pdftoolkit.ui.components.*
import com.yourname.pdftoolkit.util.FileOpener
import com.yourname.pdftoolkit.util.OutputFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for repairing corrupted PDFs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repairer = remember { PdfRepairer() }
    
    // State
    var selectedFile by remember { mutableStateOf<PdfFileInfo?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var diagnostics by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDiagnosing by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }
    
    // File picker launcher
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFile = FileManager.getFileInfo(context, uri)
            diagnostics = emptyList()
            
            // Run diagnostics
            scope.launch {
                isDiagnosing = true
                diagnostics = repairer.diagnose(context, uri)
                isDiagnosing = false
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
                    val result = repairer.repairPdf(
                        context = context,
                        inputUri = file.uri,
                        outputStream = outputStream,
                        onProgress = { progress = it }
                    )
                    
                    result.fold(
                        onSuccess = { repairResult ->
                            resultSuccess = true
                            resultUri = saveUri
                            resultMessage = buildString {
                                if (repairResult.wasCorrupted) {
                                    append("PDF was corrupted and has been repaired!\n\n")
                                } else {
                                    append("PDF appears to be healthy.\n\n")
                                }
                                append("Pages recovered: ${repairResult.pagesRecovered}\n")
                                if (repairResult.repairNotes.isNotEmpty()) {
                                    append("\nNotes:\n")
                                    repairResult.repairNotes.forEach { note ->
                                        append("• $note\n")
                                    }
                                }
                            }
                            selectedFile = null
                        },
                        onFailure = { error ->
                            resultSuccess = false
                            resultMessage = error.message ?: "Repair failed"
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
    
    // Function to repair with default location
    fun repairWithDefaultLocation() {
        scope.launch {
            isProcessing = true
            progress = 0f
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val file = selectedFile!!
                    val baseName = file.name.removeSuffix(".pdf")
                    val fileName = "${baseName}_repaired.pdf"
                    val outputResult = OutputFolderManager.createOutputStream(context, fileName)
                    
                    if (outputResult != null) {
                        val repairResult = repairer.repairPdf(
                            context = context,
                            inputUri = file.uri,
                            outputStream = outputResult.outputStream,
                            onProgress = { progress = it }
                        )
                        
                        outputResult.outputStream.close()
                        
                        repairResult.fold(
                            onSuccess = { result ->
                                val message = buildString {
                                    if (result.wasCorrupted) {
                                        append("PDF was corrupted and has been repaired!\n\n")
                                    } else {
                                        append("PDF appears to be healthy.\n\n")
                                    }
                                    append("Pages recovered: ${result.pagesRecovered}\n")
                                    append("\nSaved to: ${OutputFolderManager.getOutputFolderPath(context)}/${outputResult.outputFile.fileName}")
                                    if (result.repairNotes.isNotEmpty()) {
                                        append("\n\nNotes:\n")
                                        result.repairNotes.forEach { note ->
                                            append("• $note\n")
                                        }
                                    }
                                }
                                Triple(true, message, outputResult.outputFile.contentUri)
                            },
                            onFailure = { error ->
                                outputResult.outputFile.file.delete()
                                Triple(false, error.message ?: "Repair failed", null)
                            }
                        )
                    } else {
                        Triple(false, "Cannot create output file", null)
                    }
                } catch (e: Exception) {
                    Triple(false, e.message ?: "Repair failed", null)
                }
            }
            
            resultSuccess = result.first
            resultMessage = result.second
            resultUri = result.third
            if (resultSuccess) {
                selectedFile = null
            }
            isProcessing = false
            showResult = true
        }
    }
    
    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Repair PDF",
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
                        icon = Icons.Default.Build,
                        title = "No PDF Selected",
                        subtitle = "Select a PDF to diagnose and repair",
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
                            FileItemCard(
                                fileName = selectedFile!!.name,
                                fileSize = selectedFile!!.formattedSize,
                                onRemove = { 
                                    selectedFile = null
                                    diagnostics = emptyList()
                                }
                            )
                        }
                        
                        // Diagnostics section
                        item {
                            Text(
                                text = "Diagnostics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (isDiagnosing) {
                            item {
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
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Analyzing PDF structure...")
                                    }
                                }
                            }
                        } else if (diagnostics.isNotEmpty()) {
                            items(diagnostics) { diagnostic ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            diagnostic.startsWith("✓") -> MaterialTheme.colorScheme.primaryContainer
                                            diagnostic.startsWith("✗") -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                ) {
                                    Text(
                                        text = diagnostic,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        // Info card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Repair rebuilds the PDF structure which can fix:\n• Corrupted cross-reference tables\n• Missing end-of-file markers\n• Broken object streams\n\nThe repaired file is saved as a new document.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
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
                                message = "Repairing PDF..."
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
                        // Save location option
                        SaveLocationSelector(
                            useCustomLocation = useCustomLocation,
                            onUseCustomLocationChange = { useCustomLocation = it }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ActionButton(
                            text = "Repair PDF",
                            onClick = {
                                if (useCustomLocation) {
                                    val baseName = selectedFile!!.name.removeSuffix(".pdf")
                                    saveFileLauncher.launch("${baseName}_repaired.pdf")
                                } else {
                                    repairWithDefaultLocation()
                                }
                            },
                            isLoading = isProcessing,
                            icon = Icons.Default.Build
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
            title = if (resultSuccess) "Repair Complete" else "Repair Failed",
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
